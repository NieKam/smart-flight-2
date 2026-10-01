# Architecture review: Smart Flight rewrite against the CLAUDE.md target

Scope: all of `app/src/main`, `app/src/test`, `app/src/androidTest`, the Gradle build files, the version catalog and `.github/workflows/build.yml`. Line numbers refer to the working tree on branch `plan/initial`. Paths are relative to `/home/ai-dev/smart-flight-2-modern/`.

## 1. Current structure map

Single `:app` module, organised by feature under `app/src/main/java/kniezrec/com/flightinfo/`. Every Android type and every piece of logic lives in the same feature package. No package is a separate domain or data layer.

```
MainActivity  ──►  every feature package, every ui package, monitoring (global object)
                   Also: composition root, state holder, event router, lifecycle orchestrator

ui/gnss      ──►  course, displayunits, flight, gnss, horizon, map, nearby, route,
                  ui/route, ui/permission (colours)
ui/route     ──►  nearby, route, displayunits, ui/permission (colours)
ui/settings  ──►  display, displayunits
ui/about     ──►  about
ui/permission──►  permission

course       ──►  flight, nearby     (ForegroundCourseObservationCoordinator)
orientation  ──►  course, horizon    (adapters implement their Platform interfaces)
nearby       ──►  flight             (FlightLocationFix)
route        ──►  nearby, flight     (+ android.content.SharedPreferences)
map          ──►  flight
monitoring   ──►  flight, gnss, MainActivity, R
flight, gnss, horizon, display, displayunits, permission, about  ──►  (nothing internal)
```

How the layers work in each feature package:
- The `*Controller` classes combine three roles: state holder (a ViewModel's job), session/registration owner (a data source's job), and a small amount of domain computation.
- Each `*Platform` interface is declared in the same file as its controller. The matching `Android*Platform` class sits in the same package.
- The `*PreferencesStore` classes wrap `SharedPreferences` with synchronous read and write.
- Location data flows through three hops: `LocationForegroundService` → `LocationGnssMonitoringSession` → `BackgroundMonitoringBridge` (a global object). From there it goes to lambdas registered by `MainActivity`, then into `FlightParametersController.onLocationFix`. `MainActivity` then fans the fix out by hand to course, nearby, route, map and the bridge.

There are no coroutines, Flow, ViewModel or DI anywhere in production code. The only coroutine use is `rememberCoroutineScope` for snackbars (`MainActivity.kt:87,166`).

---

## 2. Findings (ordered by severity)

### HIGH

#### F1: VIOLATION (HIGH): UI state lives in the Activity, not in ViewModels
- **Evidence:**
  - 23 `mutableStateOf`/`mutableIntStateOf` fields on `MainActivity`: `MainActivity.kt:91-110,134-135`.
  - Plus plain fields `pressureMillibars` (95), `lastRouteSearchQuery` (131) and `mapLoadToken` (133).
  - The manifest declares no `configChanges` (`AndroidManifest.xml:25-36`), and sensor orientation is a user setting (`DisplayPreferences.portraitOrientation`). So rotation recreates the Activity and rebuilds every controller. Route-picker state (`routePicker`, `routeResults`, `routeSearchError`, the query inside `RoutePicker.kt:68-71`), the settings/about overlays (106-107) and the map load are all lost.
  - No state uses `SavedStateHandle`/`rememberSaveable`, so none of it survives process death either.
  - The only persisted UI data is route endpoints and settings, both in SharedPreferences.
- **Rule:** Target standards, "UI state held in ViewModels, exposed as `StateFlow`, collected with `collectAsStateWithLifecycle`; must survive configuration changes."
- **Direction:** one ViewModel per screen, or per feature card where the state is independent. Each exposes a single `StateFlow<UiState>`. Transient UI-only flags such as overlay visibility and picker query become `rememberSaveable`, or live in the ViewModel with `SavedStateHandle`.
- **Prerequisites:** F4 (Flow-based sources), F13 (lifecycle-viewmodel-compose and lifecycle-runtime-compose dependencies). DI (F5) makes the ViewModel constructors practical but is not strictly required first.

#### F2: VIOLATION (HIGH): `MainActivity` is composition root, event router and business-logic host at once (620 lines)
- **Evidence:**
  - Location fan-out router: `MainActivity.kt:505-511`. Each fix is pushed by hand to the bridge, course, nearby, route and map.
  - Pressure merged into flight state in two places: 500-503 and 519-523. This duplicates the merge logic that belongs in one combine.
  - Route search/nearest result handling, validation and error mapping: 245-309. This includes `validCity` checks and `getString` into state.
  - Map load cancellation via `mapLoadToken`: 595-613.
  - Permission-driven start/stop orchestration: 410-424 and 579-591. Foreground gating is repeated at 199, 324, 382, 412, 596 and 603.
  - Settings write, side effect and service start: 183-202.
  - Service start/stop: 569-577.
- **Rule:** Target architecture, layers "ui (Compose + ViewModel) → domain (optional) → data". UI code must not contain business logic.
- **Direction:**
  - The Activity keeps only Android-bound concerns: `setContent`, the permission launcher, window flags and orientation, and starting the service.
  - Orchestration moves into ViewModels, for example "observe while foreground and permission granted".
  - The fan-out is replaced by several consumers collecting one shared location Flow.
- **Prerequisites:** F4, F3.

#### F3: VIOLATION (HIGH): the process-global `BackgroundMonitoringBridge` is a hand-rolled event bus with no single source of truth for location
- **Evidence:**
  - `monitoring/BackgroundMonitoringBridge.kt:18-127`. A mutable `object` holds the service reference, generation counter, visibility flag, "has usable fix" flag and three activity lambdas.
  - The Activity installs its handlers at `MainActivity.kt:149-156` and clears them at 403-404. The service attaches, detaches and forwards at `LocationForegroundService.kt:74,94,162,214-215`.
  - Location and GNSS registrations exist twice. The service creates its own `AndroidFlightLocationPlatform` and `AndroidGnssStatusPlatform` (`LocationForegroundService.kt:200-216`), and the Activity creates another pair (`MainActivity.kt:492,499`; see F7).
  - Tests must reset global state by hand (`BackgroundMonitoringBridgeTest.kt:12-15`).
- **Rule:** "Repositories expose Flow / suspend functions; single source of truth per data type." Also "structured concurrency".
- **Direction:**
  - One application-scoped location/GNSS repository wraps the Android callbacks with `callbackFlow` and shares them (`shareIn`/`stateIn` with `WhileSubscribed`).
  - The foreground service and the dashboard ViewModel are both collectors. The service stays the owner of the background lifetime and the notification.
  - "Activity visible" and "has usable fix" become observable state in the repository layer, not fields on a global object.
- **Prerequisites:** F4. Also F5, or at least a manual application-level container, so that the service and ViewModel receive the same instance.
- **Correctness note (one line):** the bridge's plain `var`s are touched from service and Activity callbacks with no synchronisation. Hand this to correctness-reviewer.

#### F4: VIOLATION (HIGH): callback and Executor async model at every layer boundary, no coroutines
- **Evidence:**
  - Callback registration interfaces:
    - `GnssStatusPlatform.registerGnssStatusCallback` (`gnss/GnssStatusController.kt:10`)
    - `FlightLocationPlatform.registerLocationListener` (`flight/FlightParametersController.kt:9`)
    - `PressurePlatform.registerPressureListener` (`flight/PressureController.kt:7`)
    - `CourseOrientationPlatform` (`course/CourseController.kt:6`)
    - `HorizonOrientationPlatform` (`horizon/HorizonController.kt:6`)
    - `OrientationSource.addListener` (`orientation/OrientationSource.kt:80`)
  - Result callbacks: `RouteController.search/nearest` (`route/RouteController.kt:91-124`) and `MapArchiveRepository.prepare(onResult)` (`map/MapArchiveRepository.kt:15`).
  - `onStateChanged` callbacks in every controller: 8 classes.
  - Raw threads: `Executors.newSingleThreadExecutor()` at `MainActivity.kt:532` and `MapArchiveRepository.kt:11`. `mainExecutor` is threaded through 7 constructors.
  - Hand-rolled cancellation, a direct symptom of the callback model, appears in 11 places:
    - `CourseController.kt:17-18`
    - `HorizonController.kt:17-18`
    - `PressureController.kt:18-19`
    - `FlightParametersController.kt:22-23`
    - `NearbyCityController.kt:79-84`
    - `RouteController.kt:22`
    - `OrientationEventDispatcher` (`OrientationSource.kt:92-117`)
    - `BackgroundMonitoringBridge.kt:20`
    - `LocationGnssMonitoringSession.kt:14`
    - `MainActivity.kt:133` (`mapLoadToken`)
    - `MapLoadAttemptGate` (`MapArchiveCopier.kt:52`)
- **Rule:** Target standards, "Coroutines: structured concurrency, no GlobalScope, injectable dispatchers; Android callbacks wrapped with `callbackFlow`." Also "Repositories expose Flow / suspend functions."
- **Direction:**
  - Each `Android*Platform` becomes a `callbackFlow` data source, with unregistration in `awaitClose`.
  - DB/asset work becomes `suspend` functions on an injected IO dispatcher.
  - The session-token and generation machinery is deleted wherever collector cancellation (`flatMapLatest`, `collectLatest`, scope cancellation) replaces it.
  - The Platform-interface seam itself should stay (see K1).
- **Prerequisites:** a direct `kotlinx-coroutines` dependency in the catalog (F13). Pin current behaviour in tests before the rewrite (CLAUDE.md workflow rule). Existing controller tests are the pinning point.

#### F5: GAP (HIGH): no DI framework and no Application class
- **Evidence:**
  - The manifest `<application>` has no `android:name` (`AndroidManifest.xml:12-20`).
  - No Hilt/Dagger/KSP entries in `gradle/libs.versions.toml` or `app/build.gradle.kts:1-5`.
  - All wiring is `by lazy` in `MainActivity.kt:444-593`, plus ad-hoc construction in the service (`LocationForegroundService.kt:141,200-216`). The full inventory is in section 3.
- **Rule:** "DI: Hilt with KSP. Introduce it as a dedicated migration step... Verify Hilt compatibility with AGP 9 / Kotlin 2.2 before adding it."
- **Direction:**
  - Add a `@HiltAndroidApp` Application, `@AndroidEntryPoint` on the Activity and the service, and `@HiltViewModel` ViewModels.
  - Add modules for system services (`LocationManager`, `SensorManager`, `PackageManager`), for `SharedPreferences`/DataStore instances, and for dispatchers.
  - The singleton-vs-Activity scoping constraints are listed in section 3.
- **Prerequisites:** a KSP plugin compatible with AGP 9 built-in Kotlin (F13), and verified Hilt/AGP 9 compatibility (see "Not verified").

### MEDIUM

#### F6: VIOLATION (MEDIUM): settings and preferences have no observable single source of truth; synchronous `commit()` on the main thread
- **Evidence:**
  - Stores expose only a sync `read()`/`write()`:
    - `display/DisplayPreferences.kt:32-62`
    - `displayunits/UnitPreferences.kt:50-89`
    - `monitoring/BackgroundNotificationPreferences.kt:9-30`
  - The Activity polls: it reads all three stores in `onCreate` (`MainActivity.kt:157-159`) and again in every `onResume` (369-371) to pick up changes.
  - The service builds its own store instance and reads it on each reconcile (`LocationForegroundService.kt:140-143`). Toggling the setting reaches the service only because the Activity also calls `BackgroundMonitoringBridge.setNotificationEnabled` (`MainActivity.kt:198`).
  - `commit()` is called on the main thread in 4 places: `DisplayPreferences.kt:48`, `UnitPreferences.kt:70`, `BackgroundNotificationPreferences.kt:23`, `RouteController.kt:180`.
  - Permission-request history is stored through an anonymous object writing raw prefs inside the Activity (`MainActivity.kt:463-470,486-488`).
  - There are six separate preference files: `display_behavior`, `display_units`, `monitoring_behavior`, `location_permission`, `route`, `osmdroid` (`MainActivity.kt:475,479,483,487,544`; `MapCard.kt:263`).
- **Rule:** "Repositories expose Flow / suspend functions; single source of truth per data type."
- **Direction:** one settings repository, or one per concern, exposing `Flow<…>` plus `suspend` setters. The ViewModel and the service observe the same Flow, which removes the `onResume` re-reads and the bridge notification call. Whether the backing store stays SharedPreferences or moves to DataStore is a planner decision. Existing key names (`display_behavior_*`, `display_units_*`, `monitoring_show_background_notification`, `route_*_id`, `has_requested_location_permission`) must be preserved or migrated to keep user settings.
- **Prerequisites:** F4 (coroutines dependency).

#### F7: OVER_ENGINEERING (MEDIUM): dead dual-path "own registration vs external session" in the foreground controllers
- **Evidence:**
  - `startObservation` always calls `attachToExternalSession()` (`MainActivity.kt:582-583`) before `courseObservationCoordinator.start()` (585).
  - `FlightParametersController.externalSession` is set at `FlightParametersController.kt:74` and never reset. So `start()` always takes the early return at lines 31-35.
  - As a result the foreground `AndroidFlightLocationPlatform` (`MainActivity.kt:499`) never registers, and `onRegistrationFailed → gnssStatusController.showError()` (504) is unreachable.
  - `GnssStatusController.start()` (`GnssStatusController.kt:22-40`) is never called from production code: grep finds only `stop`, `showError` and `attachToExternalSession` calls. Its `AndroidGnssStatusPlatform` (`MainActivity.kt:492`) is also dead.
  - `ForegroundCourseObservationCoordinator.start` depends on `flightParametersController.start()`'s return value, which is now always `true`.
- **Rule:** "Every abstraction must justify its existence; prefer fewer, clearer types."
- **Direction:** a single location/GNSS source (see F3). The foreground controllers or ViewModels become pure consumers. The "own registration" branch and its platform wiring go away.
- **Prerequisites:** before deleting, pin with tests that the foreground path depends only on service-forwarded fixes. `FlightParametersControllerTest` and `GnssStatusControllerTest` currently exercise the dead `start()` path.

#### F8: VIOLATION (MEDIUM): Android type inside a logic class; route persistence mixed into the state holder
- **Evidence:**
  - `route/RouteController.kt:3,13` takes `android.content.SharedPreferences` and performs persistence directly (140-141, 162-181). It is simultaneously repository, state holder and search dispatcher.
  - Its test has to hand-roll a full `SharedPreferences`/`Editor` fake (`RouteControllerTest.kt:194-290`).
  - `RouteController.start()` reads prefs on the worker thread while `persist()` writes on the main thread. This is a structural consequence of the missing repository.
- **Rule:** "Keep Android types out of logic classes (keep the Platform-interface idea)." Also Layers / data.
- **Direction:**
  - A route repository owns the persisted endpoint IDs and exposes them as a Flow. Its storage details stay inside it.
  - The route ViewModel combines that repository, the city repository and the location Flow.
  - `routeDetails` and `validCity` stay as pure functions (see K2).
- **Prerequisites:** F4, F6.

#### F9: VIOLATION (MEDIUM): the city data layer has no single source of truth or caching, and asset handling is duplicated
- **Evidence:**
  - `AndroidNearbyCityRepository` is instantiated twice (`MainActivity.kt:536,548`).
  - Every call to `findNearest`/`searchByName`/`findById` stats the file, opens SQLite and reads the entire `cities_info` table into a list (`nearby/AndroidNearbyCityRepository.kt:31-54`). `NearbyCityController` calls `findNearest` for every accepted fix (1 Hz GPS, `AndroidFlightLocationPlatform.kt:41`), mitigated only by pending-request collapsing (`NearbyCityController.kt:130-142`).
  - The interface uses `@Throws(Exception::class)` and default no-op implementations (`NearbyCityController.kt:53-69`). The defaults exist only so fakes can skip methods.
  - Two asset-copy strategies:
    - `AndroidNearbyCityRepository.copyAsset` (56-70): `renameTo` into `filesDir`.
    - `MapArchiveCopier.copy` (`MapArchiveCopier.kt:11-28`): atomic `Files.move` into `cacheDir`.
  - `MapArchiveRepository` owns a private executor and a callback API (`MapArchiveRepository.kt:11,15`).
- **Rule:** "Repositories expose Flow / suspend functions; single source of truth per data type."
- **Direction:**
  - One application-scoped city repository with `suspend` queries on an IO dispatcher. It loads the table once, or queries with SQL, and serves both nearby and route.
  - One asset-extraction helper, reused by the city DB and the map archive.
  - `MapArchiveRepository.prepare` becomes a `suspend` function.
- **Prerequisites:** F4, F5 (the repository needs singleton scope to be shared).

#### F10: VIOLATION (MEDIUM): logic and presentation leak across the UI boundary; map state is not observable
- **Evidence:**
  - `MapCardState`, which holds a `java.io.File`, is declared in the UI package (`ui/gnss/MapCard.kt:62-72`) but is produced and owned by `MainActivity` (99, 600-612). Its logic-side sibling is `map/MapState.kt`.
  - `MapSessionRules` is a mutable, non-snapshot object. The Activity mutates it (`MainActivity.kt:510,598,611`) and composables read it imperatively (`MapCard.kt:124,253,322-336`). Recomposition is forced by the counter `mapPositionVersion` (`MainActivity.kt:134,510,599`), read as a bare expression at `GnssStatusScreen.kt:114`.
  - The UI decides the map button glyph by checking whether the localised string starts with "Expand"/"Collapse" (`MapCard.kt:188-194`). This breaks under translation.
  - UI state carries pre-resolved or pre-formatted strings:
    - `routeSearchError` via `getString` at `MainActivity.kt:253,280,296,300,306`
    - `NearbyCityState.Available.localTime`, formatted in the controller with `Locale.getDefault()` (`NearbyCityController.kt:171`)
    - `RouteDetails.arrival`/`duration`, formatted in the model (`RouteModels.kt:94-107`)

    These do not refresh after a locale change and cannot be asserted on the JVM without locale setup.
  - Route-picker validation and selection logic lives in the composable (`RoutePicker.kt:69-74,140-141`).
- **Rule:** Layers, "ui (Compose + ViewModel)". UI state must survive configuration changes. UI code must not contain business logic.
- **Direction:**
  - Map position, marker course and first-fix centring become part of an immutable map UI state emitted by a ViewModel.
  - `MapCardState` moves next to its producer.
  - Error states are typed (enums or sealed types) and resolved to strings in composables.
  - Time and number formatting happens at the UI edge. State keeps `Instant`/`ZoneId`/`Duration`.
- **Prerequisites:** F1.

#### F11: VIOLATION (MEDIUM): 38-parameter god composable hides wiring errors
- **Evidence:**
  - `GnssStatusScreen` takes 38 parameters, most with defaults (`ui/gnss/GnssStatusScreen.kt:73-113`).
  - Because they are defaulted, `MainActivity` silently omits `routeNearestDraft`: the call at `MainActivity.kt:207-326` never passes it, although the Activity maintains it at 105, 260, 302. The nearest-city draft never reaches `RoutePicker`. Handing this to correctness-reviewer.
  - `RoutePicker` is embedded inside the dashboard composable (170-187), and the route-picker parameters (98-111) are threaded through it.
- **Rule:** "Scalable and easy to understand"; "prefer fewer, clearer types."
- **Direction:**
  - A screen-level composable takes one `UiState` plus an event sink, or per-card state objects from feature ViewModels.
  - The route picker becomes its own destination or overlay with its own state holder.
  - Keep the individual cards stateless (see K4).
- **Prerequisites:** F1.

#### F12: GAP (MEDIUM): the most complex logic is untestable on the JVM; test infrastructure does not match the target
- **Evidence:**
  - The orchestration in `MainActivity` (F2) has no tests, unit or instrumented. Every `androidTest` hosts composables on a bare `ComponentActivity` (for example `GnssStatusScreenTest.kt:59`).
  - There are 4 hand-written `SharedPreferences` fakes: `RouteControllerTest.kt:194`, `DisplayPreferencesTest.kt:79`, `UnitPreferencesTest.kt:35`, `BackgroundMonitoringTest.kt:43`.
  - The global bridge forces `@After` resets (`BackgroundMonitoringBridgeTest.kt:12-15`).
  - No `kotlinx-coroutines-test` dependency (`app/build.gradle.kts:54-56`).
  - CI runs only `ktlintCheck testDebugUnitTest assembleDebug` (`.github/workflows/build.yml:36`), so the Compose tests in `androidTest` never run anywhere automatically.
  - Template leftovers: `ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`.
  - What is testable on the JVM today: all `*Controller` classes (with fakes), pure functions, the stores (with fakes), and `LocationForegroundService` under Robolectric via its `protected open` seams (`LocationForegroundService.kt:133,140,183,187,200`).
- **Rule:** CLAUDE.md workflow, "Before refactoring an area, ensure unit tests pin its current behavior". Target standards (injectable dispatchers imply coroutine tests).
- **Direction:**
  - ViewModels tested with fake repositories and `StandardTestDispatcher`.
  - Repositories tested against fake data sources.
  - Settings fakes become in-memory repository fakes instead of `SharedPreferences` reimplementations.
  - Whether instrumented tests should run in CI (emulator cost) is a planner decision.
- **Prerequisites:** F13 (test dependencies). Pinning tests for `MainActivity` orchestration are needed before F2 can be done safely.

#### F13: GAP (MEDIUM): the build is missing target dependencies and tooling
- **Evidence:**
  - Catalog (`gradle/libs.versions.toml:1-39`): no `ksp` plugin, no `hilt`, no `androidx-lifecycle-viewmodel-compose`, no `androidx-lifecycle-runtime-compose` (needed for `collectAsStateWithLifecycle`), no `kotlinx-coroutines-android`/`-test`.
  - Coroutines arrive only transitively (`MainActivity.kt:87`).
  - The Kotlin plugin setup relies on AGP 9 built-in Kotlin: only `kotlin.compose` is applied (`app/build.gradle.kts:1-5`), and there is no `org.jetbrains.kotlin.android`. KSP and the Hilt Gradle plugin must both support this mode.
  - `compileOptions` target Java 11 (`app/build.gradle.kts:30-33`) while CI uses JDK 17 (`build.yml:29`). Not blocking.
  - There is no kapt, so there is nothing to migrate from kapt.
- **Rule:** "DI: Hilt with KSP"; "KSP instead of kapt; Gradle version catalog"; "Check current library versions instead of assuming them."
- **Direction:** add the missing catalog entries at verified current versions. Confirm KSP and Hilt support for AGP 9 built-in Kotlin before adopting.
- **Prerequisites:** none. This is a leaf.

### LOW

#### F14: OVER_ENGINEERING (LOW): three interfaces and two adapters for one rotation sensor
- **Evidence:**
  - `OrientationSource` (`orientation/OrientationSource.kt:77-83`)
  - `CourseOrientationPlatform` (`course/CourseController.kt:3-9`)
  - `HorizonOrientationPlatform` (`horizon/HorizonController.kt:3-9`)
  - `SharedCourseOrientationPlatform` and `SharedHorizonOrientationPlatform` (`orientation/OrientationPlatformAdapters.kt:6-42`), which are near-identical pass-through adapters.
  - `OrientationEventDispatcher` (`OrientationSource.kt:92-123`) exists only to invalidate stale queued callbacks.
- **Rule:** "Every abstraction must justify its existence."
- **Direction:** one orientation data source exposing `Flow<OrientationSample>`, shared among collectors. Course and horizon map it themselves. The adapters and the dispatcher disappear with the move to Flow.
- **Prerequisites:** F4.

#### F15: OVER_ENGINEERING (LOW): dead or test-only production code
- **Evidence:**
  - `BackgroundMonitoringSession` and `BackgroundMonitoringState` (`monitoring/BackgroundMonitoringSession.kt:1-30`): referenced only from `BackgroundMonitoringTest.kt:26-39`.
  - `MapLoadAttemptGate` (`map/MapArchiveCopier.kt:52-58`): only in `MapArchiveCopierTest.kt:51`.
  - `formatKilometres` (`route/RouteModels.kt:111`): no references.
  - `LocationGnssMonitoringSession.isActive` (57): test-only.
  - `normalizeCourse` (`map/MapState.kt:28`) duplicates `normalizeCourseDegrees` (`course/CourseState.kt:16`).
  - Unused constructor parameter `callbackExecutor` (`flight/AndroidPressurePlatform.kt:11`).
  - A pre-API-28 branch is unreachable with minSdk 31 (`about/AboutPlatform.kt:38-43`).
- **Rule:** "prefer fewer, clearer types."
- **Direction:** delete, or fold into the surviving equivalents.
- **Prerequisites:** none.

#### F16: OVER_ENGINEERING (LOW): thin indirections around trivial behaviour
- **Evidence:**
  - `ForegroundCourseObservationCoordinator` (`course/ForegroundCourseObservationCoordinator.kt:7-34`) is a cross-feature orchestrator that couples course to flight and nearby. `nearbyCityController` defaults to `null` only for tests.
  - `DisplayPreferencesApplier` plus `DisplayEffectSink` (`display/DisplayPreferences.kt:11-30`) is a 3-line applier with an anonymous sink in the Activity (`MainActivity.kt:111-130`).
  - `CourseController.retry(isForeground)` and `HorizonController.retry(isForeground)` (`CourseController.kt:53`, `HorizonController.kt:61`) take a UI lifecycle flag into the logic class.
  - `AndroidAppVersionProvider` has a nullable `Context` plus a lambda default (`about/AboutPlatform.kt:26-33`).
- **Rule:** "Every abstraction must justify its existence." "No pass-through."
- **Direction:** fold the coordination into the dashboard ViewModel. Keep the display side effect as a small Activity-level effect driven by settings state. Lifecycle gating belongs to the collector (`collectAsStateWithLifecycle`/`repeatOnLifecycle`), not to a parameter.
- **Prerequisites:** F1, F4.

#### F17: VIOLATION (LOW): design tokens scattered and misplaced; presentation mappings duplicated
- **Evidence:**
  - App-wide colours `cardPurple`/`actionCyan` live in `ui/permission/PermissionColors.kt:6-7`. Every card imports them from the permission package: `CourseCard.kt:49-50`, `HorizonCard.kt:44-45`, `MapCard.kt:47-48`, `RouteCard.kt:27-28`, `NearbyCityCard.kt:38`, `FlightParametersCard.kt:42`, `GnssStatusScreen.kt:64-65`.
  - 31 `Color(0x…)` literals across 11 UI files. Text colour `0xFFD9D9ED` is redeclared in at least 5 files.
  - `ui/theme/Theme.kt:13-58` is the unmodified template with dynamic colour, and the app's own palette bypasses it.
  - Unit → string-resource mapping is duplicated in 4 files: `FlightParametersCard.kt:156-275`, `UnitSettingsScreen.kt:294-343`, `RouteCard.kt:168-204`, `NearbyCityCard.kt:97-120`.
- **Rule:** "Scalable and easy to understand."
- **Direction:** move tokens into `ui/theme`, with one set of unit label/accessibility-label helpers shared by all cards.
- **Prerequisites:** none.

#### F18: VIOLATION (LOW): the service depends on a concrete UI class, and osmdroid global config is initialised inside composables
- **Evidence:**
  - `LocationForegroundService.kt:20,223` builds a `PendingIntent` to `MainActivity::class.java`.
  - `Configuration.getInstance().load(...)` runs inside `AndroidView.factory` in two places (`MapCard.kt:263`, `RoutePicker.kt:162`).
- **Rule:** dependency direction; "every abstraction must justify its existence". This is also a DI-readiness item.
- **Direction:** osmdroid configuration becomes app-startup work in the future Application class or an initializer. Making the launch intent injectable is optional; flagged only because Hilt migration touches this code.
- **Prerequisites:** F5 (Application class).

### KEEP

#### K1: The Platform-interface seam
Narrow interfaces over Android APIs, with Android implementations separate from logic. Examples: `GnssStatusPlatform`, `FlightLocationPlatform`, `PressurePlatform`, `FineLocationPermissionPlatform`, `LocationPermissionRequestHistory`, `MonitoringSession`, `MapZoomTarget`.

- **Relates to:** "Keep Android types out of logic classes (keep the Platform-interface idea)."
- **Migration note:** the interfaces should turn into `callbackFlow`-based data-source interfaces. They should not be deleted.

#### K2: Pure, Android-free computation functions with good JVM tests
These should become the domain layer, or stay as feature-local pure functions:
- `mapHorizonAttitude` (`horizon/HorizonState.kt:23`)
- `DisplayRelativeOrientation.calculate` (`orientation/OrientationSource.kt:36-74`). Its only Android dependency is `Surface.ROTATION_*` in `fromSurfaceRotation`, which is trivially separable.
- `normalizeCourseDegrees` and `compassCardinal` (`course/CourseState.kt:16-42`)
- `distanceKilometres` and `NearbyCoordinate.from` (`nearby/NearbyCityController.kt:27-42,189-201`)
- `routeDetails`, `routeOverlay`, `validCity` (`route/RouteModels.kt`)
- `locationPermissionState` (`permission/LocationPermissionState.kt:14`)
- Unit conversions (`displayunits/UnitPresentation.kt`)
- `MapSessionRules` companion zoom rules and `applyMapZoomPolicy` (`map/MapState.kt:71-104`)
- The vertical-speed computation in `FlightParametersController.kt:95-112`
- `formatAppVersion`

None of these warrants a use-case class. They are called from a single consumer each, so no use cases are justified yet.

#### K3: Sealed and data `*State` types
`GnssStatusState`, `FlightParametersState`, `CourseState`, `HorizonState`, `NearbyCityState`, `RouteState`, `LocationPermissionState`. These are immutable, exhaustive and Android-free, and they map directly onto `StateFlow` UI state. Keep them, subject to the string/format fix in F10.

#### K4: Stateless cards
`FlightParametersCard`, `CourseCard`, `HorizonCard`, `NearbyCityCard`, `RouteCard`, `PermissionOnboardingScreen`, `AboutDialog` and `UnitSettingsScreen` take state plus callbacks and hold only ephemeral UI state. The Compose tests in `androidTest` rely on this. Keep the cards stateless and put ViewModels one level above them.

#### K5: Package by feature
This already matches "Package by feature, layers inside each feature". The migration should add layer sub-packages inside features, not reorganise by layer.

#### K6: Shared sensor ownership and service-owned background lifetime
- `AndroidOrientationSource` reference-counts a single sensor listener shared by course and horizon (`OrientationSource.kt:138-152`). This is the right behaviour to reproduce with `shareIn(WhileSubscribed)`.
- The foreground service as owner of background monitoring, plus the eligibility checks and notification logic, is correct Android structure. Keep the service; change what it collects from.

#### K7: Asset extraction
`MapArchiveCopier` copies atomically, validates, and never exposes a partial destination (`map/MapArchiveCopier.kt:10-50`). This is the better of the two copy strategies and should become the shared helper (F9).

#### K8: Injected clocks and test seams
The `clock: () -> Instant` parameters (`NearbyCityController.kt:76`, `RouteController.kt:17`) and the `protected open` seams on `LocationForegroundService` allow deterministic tests. Keep the clock injection pattern; it maps directly to a Hilt-provided clock.

#### K9: Build basics
Version catalog, ktlint with `check` depending on `ktlintCheck`, configuration cache, a single module, and no kapt. On modularization: no concrete problem was found that splitting into modules would solve at this size (about 50 source files, one screen). Nothing here justifies proposing it now.

---

## 3. DI inventory (every construction or wiring site Hilt would replace or touch)

Suggested scopes: **S** = could be `@Singleton` (application context only); **A** = must stay Activity-bound or Activity-provided; **VM** = belongs inside a ViewModel. The scope column is advisory for the planner.

### `MainActivity.kt`

| Line(s) | Object | Dependencies | Scope note |
|---|---|---|---|
| 111-130 | `DisplayPreferencesApplier` + anonymous `DisplayEffectSink` | `window`, `requestedOrientation` | A (window side effect) |
| 132 | `MapSessionRules()` | none | VM state |
| 136-139 | `permissionLauncher` | Activity Result API | A (stays in Activity) |
| 149-156, 403-404 | `BackgroundMonitoringBridge` handler install/clear | controllers | Removed by F3 |
| 167 | `AndroidAppVersionProvider(this)` | Context | S |
| 168 | `AndroidExternalIntentLauncher(this)` | Context (starts activities) | A (needs Activity context for `startActivity` without NEW_TASK) |
| 444-472 | `LocationPermissionStateController` + anonymous `FineLocationPermissionPlatform` + anonymous `LocationPermissionRequestHistory` | `ContextCompat.checkSelfPermission`, `shouldShowRequestPermissionRationale`, `permissionPreferences` | Rationale check is A. The request history is S (prefs). |
| 474-476 | `DisplayPreferencesStore` | `getSharedPreferences("display_behavior")` | S |
| 478-480 | `BackgroundNotificationPreferencesStore` | `getSharedPreferences("monitoring_behavior")` | S (shared with the service) |
| 482-484 | `UnitPreferencesStore` | `getSharedPreferences("display_units")` | S |
| 486-488 | `permissionPreferences` | `getSharedPreferences("location_permission")` | S |
| 490-495 | `GnssStatusController` + `AndroidGnssStatusPlatform` | `LocationManager`, `PackageManager`, `mainExecutor` | Platform S (unused today, F7). Controller VM. |
| 497-513 | `FlightParametersController` + `AndroidFlightLocationPlatform` + fan-out lambda | `LocationManager`, `PackageManager`, `mainExecutor`, 5 other components | Platform S (unused today, F7). Controller VM. |
| 515-526 | `PressureController` + `AndroidPressurePlatform` | `SensorManager`, `mainExecutor` | Platform S, controller VM |
| 528-530 | `CourseController` + `SharedCourseOrientationPlatform` | `orientationSource` | VM |
| 532 | `cityLookupExecutor` (`Executors.newSingleThreadExecutor`) | none | Replace with injected IO dispatcher |
| 534-542 | `RouteController` + `AndroidNearbyCityRepository` (instance 1) | `applicationContext`, `routePreferences`, executors | Repository S, controller VM |
| 544 | `routePreferences` | `getSharedPreferences("route")` | S |
| 546-553 | `NearbyCityController` + `AndroidNearbyCityRepository` (instance 2) | `applicationContext`, executors | Repository S (dedupe), controller VM |
| 555 | `AndroidOrientationSource(this, mainExecutor)` | Activity context (`context.display`, `OrientationSource.kt:130`) | **A, or a display-rotation provider injected separately.** With an application context, `Context.getDisplay()` is not associated with a display. Making this `@Singleton` with `@ApplicationContext` would break display-relative orientation. |
| 557-559 | `HorizonController` + `SharedHorizonOrientationPlatform` | `orientationSource` | VM |
| 561-563 | `ForegroundCourseObservationCoordinator` | 3 controllers | VM (F16) |
| 571, 576 | `startForegroundService` / `stopService` intents | Context | A (or a small injectable service launcher) |
| 593 | `MapArchiveRepository(applicationContext)` | Context; owns `Executors.newSingleThreadExecutor()` (`MapArchiveRepository.kt:11`) | S, with an injected IO dispatcher |

### `LocationForegroundService.kt`

| Line(s) | Object | Dependencies | Scope note |
|---|---|---|---|
| 35 | `Handler(Looper.getMainLooper())` for the eligibility poll | none | Could become a service `lifecycleScope` coroutine |
| 74, 94, 162, 214-215 | `BackgroundMonitoringBridge` attach/detach/notify/forward | global object | Replaced by the injected location repository (F3) |
| 133-138 | `getSystemService(LocationManager)` + permission check | Context | Inject `LocationManager`, or an eligibility checker (S) |
| 141-143 | second `BackgroundNotificationPreferencesStore` | `getSharedPreferences` | Inject the shared settings repository (S) |
| 200-216 | `LocationGnssMonitoringSession` + second `AndroidFlightLocationPlatform` + second `AndroidGnssStatusPlatform` | `LocationManager`, `PackageManager`, `mainExecutor` | Collapse into the shared S location repository |
| 133, 140, 183, 187, 200 | `protected open` test seams | Robolectric subclass tests | Hilt test modules could replace these later. Keep them until the tests are migrated. |

### Global objects and other wiring

| Location | Object | Note |
|---|---|---|
| `monitoring/BackgroundMonitoringBridge.kt:18` | `object BackgroundMonitoringBridge` | Mutable global. Remove via F3. |
| `map/MapArchiveCopier.kt:10` | `object MapArchiveCopier` | Stateless. Fine as-is; could be injected for tests. |
| `about/AboutPlatform.kt:49` | `object AboutIntentFactory` | Stateless. Fine. |
| `orientation/OrientationSource.kt:36` | `object DisplayRelativeOrientation` | Pure. Fine. |
| `ui/gnss/MapCard.kt:263`, `ui/route/RoutePicker.kt:162` | `Configuration.getInstance().load(...)` (osmdroid global) | Move to Application init (F18) |
| `ui/gnss/MapCard.kt:264`, `ui/route/RoutePicker.kt:163` | `OfflineTileProvider(SimpleRegisterReceiver(context), …)` | View-level. Stays in the UI. |
| `AndroidManifest.xml:12` | No `Application` subclass | Required for `@HiltAndroidApp` |

---

## 4. Not verified (inferred, not traced end-to-end)

1. **Hilt and KSP compatibility with AGP 9.4 built-in Kotlin (Kotlin 2.2.10).** I did not check release notes or current versions (no network lookup in this review). The project applies no `org.jetbrains.kotlin.android` plugin, so both the KSP Gradle plugin and the Hilt Gradle plugin must support AGP 9's built-in Kotlin mode. This has to be verified before F5/F13, as CLAUDE.md requires.
2. **Rotation behaviour.** Activity recreation on rotation is inferred from the manifest having no `configChanges` and from the `portraitOrientation=false` → `SCREEN_ORIENTATION_SENSOR` setting (`MainActivity.kt:128`). So I infer that `onConfigurationChanged` (`MainActivity.kt:380-385`) is effectively not called on rotation, but I did not run it. A 0°↔180° rotation causes no recreation and, I believe, no `onConfigurationChanged` either. If so, horizon or course would not re-read display rotation until the next sample (the sensor callback reads `display.rotation` per event, which may make this harmless).
3. **Performance of `AndroidNearbyCityRepository`.** I traced the full-table read per GPS fix in code, but the size of `assets/databases/cities_info.db` and the resulting cost were not measured.
4. **Dead foreground registration paths (F7).** Traced within `MainActivity` and the controllers. I did not execute anything to confirm there is no reflective or test-only production path.
5. **`routeNearestDraft` never reaching the picker (F11).** Traced by reading the call site. I did not confirm runtime behaviour. It may be partly masked, because `routeResults = listOf(city)` (`MainActivity.kt:303`) still shows the city in the list.
6. **Thread-safety of `BackgroundMonitoringBridge` and `RouteController` prefs access.** Noted only as structural consequences. Not analysed; that belongs to correctness-reviewer.
7. **Behavioural parity with the original app (`~/smart-flight`).** Not compared. This review covers structure only.
