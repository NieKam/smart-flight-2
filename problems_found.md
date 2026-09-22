# Android Code Review

## Executive Summary

Reviewed Smart Flight at commit `03adcd63a14f63a3d932186bbb00f7cd69033912` on 22 September 2026. This is a single-module, offline Android application with Compose UI, Android location/GNSS and sensor adapters, an offline SQLite city catalog, and osmdroid maps. There are **no Android ViewModels, StateFlow, SharedFlow, or application Flow pipelines**. The corresponding responsibilities live in `MainActivity`, callback-based controllers, and a process-local service bridge; these were reviewed as the actual state owners.

The code has useful platform interfaces, immutable presentation models, several effective stale-callback guards, and meaningful controller tests. The main production concerns are integration defects: service failures never reach the dashboard's remediation states, route restoration can overwrite a newer saved choice, and the picker/map state bridges contain correctness and update risks. The instrumentation source set also contains definite compilation errors that the current CI commands do not compile.

**Findings: 0 Critical, 5 High, 10 Medium, 1 Low.** High findings are classified **BLOCKING** for production readiness; Medium findings are **WARNING**; Low findings are **INFO**. A finding's **Type** separately distinguishes source-confirmed defects, conditional risks requiring runtime verification, and recommendations. The absence of a Critical finding does not constitute release approval.

Scope included all 50 production Kotlin files, every test suite, manifest/resources/assets, Gradle and the version catalog, CI, and the supporting agent/orchestrator workflow. Selected task specifications and recent history were used to check intended behavior, not as evidence that implementation or tests pass. No application source, test, or configuration was changed.

Verification: `./gradlew ktlintCheck --console=plain` passed. `./gradlew testDebugUnitTest lintDebug assembleRelease --console=plain` stopped during task dependency resolution because an Android SDK is not configured; none of those three tasks completed. SQLite integrity and map ZIP CRC checks passed. Supplemental JVM verification and its limits are recorded in Testing Review. No emulator, physical device, release installation, or remote CI run was verified.

## Critical Findings

None established by this review. In particular, the source inspection did not establish a remotely exploitable vulnerability, a broad application-startup crash, or destruction of an external user dataset.

## High Findings

### [H01] Service monitoring failures bypass the dashboard state machine and retry path

**Severity:** High — BLOCKING

**Type:** Confirmed Problem

**Location:**
`app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt:579` — `startObservation`; also lines 149, 324, 569.
`app/src/main/java/kniezrec/com/flightinfo/gnss/GnssStatusController.kt:52` — `attachToExternalSession`.
`app/src/main/java/kniezrec/com/flightinfo/monitoring/LocationForegroundService.kt:61` — `onStartCommand`; also lines 161–174.

**Problem**

The Activity marks the GNSS and flight controllers attached before establishing that a service session is active. The GNSS external attachment unconditionally publishes `Waiting`, bypassing the disabled-location, missing-hardware, and registration-error branches in `GnssStatusController.start()`. Service startup and registration failures stop the service without publishing a corresponding dashboard state. `startBackgroundMonitoring()` also discards its `runCatching` result.

**Why it matters**

A user with location disabled, unavailable GNSS hardware, or failed registration can see indefinite “waiting” instead of the implemented Settings/retry UI. If eligibility is lost after a satellite report, the bridge callback calls `gnssStatusController.stop()`, which does not clear or replace the existing `Available` state. Recovery is also incomplete: the dashboard retry only calls `startObservation()`, not the service startup path.

**Technical analysis**

The production path is `onResume → refreshPermissionState → startObservation → attachToExternalSession`, followed separately by service startup. `FlightParametersController.start()` returns success whenever its external-session flag is set, regardless of actual service ownership. The bridge forwards location and satellite lists but has no observable Starting/Active/Disabled/Unavailable/Error/Stopped state. Unit tests for `GnssStatusController.start()` verify a path the Activity no longer uses. Service tests verify cleanup but do not assert the resulting Activity UI.

**Recommended approach**

Make the service/session owner expose a typed status alongside data callbacks, including a reason for startup failure and termination. Derive dashboard availability from that status and invalidate dependent live readings when monitoring ends. Make retry invoke the authoritative session start/reconcile operation while the Activity is eligible. Preserve useful failure information rather than discarding it. Test the complete Activity-to-service path with providers off, absent GNSS, registration rejection, service-start exception, provider loss, and successful retry.

---

### [H02] Picker map update reads and replaces its own observed state

**Severity:** High — BLOCKING

**Type:** Potential Risk

**Location:**
`app/src/main/java/kniezrec/com/flightinfo/ui/route/RoutePicker.kt:158` — `PickerMap`, especially `AndroidView.update` at lines 190–203.

**Problem**

`draftMarker` is `mutableStateOf<Marker?>`. The `AndroidView.update` callback reads it, removes the old marker, creates a new `Marker`, and writes the new instance back into the same state on every update with a non-null selected coordinate.

**Why it matters**

`AndroidView` observes snapshot-state reads in its update callback. Replacing the observed marker with a distinct object can invalidate the same update repeatedly, producing a self-sustaining update cycle, continuous allocation/redraw, and an unresponsive or non-idle picker. Selecting a city or editing an already populated endpoint reaches this path when the map preview is present.

**Technical analysis**

The coordinate need not change: `Marker(map)` has a new identity each time. This is imperative renderer bookkeeping stored as reactive input, with no equality guard. Existing picker tests pass `mapArchive = null`, so they never execute this callback. The source establishes the read/write feedback pattern; the exact runtime scheduling and impact have not been reproduced on an Android device, hence the risk classification.

**Recommended approach**

Keep the marker in a non-snapshot holder owned by the map instance, create it once, and mutate its position/icon only when the corresponding input changes. Remove it only when selection becomes null. Avoid writing Compose state merely to track an Android view's internal objects. Add a real-preview Compose test that selects a city, waits for idle, changes selection, and verifies bounded marker count and updates.

---

### [H03] Asynchronous route restoration can overwrite a newer user edit and persist the old route

**Severity:** High — BLOCKING

**Type:** Confirmed Problem

**Location:**
`app/src/main/java/kniezrec/com/flightinfo/route/RouteController.kt:126` — `restore`; also `choose`, `clear`, and `clearRoute` at lines 55–88.

**Problem**

Restoration checks only the lifecycle `session` token before assigning both endpoints and calling `persist()`. User mutations do not invalidate that pending restore. `start()` schedules restoration on every foreground entry.

**Why it matters**

A user can clear or change an endpoint while a previous saved route is being resolved. The old restore callback can then reinstate the previous endpoints and write them back to preferences, losing the newer explicit action.

**Technical analysis**

A valid execution order is: restore reads saved endpoint A on the worker; the UI chooses B or clears A; restore's main-thread callback arrives; the session is unchanged, so A replaces the current value and is persisted. The single worker does not serialize UI mutations with callback delivery. The route card remains interactive during restore, and existing tests use direct executors, eliminating this interleaving.

**Recommended approach**

Track a route-edit revision in addition to lifecycle session. Capture it when restoration begins and apply restored values only if no intervening edit occurred, or merge only untouched endpoints. Capture preference IDs consistently before scheduling work. Avoid rewriting persisted choices merely as a side effect of reading them. Add controllable worker/callback executor tests covering choose, individual clear, and clear-all between restore read and delivery.

---

### [H04] Aircraft position is hidden behind a mutable object that Compose may skip

**Severity:** High — BLOCKING

**Type:** Potential Risk

**Location:**
`app/src/main/java/kniezrec/com/flightinfo/map/MapState.kt:37` — `MapSessionRules`.
`app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt:510` — accepted fix/version update.
`app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt:114` — version expression; `MapCard` call at line 157.
`app/src/main/java/kniezrec/com/flightinfo/ui/gnss/MapCard.kt:322` — position rendering.

**Problem**

Position, bearing, and first-fix centering flags are ordinary mutable properties of one `MapSessionRules` instance. The Activity increments Compose state `mapPositionVersion`, but the screen merely evaluates that argument and does not pass it or a changed position value to `MapCard`/`OfflineMap`.

**Why it matters**

Recomposing a parent does not guarantee that a child executes. With Kotlin Compose strong skipping, unchanged unstable objects are compared by identity. The map can retain the previous marker or never center on the first fix until some unrelated map input changes.

**Technical analysis**

The configured Kotlin Compose plugin is 2.2.10, where strong skipping is enabled by default; no opt-out is configured. `rules` and the archive remain the same objects, the callbacks can be memoized, and route endpoints need not change when the aircraft moves. Reading ordinary `rules.latestPosition` inside `AndroidView.update` creates no snapshot dependency. The current map tests exercise pure rules or replace overlays, not this Activity-to-map position propagation. Generated application code and device behavior could not be inspected without the Android build, so this remains a specifically identified runtime risk rather than a claimed observed freeze.

**Recommended approach**

Pass an immutable map presentation value containing coordinate, bearing, and a first-fix/recenter request identity through every relevant composable boundary. Alternatively expose snapshot-observable map state read directly in the renderer. If retaining a version parameter, it must reach the rendering update rather than stop at the dashboard. Verify first and subsequent fixes while all other inputs remain constant under the actual Compose compiler configuration.

---

### [H05] Instrumentation tests contain compile errors and are outside the CI gate

**Severity:** High — BLOCKING

**Type:** Confirmed Problem

**Location:**
`app/src/androidTest/java/kniezrec/com/flightinfo/ui/permission/PermissionOnboardingScreenTest.kt:39`.
`app/src/androidTest/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreenTest.kt:217` — also lines 386, 428, 567.
`app/src/androidTest/java/kniezrec/com/flightinfo/ui/settings/UnitSettingsScreenTest.kt:78` — also line 103.
`.github/workflows/build.yml:30` — verification steps.

**Problem**

The checked-in Android tests have drifted from production APIs:

- The permission test refers to nonexistent `permissionStateCardTestTag`; production declares `PERMISSION_STATE_CARD_TEST_TAG`.
- The GNSS test passes `CourseState` as the fifth positional argument to `GnssStatusScreen`, where the current parameter is `onOpenSettings: () -> Unit`.
- Two picker calls use `onConfirm = {}` despite the required `(NearbyCityRecord) -> Boolean` return type.
- Two settings calls supply a trailing lambda after four positional arguments, although the final parameter is `Modifier`, not a function. Use the named `onDisplayPreferenceChange` parameter.

**Why it matters**

The UI verification suite cannot serve as a regression gate in this state. The CI jobs run formatting, JVM unit tests, and `assembleDebug`; none compile `androidTest`. Formatting can pass while these type/reference errors remain.

**Technical analysis**

These errors are established by comparing source declarations and call sites, independently of the missing local SDK. This review does not claim to have run the Android compiler. Runtime assertions also need attention after compilation: some tests call `setContent` twice on one rule, assume off-screen dashboard content is displayed without scrolling, or assert stale labels/formatting. For example, the speed selector test clicks the last option (knots) but expects miles per hour.

**Recommended approach**

Repair test calls with named arguments and the current constants/return types. Add `assembleDebugAndroidTest` to every PR gate, then run `connectedDebugAndroidTest` or managed-device tests on a controlled emulator. Replace repeated `setContent` with mutable test inputs, scroll to off-screen content, and align assertions with intended product semantics. Add actual `MainActivity` integration tests; current Compose suites host `ComponentActivity` with manually supplied state and callbacks.

---

## Medium Findings

### [M01] Map-selected city draft is never passed from the Activity to the picker

**Severity:** Medium — WARNING

**Type:** Confirmed Problem

**Location:**
`app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt:286` — `onRouteNearest`, especially line 302 and the `GnssStatusScreen` call.
`app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt:109` — `routeNearestDraft`, forwarded at line 181.
`app/src/main/java/kniezrec/com/flightinfo/ui/route/RoutePicker.kt:73` — draft selection effect.

**Problem**

The nearest-city callback assigns `routeNearestDraft = city`, but the Activity omits `routeNearestDraft = routeNearestDraft` when calling the dashboard. The dashboard parameter therefore remains its default `null`.

**Why it matters**

A map long press updates the results list but not the selected draft or its marker. With no prior selection, Confirm stays disabled until an extra list selection. With a prior selection, Confirm can still accept the old city after the user long-presses a new location.

**Technical analysis**

The draft is written and cleared in the Activity but never read there. The picker itself has the required effect. Its nearest-city test injects `nearestDraft = city` directly and uses no map, bypassing the broken production call site.

**Recommended approach**

Wire the selected draft through the complete call chain and define how searching, changing endpoint, failures, and cancel clear it. Prefer one picker state object containing selected city and operation status to avoid parallel independent fields. Add a test through the actual Activity wiring that long-presses a second city and verifies what Confirm commits.

---

### [M02] Picker requests outlive picker identity, and a pause can leave loading permanently set

**Severity:** Medium — WARNING

**Type:** Confirmed Problem

**Location:**
`app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt:224` — open/search/cancel callbacks; also lines 245–307 and 392.
`app/src/main/java/kniezrec/com/flightinfo/route/RouteController.kt:91` — `search` and `nearest`.

**Problem**

Requests are keyed only to the route controller's foreground session. Canceling a picker or opening the other endpoint does not invalidate the old request. Conversely, pausing does invalidate controller callbacks, but the Activity's `routeSearchLoading` flag remains true and is not reset on resume or picker open.

**Why it matters**

An old picker request can populate a newly opened picker. A request interrupted by backgrounding can leave Search and Confirm disabled indefinitely, because the callback responsible for clearing loading is deliberately dropped. IME search and map long presses also allow new work while another request is pending, so an earlier completion can clear the shared loading flag prematurely.

**Technical analysis**

`onPause()` calls `routeController.stop()`; both result callbacks require `active && token == session`. On the same Activity's return, `routePicker` and `routeSearchLoading` survive, but `start()` creates a new session. No terminal UI state is published for the canceled request. Single-thread worker ordering does not solve UI identity or cancellation ownership.

**Recommended approach**

Give each picker opening and each search/nearest operation an ID, capture the endpoint, and accept results only for the current operation. On cancel/pause, cancel or invalidate work and explicitly terminate loading. On resume, either restart the saved query or show an idle retryable state. Test slow search → Home → return, search → Cancel → other endpoint, repeated IME search, and nearest-versus-search overlap.

---

### [M03] User navigation and unconfirmed route input are lost on Activity recreation

**Severity:** Medium — WARNING

**Type:** Confirmed Problem

**Location:**
`app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt:101` — route/settings/about state; `onCreate` at line 141.
`app/src/main/java/kniezrec/com/flightinfo/ui/route/RoutePicker.kt:68` — query and selection.
`app/src/main/java/kniezrec/com/flightinfo/ui/settings/UnitSettingsScreen.kt:77` — open selector.

**Problem**

Navigation flags and picker ownership are Activity `mutableStateOf` fields. Query and selected draft use `remember`, not saveable state. The Activity accepts `savedInstanceState` but does not restore these values, and the manifest does not intercept configuration recreation.

**Why it matters**

Rotation, window-size changes, locale changes, or process recreation return the user to the default dashboard and discard an in-progress endpoint choice. Turning the portrait setting on/off can itself trigger this while the user is in Settings. Confirmed route IDs and preferences survive; unconfirmed user work does not.

**Technical analysis**

This is a state-restoration issue, not an objection to the absence of a ViewModel. Reacquiring sensor readings after recreation is reasonable. Restorable user intent should be treated differently from live measurements, resources, and transient loading flags.

**Recommended approach**

Save the current destination/overlay, picker endpoint, query, and selected city ID with `rememberSaveable`, a `SavedStateRegistry` owner, or a ViewModel plus `SavedStateHandle`. Resolve IDs after recreation and restart necessary operations; never save Activity, MapView, executor, or sensor objects. Test recreation during editing and while changing the orientation preference, as well as process restoration with persisted route IDs.

---

### [M04] Every city lookup materializes the entire 47,317-row database

**Severity:** Medium — WARNING

**Type:** Confirmed Problem

**Location:**
`app/src/main/java/kniezrec/com/flightinfo/nearby/AndroidNearbyCityRepository.kt:12` — search/find methods and `readAll` at line 31.
`app/src/main/java/kniezrec/com/flightinfo/nearby/NearbyCityController.kt:105` — per-fix lookup and worker loop at line 144.
`app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt:532` — shared single city executor.

**Problem**

Each search, ID restore, and nearest-city lookup opens SQLite, reads all rows into new Kotlin records/strings, then filters or scans the list. Nearest-city work is scheduled for every valid GPS fix, including unchanged positions; the platform requests updates every second.

**Why it matters**

The packaged database contains 47,317 records. Rebuilding that object graph and computing geographic distances at GPS cadence creates avoidable I/O, CPU and garbage-collection work. Route search/restore shares the worker with nearby lookups and can be delayed. If lookups consistently take longer than the fix interval, stale-result rejection can prevent an Available result and the continuously draining lookup loop can monopolize that worker.

**Technical analysis**

Nearby requests are coalesced, which correctly bounds the queue, but it does not remove repeated database loading or guarantee fairness to route tasks. ID lookup ignores the existing primary-key index. The card also replaces an available city with `LookingUp` on every fix. No device performance measurement was made; the repeated full-table work is confirmed, while the magnitude of jank, starvation, or power cost is device-dependent.

**Recommended approach**

Query IDs directly with bound SQL parameters. Use bounded/paged name queries with a defined search policy. For nearest cities, either cache the immutable catalog once on a worker with explicit asset-version invalidation, or use a geographic candidate index/bounding-box strategy with antimeridian handling and exact distance checks. Apply a measured movement/time threshold, retain current content while refreshing, and ensure nearby processing yields to interactive requests. Benchmark with the real asset and test rapid fixes plus concurrent route searches.

---

### [M05] Preference writes perform synchronous disk commits on the main thread

**Severity:** Medium — WARNING

**Type:** Confirmed Problem

**Location:**
`app/src/main/java/kniezrec/com/flightinfo/displayunits/UnitPreferences.kt:62` — `write`.
`app/src/main/java/kniezrec/com/flightinfo/display/DisplayPreferences.kt:42` — `write`.
`app/src/main/java/kniezrec/com/flightinfo/monitoring/BackgroundNotificationPreferences.kt:22` — `write`.
`app/src/main/java/kniezrec/com/flightinfo/route/RouteController.kt:162` — `persist`.

**Problem**

All four stores call `SharedPreferences.Editor.commit()`. Settings click callbacks invoke them directly on the UI thread. Route choose/clear and restore's main-executor callback also commit synchronously, and callers ignore the Boolean persistence result.

**Why it matters**

Disk latency blocks input and rendering; slow storage can cause visible stalls and contributes to ANR risk. The UI can also report a successful saved selection when a durable write failed.

**Technical analysis**

The call chain is visible in `MainActivity.kt:183–197`, `:257–265`, and `RouteController.restore():144–150`. The repository worker does not cover persistence. Fake preference editors always return true, so tests exercise neither disk latency nor failure.

**Recommended approach**

Use `apply()` for ordinary preference changes when its durability semantics meet the requirement, or serialize durable writes on an I/O owner/DataStore and publish their outcome. For route choices requiring an explicit persistence guarantee, handle success/failure and preserve write ordering. Do not merely launch independent asynchronous commits that can reorder user edits. Verify main-thread disk access with StrictMode and cover failed persistence.

---

### [M06] Negative altitude is formatted as a positive number

**Severity:** Medium — WARNING

**Type:** Confirmed Problem

**Location:**
`app/src/main/java/kniezrec/com/flightinfo/displayunits/UnitPresentation.kt:55` — `formatUnitNumber`, especially line 67.
`app/src/main/java/kniezrec/com/flightinfo/ui/gnss/FlightParametersCard.kt:205` — altitude row.

**Problem**

`formatUnitNumber` always formats `abs(value)`. It restores a sign only when `signed = true`. Altitude passes `signed = false`, so a valid altitude of -50 metres is shown and spoken as 50 metres; feet conversion has the same problem.

**Why it matters**

Negative GNSS altitude is valid, including below-sea-level locations. “No explicit positive sign” is not equivalent to “discard the negative sign.” This changes the meaning of the measured value.

**Technical analysis**

The controller accepts any finite altitude and `convertAltitude` preserves its sign. The error is isolated to presentation. Existing tests cover negative explicitly signed vertical speed, but do not cover ordinary negative formatting or negative altitude in the card.

**Recommended approach**

Format the original value for normal numeric presentation. Add a positive sign only for modes that require it, while preserving localized negative formatting. Test negative, zero, and positive altitude in metres and feet, including accessibility text and at least two locales.

---

### [M07] Fresh Android 13+ installs have no in-app path to enable notifications

**Severity:** Medium — WARNING

**Type:** Confirmed Problem

**Location:**
`app/src/main/AndroidManifest.xml:10` — notification permission declaration.
`app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt:136` — only runtime permission launcher; settings handling at line 193.
`app/src/main/java/kniezrec/com/flightinfo/monitoring/LocationForegroundService.kt:183` — `canPostNotifications`.

**Problem**

The app declares and checks `POST_NOTIFICATIONS`, but never requests it and offers no notification-settings remediation. The “Show background notification” preference defaults to enabled regardless of system authorization.

**Why it matters**

On a fresh Android 13+ install targeting this SDK, normal notification delivery is disabled until permission is granted. The app can legally run a foreground service, but its waiting notification is absent from the notification drawer and the expected return/dismiss interaction is unavailable. The switch's On state does not reveal this limitation.

**Technical analysis**

The location permission request contains only fine/coarse permissions. The service checks notification permission to choose copy, not to obtain authorization. The fresh-install case differs from restored/upgraded apps that may receive pre-grants. This finding does not claim that denied notification permission makes `startForeground` illegal.

**Recommended approach**

Offer a contextual notification permission request when the user enables or first uses background waiting. Handle dismissal/denial without repeated prompts, and expose an accurate blocked-by-system state with a link to notification settings when appropriate. Check app/channel notification availability as well as the runtime grant. Test fresh install, denial, grant, channel blocking, and return from Settings.

---

### [M08] Approximate permission masks permanent denial of a precision upgrade

**Severity:** Medium — WARNING

**Type:** Confirmed Problem

**Location:**
`app/src/main/java/kniezrec/com/flightinfo/permission/LocationPermissionState.kt:20` — `locationPermissionState`.
`app/src/test/java/kniezrec/com/flightinfo/permission/LocationPermissionStateTest.kt:58` — approximate-only expectation.

**Problem**

Whenever coarse location is granted, the state is `Requestable`, before request history and fine-location rationale are considered. There is no separate record of attempted precision upgrades.

**Why it matters**

If the user retains approximate location but permanently denies fine location, the UI continues offering the permission request action. Android can immediately deny subsequent requests, leaving the user stuck without the app-settings action needed to recover.

**Technical analysis**

The state reducer returns Requestable for coarse=true, fine=false, previously-requested=true, rationale=false. This can represent both a still-requestable first precision upgrade and a blocked upgrade; the single history flag cannot distinguish them. The unit test currently enshrines one answer for all such states. The correct remedy is not simply reordering the branches, which could prematurely send an initial approximate grant to Settings.

**Recommended approach**

Track whether a fine-location upgrade has been attempted separately from the initial coarse/fine request, or provide a non-dead-end Settings action for the ambiguous approximate-only state. Refresh platform grants after returning from Settings. Add device/API tests for initial approximate grant, first precision upgrade, repeated denial, permanent denial, and subsequent Settings grant.

---

### [M09] Package-visibility preflight can reject external actions that Android could launch

**Severity:** Medium — WARNING

**Type:** Potential Risk

**Location:**
`app/src/main/java/kniezrec/com/flightinfo/about/AboutPlatform.kt:61` — `AndroidExternalIntentLauncher`.
`app/src/main/AndroidManifest.xml:1` — no intent visibility queries in the app manifest.

**Problem**

The launcher refuses to call `startActivity` unless `resolveActivity(packageManager)` returns a handler. The app targets Android package-visibility restrictions and declares no matching `<queries>` for mail/store intents.

**Why it matters**

On devices where the handler is not automatically visible to this app, resolution can return null even though starting the intent would succeed. Feedback or rating then reports that no application is available despite an installed handler.

**Technical analysis**

Activity launch authorization is not the same as package-query visibility. Actual behavior depends on installed handlers, their automatic visibility, and the final merged manifest; that manifest could not be generated locally. The Robolectric tests supply fake resolver results and do not exercise Android's filtering.

**Recommended approach**

Prefer attempting `startActivity` and handling `ActivityNotFoundException`/`SecurityException`, retaining market-to-web fallback. If preflight resolution is needed for a UI decision, declare narrow matching intent queries and verify the merged manifest. Test on Android 11+ with installed email/store/browser handlers and with handlers absent. Do not add broad package-query permission.

---

### [M10] Live telemetry has no freshness state after GPS updates stop

**Severity:** Medium — WARNING

**Type:** Confirmed Problem

**Location:**
`app/src/main/java/kniezrec/com/flightinfo/flight/FlightParametersController.kt:79` — `onLocation`.
`app/src/main/java/kniezrec/com/flightinfo/route/RouteController.kt:44` — `onFix`.
`app/src/main/java/kniezrec/com/flightinfo/nearby/NearbyCityController.kt:165` — `present`.
`app/src/main/java/kniezrec/com/flightinfo/flight/AndroidFlightLocationPlatform.kt:23` — callback adapter.

**Problem**

After a valid fix, speed/altitude, GPS bearing, remaining distance, and ETA remain available until another fix or a lifecycle reset. There is no age threshold, last-updated presentation, or signal-loss transition. The nearby city's local time is a formatted string sampled only when a city lookup finishes.

**Why it matters**

Losing reception inside an aircraft while leaving location services enabled is an ordinary operating condition. The screen continues presenting old readings as current, and its city clock can stop advancing. The service's eligibility poll only checks permissions/providers; it does not detect lost reception.

**Technical analysis**

`elapsedRealtimeNanos` is used for vertical speed and route ordering but is not retained as freshness metadata in presentation state. No timer or timeout invalidates a fix. Pressure/sensor events can still update other parts of the dashboard, making the frozen GPS-derived values less obvious. The app disclaimer does not communicate the age of a particular reading.

**Recommended approach**

Define a product-appropriate freshness interval using monotonic elapsed time. Represent last-known versus current data explicitly, expose age where useful, and stop calculating current ETA/vertical speed from expired samples. Share the accepted fix/freshness decision across consumers. Update city time from a lifecycle-bound clock using the selected zone without re-querying the database. Test loss/reacquisition, long gaps, out-of-order fixes, and wall-clock changes.

---

## Low Findings

### [L01] Tests exercise a monitoring state machine that production never uses

**Severity:** Low — INFO

**Type:** Recommendation

**Location:**
`app/src/main/java/kniezrec/com/flightinfo/monitoring/BackgroundMonitoringSession.kt:5`.
`app/src/test/java/kniezrec/com/flightinfo/monitoring/BackgroundMonitoringTest.kt:25`.
`app/src/main/java/kniezrec/com/flightinfo/map/MapArchiveCopier.kt:52` — similar unused `MapLoadAttemptGate`.

**Problem**

`BackgroundMonitoringSession` models foreground/background/fix transitions but is only instantiated by its tests. Production independently implements those rules in the bridge and service. Likewise, a tested `MapLoadAttemptGate` is not the gate used by `MainActivity`, which maintains its own token.

**Why it matters**

Passing tests for these disconnected helpers can give misleading assurance about lifecycle rules and leave parallel implementations to drift.

**Technical analysis**

Repository-wide reference inspection found no production use of the monitoring state machine or map gate. The real service/session tests do add value, but these helper tests do not establish correctness of the Activity integration.

**Recommended approach**

Either use the tested transition logic in the production owner or remove the unused abstractions and move the behavioral assertions to the real bridge/service/Activity boundary. Do not add another architectural layer merely to preserve unused code.

---

## Architecture Review

### Structure and boundaries

One `:app` module contains feature-oriented packages. There is no `buildSrc`, included build, convention-plugin module, DI framework, Room, network client, or WorkManager integration. A larger module or “clean architecture” migration is not a prerequisite for this application's size. The concrete need is consistent ownership and testable integration.

The principal data paths are:

```text
Android GPS / GNSS
  → service-owned LocationGnssMonitoringSession
  → BackgroundMonitoringBridge
  → Activity-owned flight/GNSS controllers
  → flight readings, course bearing, nearby lookup, route calculation, map rules
  → Activity Compose state → dashboard cards / AndroidView maps

Compose route interaction
  → MainActivity picker fields → RouteController
  → shared city worker → AndroidNearbyCityRepository → packaged SQLite data
  → main executor callback → Activity fields → picker

Rotation-vector / pressure sensors
  → Android adapters → Course/Horizon/Pressure controllers
  → Activity Compose state → cards

Settings / confirmed route selection
  → preference stores / RouteController → SharedPreferences
```

### UI and state ownership

Most cards are functions of typed state and callbacks, with meaningful waiting, unavailable, and error representations. Unit conversion is centralized. The Activity is both the composition root and a sizeable coordinator: it owns navigation, many independent picker fields, service handoff, multiple controller lifetimes, and map preparation. H01, H04, M01, M02 and M03 show concrete costs of these responsibilities being spread across independent fields and callbacks.

A consolidated picker state and one authoritative monitoring owner would reduce invalid combinations without dictating a particular architecture. A retained presentation owner could improve configuration continuity and integration testability; a ViewModel is one option. Android window/orientation effects and permission launchers should remain with a lifecycle-aware UI host.

### Domain/business logic and repositories

Geographic distance, route ETA, unit conversion, heading normalization and horizon mapping are separated from drawing and generally testable. Route endpoint validation checks coordinates/time zones. City repositories do database/file work off the main thread and close cursors/databases with `use`. They do not navigate or directly manipulate UI.

`NearbyCityController` and route models format localized time into presentation values; this is reasonable for a presentation controller but means locale/time changes must be accounted for. The city repository's full scans and RouteController's direct persistence are concrete performance/testability concerns (M04, M05), not reasons to add a domain layer everywhere.

### Services and dependency injection

The service owns the actual location and GNSS registrations, which avoids dual foreground/background subscriptions. It is non-exported, uses the location foreground-service type, immutable PendingIntents, and `START_NOT_STICKY`; stopping after the background fix and not guaranteeing indefinite process survival match the documented feature intent.

Manual constructor injection into controllers is effective. Repositories receive application context; the orientation source appropriately needs display context. The Activity and service still construct platform implementations internally, while the singleton bridge captures Activity/controller callbacks. That makes full-path substitution difficult and assumes one active Activity consumer. The bridge should have explicit attach/detach ownership and a status contract; service generations already provide a useful foundation. No unconditional Activity leak is asserted: callbacks are replaced on recreation and cleared on ordinary destruction, although multi-instance and lifecycle-overlap behavior needs integration tests.

### State management, asynchronous operations and lifecycle

Compose `mutableStateOf` is used for presentation. There is no Flow event delivery or collector lifecycle to audit; absence of those libraries is not itself a finding. Most platform callbacks and result delivery are on the main executor. City work uses a shared single-thread executor and map preparation a separate executor. Tokens prevent many old-session callbacks from publishing.

Ownership is less complete at operation boundaries: ignoring a callback does not finish the corresponding UI loading state, and stopping an executor does not necessarily interrupt file copying. Session generation, individual request identity, user-edit revision, and data freshness are distinct concepts and should be modeled distinctly. `onPause` stops sensors and foreground city/route work; background location is deliberately retained according to service rules.

## ViewModel Review

There are **no classes extending AndroidX ViewModel** and no `viewModelScope`/`SavedStateHandle` in this repository. This section reviews every important equivalent state owner. Adding ViewModels indiscriminately would not repair the identified integration bugs.

| State owner | Responsibilities, dependencies and testability | State/events, async behavior and errors | Lifecycle, races and restoration |
| --- | --- | --- | --- |
| `MainActivity` | Composition root, permission/settings effects, navigation, service handoff, controller construction and picker/map orchestration. Hardwired platform constructors and global bridge hinder complete integration tests. | Compose fields receive callbacks directly. Only coroutine scope is the composition-owned snackbar scope. Numerous independent picker flags allow invalid combinations; service-start exceptions are discarded. | Recreates all controllers/executors; user navigation and draft state are not saved. M01/M02/M03, H01/H04 apply. Preference-backed settings and confirmed IDs do restore. |
| `LocationPermissionStateController` | Small injected platform/history boundary with deterministic tests. No inappropriate UI or repository work. | Synchronous permission reducer; events are explicit refresh/request-history methods. No Flow or coroutine. | Current grant is refreshed on resume and launcher result. Initial request history is durable. Approximate/blocked precision states need M08's distinction. Backup restoration of request history merits a device test. |
| `GnssStatusController` | Converts satellite callbacks to Waiting/Available and models hardware/provider/registration errors in standalone mode. Injectable platform. | External attachment bypasses error derivation. `stop()` does not publish cleared state; standalone callback has no generation token, while the production service path has upstream generations. | Test standalone and actual external mode separately. H01 is the production defect. Historical callbacks in standalone mode are a test gap, not a claim that the production service's token guard is absent. |
| `FlightParametersController` | Derives speed, altitude and vertical speed; forwards accepted fixes to other consumers. Injectable platform and callbacks. | Internal generation checks are good. Vertical speed uses monotonic timestamps and rejects nonpositive intervals. External mode assumes registration success; no error/freshness state. | Stops/reset samples between foreground starts but remains attached during intended background monitoring. H01/M10 apply. Do not restore raw readings as fresh after process death. Test missing altitude between samples and actual service termination. |
| `PressureController` | Optional sensor registration, finite/nonnegative validation, nullable pressure callback. Easily faked. | One active session token rejects late callbacks. No independent loading/error model; optional missing pressure is represented by null. No coroutine/Flow. | Stops on pause/destroy and resets pressure. That lifecycle is appropriate; source does not establish a persistent registration leak. Test platform false/throw paths and registration cleanup on actual hardware. |
| `CourseController` | Compass presentation plus supplementary GPS bearing using shared orientation source. Injected adapter. | Waiting/Unavailable/Error/Available states; session token and finite normalization. No one-shot event queue. GPS bearing can remain old when reception stops (M10). | Clears bearing and heading on stop/retry, with no saved raw sensor state. Optional sensor failure is handled. UI retry is foreground-guarded. |
| `HorizonController` | Attitude display and pitch calibration; injected shared orientation adapter. | Clear states including Recalibrating; finite checks, display clamping and generation guard. First new sample is the reference. No coroutine/Flow. | Calibration resets when the foreground session restarts, including recreation. This is deliberate in the existing tests; no permanent-calibration requirement is invented. Verify real display rotations, including changes that do not recreate the Activity. |
| `ForegroundCourseObservationCoordinator` | Coordinates flight/course/nearby start-stop; testable controller dependencies. | Provides separate full stop and foreground-only stop. External flight mode can report success without a live service. | Useful sensor/location lifetime separation, but service status must govern actual observation (H01). Existing failure test covers standalone mode only. |
| `NearbyCityController` | Coalesces position lookup, geographic distance, zone/time presentation; injects repository, worker, callback executor and clock. | Synchronized pending request state, session/fix guards, failures mapped to Unavailable. Latest request replaces queued work; exceptions are not thrown into UI. | Good obsolete-session protection. M04/M10 cover full scan cadence and stale time. Arrival order, rather than elapsed timestamp, defines newest input; test out-of-order delivery and executor shutdown. No durable user input to restore. |
| `RouteController` | Endpoint mutation, restore/persist, query/nearest operations and live route calculation. Injected executors/repository/clock, but direct SharedPreferences dependency. | Main-thread state plus worker reads and callback delivery. Request callbacks share session identity. Restore error is transient: a later ordinary `publish()` can remove it even before successful restoration. | Durable endpoint IDs survive process death; live fix appropriately resets. H03/M02/M05/M10 apply. Missing edit revision and operation identity are the principal races. Current direct-executor tests do not cover them. |
| `MapSessionRules` / Activity map loader | Validation, first-fix/recenter policy and marker bearing; repository prepares archive off-main. | Rules are ordinary mutable fields with a disconnected UI version counter (H04). Archive result is guarded by Activity token. | Map is removed/reset on pause. Viewport/expanded state is not restored across recreation. Session reset is explicit; resource publication after pause is guarded. Test interrupted first copy and overlapping Activity instances. |
| `BackgroundMonitoringBridge` / `LocationForegroundService` | Process-local event dispatch, visibility/fix flags and actual platform lifetime. Service is partly injectable through protected hooks. | Service-generation guard rejects obsolete callbacks; no retained telemetry/status stream. Events are direct callbacks, not replaying flows. Errors/status transitions need H01. | `START_NOT_STICKY`, task removal, provider receiver and polling cleanup are explicit. Global handlers assume one current consumer; recreation/multiple Activities and stop/start overlap need integrated coverage. Notification authorization is separate from preference (M07). |

## Compose / UI Review

### What works

Most cards hoist state and emit callbacks. Optional/unavailable data has placeholders rather than fabricated readings. Many actions have 48dp or larger minimum targets, roles, descriptions and keyboard-focus indication. Compass/horizon canvases have textual alternatives; unit labels expand for accessibility. Resource-backed copy is widespread. Nearby retry's `collectIsFocusedAsState()` collects a local interaction source, not long-running application data.

`rememberCoroutineScope` is used only for snackbars and is canceled with the composition. The small picker `LaunchedEffect`s synchronize local selection/validation; there is no unbounded application coroutine launched during recomposition. The dashboard map has explicit disposal, and osmdroid 6.1.20 also defaults to destroying its resources on `onDetachedFromWindow`. A missing explicit `PickerMap.onDetach()` was therefore **not** reported as a proven leak.

### State, recomposition and side effects

H02 and H04 are the highest-priority rendering concerns. Renderer objects should be imperative holder state, while values that drive rendering must be observable inputs. M01/M02 show that a working stateless picker can still be incorrectly wired by its host. `remember` is appropriate for purely transient focus/menu state; restorable user input needs M03.

The GNSS `Crossfade` uses the entire satellite list state as its target. Changing signal strengths can recreate/fade the card even when its visible state category is unchanged. The dashboard is an eager scrolling Column and keeps sensor-driven content/map composed behind Settings. These are opportunities to profile on a representative device, not measured performance failures; first address the known repeated city work and picker update pattern.

### Navigation, accessibility and restoration

Simple overlays and BackHandlers can be sufficient for this small app. The current Settings overlay deliberately keeps the map alive, which supports viewport continuity within the same Activity. However, z-order/background paint alone is not a guarantee of accessibility or keyboard modality: verify TalkBack/focus cannot enter obscured dashboard controls behind Settings or the route picker. Use focus/semantics containment if needed while retaining the map. No device accessibility audit was performed.

Large-font/RTL tests exist, but several assert only node presence rather than usable bounds/interaction, and the suite currently cannot compile (H05). Test the picker with the keyboard open, large text, landscape, actual preview tiles, and a long result list. A content description for a map's long-press gesture does not itself provide an equivalent accessibility action; city-name search provides an alternative, which should remain reachable.

Color behavior should also be inspected in both system themes: the app mixes fixed purple surfaces with dynamic Material colors, and the route picker uses unqualified Material text on a fixed dark background. Do not infer contrast compliance for every surface from the one cyan-on-purple test. These are targeted visual/accessibility verification recommendations rather than additional counted findings.

## Concurrency and Lifecycle Review

- **Coroutine scopes and cancellation:** no application coroutine pipelines, jobs, callbackFlows or SharedFlow event queues exist. Snackbar scopes are composition-bound. Executor work is not coroutine-cancelable; session guards suppress obsolete delivery but do not by themselves cancel I/O or terminate UI loading.
- **Thread confinement:** production location/GNSS callbacks and city-result publication use `mainExecutor`. Sensor registration is performed from the main thread. Most controllers assume UI-thread callers, which is consistent with their current call sites; document/assert that contract if reused. Nearby's worker request state is synchronized. No general unsynchronized multi-thread UI mutation is asserted without a call path.
- **Concurrent operations:** restore versus edit is a real interleaving (H03), and a single city worker does not solve it. Search/nearest identity is too broad (M02). Nearby coalescing is good but can monopolize the shared worker under sustained slow lookups (M04).
- **Service ownership:** real subscriptions belong to the service session, with cleanup on partial registration failure and generation checks. The missing service-status channel leaves consumers attached to nonexistent monitoring (H01). Pause is used as visibility, so configuration changes and short focus losses exercise background rules; add transitions with an already acquired fix and rapid stop/restart, not just no-fix manual bridge toggles.
- **Resource lifetime:** streams/cursors/SQLite handles use `use`; executors are shut down on Activity destruction; sensors stop on pause; the service removes its Handler runnable and receiver. `shutdownNow()` cannot guarantee interruption of blocking asset copies. Multiple repository instances share fixed cache/temp filenames, so rotation during first asset copy deserves a stress test before asserting cross-instance safety. No reproducible corruption was demonstrated here.
- **Process death:** preference-backed confirmed route/settings restore; unconfirmed UI state does not (M03). Process-local bridge state is intentionally not durable, and service automatic restart is not promised. Do not introduce background-location permission or persistent work solely to mimic a lifecycle state holder.
- **Freshness and ordering:** route rejects nonincreasing fix timestamps; flight vertical speed rejects invalid intervals; map/nearby accept valid coordinates without a shared freshness decision. A common accepted-fix model would prevent consumers from disagreeing about whether data is current (M10).

## Testing Review

### Existing coverage

The source contains 125 JVM `@Test` methods across 26 files and 43 instrumentation `@Test` methods across 7 files, including template tests. This is an inventory, not a passing-test claim or coverage percentage. Controller tests exercise unit conversions, route calculations, preferences, permissions, sensor unavailability, generation guards, nearby request coalescing and failures. Archive tests check failed-copy cleanup and atomic replacement. Robolectric service tests cover registration ownership, notifications, eligibility, destruction and obsolete callbacks.

Compose tests cover state presentations, unit selection, semantic descriptions, route drafts and map controls. They host a generic `ComponentActivity`, manually reproduce navigation/state wiring, and frequently omit the real map. Consequently they cannot validate the actual `MainActivity → controller → service/repository → UI` path. H05 prevents the suite from being a functioning gate at present.

Some test doubles are materially simplified: preference editors mutate their backing map before commit and always succeed; route workers/callbacks are usually direct executors; service tests override foreground notification startup; fake city datasets are tiny. These are reasonable for isolated assertions but require complementary integration tests. The helper-only state-machine tests in L01 should not be treated as service lifecycle coverage.

### Verification performed and limitations

| Verification | Result and interpretation |
| --- | --- |
| Initial `git status`, branch and diffs | Repository found under `smart-flight-2`; initial branch `main`, clean tree; review branch created without replacing user work. Initial commands in the parent workspace correctly failed because it is not a Git repository. |
| `./gradlew ktlintCheck --console=plain` | Passed; 9 actionable tasks executed. This validates formatting, not Kotlin type correctness or Android behavior. |
| `./gradlew testDebugUnitTest lintDebug assembleRelease --console=plain` | Failed before task execution: Android SDK location not found. No Gradle unit-test, lint, or release result is claimed. |
| SQLite read-only inspection | `PRAGMA integrity_check` returned `ok`; 47,317 city rows; no out-of-range coordinates. Schema has the ID primary key and no geographic/name index. |
| Map ZIP inspection | 4,356 archive entries including directories; `ZipFile.testzip()` found no CRC error. This does not verify tile coverage, visual quality or rendering. |
| Dependency source inspection | osmdroid 6.1.20 source confirms automatic `onDetach()` on view detach by default; used to reject a suspected leak. |
| Android/device/remote CI verification | Not performed. No SDK/device was available; the CI YAML was inspected, not its remote run history. |

Supplemental JVM checks use a scratch directory outside the repository and the unchanged production/test source files. They use a cached standalone Kotlin compiler and an Android API signature jar for interfaces such as SharedPreferences; this is not the app's AGP/Compose toolchain or an Android runtime. Their result is recorded below once complete and must not be read as a substitute for the blocked Gradle/device suite.

### Priority testing gaps

1. **Actual Activity/service integration:** disabled provider/hardware, start failure, registration failure, eligibility loss while visible, retry, fresh notification permission, background first fix, rapid foreground return, configuration change with an existing fix, and notification dismissal.
2. **Deterministic route interleavings:** use separately controlled worker and callback queues; restore versus choose/clear, pause before completion, cancel/open another endpoint, repeated search, and nearest/search overlap. Assert both UI and persisted IDs.
3. **Map rendering:** real bundled archive, non-null selected city, sustained idle after marker creation, first/subsequent GPS fixes with no unrelated state changes, stale callbacks after disposal, and view/resource counts during repeated open/close.
4. **Recreation/process restoration:** in-progress picker, query/draft, Settings and orientation changes, confirmed endpoints, preference failures, and transient state reacquisition.
5. **Real repository/asset tests:** SQL search semantics, exact ID lookup, actual time-zone records, antimeridian/polar/near-antipodal coordinates, asset reload/corruption and concurrent initialization. Current geographic-distance helper should be tested near floating-point boundaries before considering clamping of its haversine intermediate.
6. **Measurement validity and presentation:** negative altitude, monotonic-age expiry, missing fields and long sample gaps, out-of-order fixes, stale ETA and local-clock behavior.
7. **Accessibility/device matrix:** TalkBack and keyboard modality, large fonts/RTL, picker with IME, all theme combinations, API 31 minimum and modern notification/foreground-service restrictions. Repair compilation and execute instrumentation before relying on existing accessibility assertions.

## Build / CI Review

### Gradle, plugins and dependency management

The project uses Gradle 9.6.0 with a distribution SHA-256, AGP 9.4.0, Kotlin Compose plugin 2.2.10, Compose BOM 2026.02.01, and fixed catalog versions. Plugin/repository declarations are centralized; `FAIL_ON_PROJECT_REPOS` prevents arbitrary subproject repositories. AGP's built-in Kotlin support means absence of `org.jetbrains.kotlin.android` is not itself an error. There are no custom convention plugins to review.

Application configuration is compile/target SDK 37 and minimum SDK 31. Java source/target compatibility is 11. This is distinct from the Gradle runtime: `gradle-daemon-jvm.properties` requests JDK 25 while CI explicitly installs JDK 17. Gradle successfully started/configured locally, so this mismatch is not reported as a proven build failure. Align/document the intended daemon/toolchain and provision it explicitly to avoid hidden downloads and misleading CI setup.

Dependencies are fixed but there is no checked-in dependency lock/verification metadata. Consider checksum verification and reproducible SDK/toolchain provisioning for releases. The app directly uses coroutine APIs through transitive dependencies; declaring direct dependencies for directly used APIs makes upgrades less fragile. No vulnerability claim is made solely from library age or version numbers.

### Variants and release readiness

There are debug/release build types, no flavors, and release `optimization.enable = false`. The checked-in keep-rule file is a template. No release signing configuration, signed bundle job, release artifact gate or versioning automation is present. Credentials should remain outside source control; their absence here does not prove external signing is unavailable. Before release, define a secure signing/versioning process and validate an actual release artifact. Evaluate optimization with osmdroid on a representative device rather than enabling it blindly.

The app manifest requests precise/coarse location, location foreground-service and notification permissions. The service is non-exported and the exported Activity is the launcher. No Internet permission or application networking stack is declared; both map views explicitly disable data connections. About launches external apps rather than an in-process network client. No hardcoded authentication secrets were identified.

Backup is enabled with default/template rules. The copied city catalog lives under `filesDir` even though it is derivable from an immutable asset; routes/preferences are also eligible for backup, whereas the map copy is under cache. Define what should restore, exclude reproducible assets where appropriate, and decide whether permission-request history should transfer to a new device. Treat this as a backup-policy recommendation, not an established sensitive-data leak.

### CI and supporting workflow

CI runs on pushes and PRs, has read-only repository permissions, caches Gradle, checks formatting, runs debug unit tests, builds a debug APK, and uploads it. These are useful basics. However, `assembleDebug`/`testDebugUnitTest` do not compile instrumentation tests (H05); no emulator job, Android lint task, release assembly, signed release test, or test-result upload is configured. Add fast Android-test compilation first, then device execution and release/lint gates with useful reports.

The Python orchestrator and agent TOML files are development automation, not runtime app code. They invoke commands as argument lists and write workflow JSON through temporary-file replacement. Their reviewer PASS state and historical review documents are human/agent judgments, not substitutes for executed CI. This review did not execute the orchestrator or follow its task-specific branch/push instructions; the explicit requested documentation-only workflow governs this review.

## Positive Findings

- Narrow platform interfaces make controllers testable without Android hardware, and explicit worker/callback executors make deterministic concurrency testing feasible.
- Service-owned GNSS/location subscriptions and cleanup on partial registration failure are a sound foundation; the service/session and bridge both guard old callback generations.
- Course, horizon, pressure, flight and nearby controllers reject many invalid or obsolete callbacks. Route uses monotonic ordering; vertical speed does not divide by nonpositive intervals.
- Nearby lookup coalesces pending positions, avoiding an unbounded queue for that feature.
- Database/cursor/stream handles use structured `use` cleanup. Offline archive preparation uses a temporary file and atomic replacement where available; no network fallback is silently introduced.
- Unit preferences use stable keys rather than enum ordinals. Confirmed route endpoint IDs survive recreation and invalid city/time-zone records are rejected before selection.
- The service is non-exported, PendingIntents are immutable, notifications do not include raw coordinates, and no unnecessary background-location permission is requested.
- Many UI controls provide meaningful text, minimum touch targets, focus indication and accessible units; rendering APIs are mostly state-hoisted and can be exercised independently.
- Gradle versions are centralized, the wrapper has a checksum, formatting is gated, and JVM test/ APK jobs exist. Formatting actually passed during this review.

## Recommended Priorities

1. **Restore a trustworthy test gate (H05):** repair instrumentation compilation, add Android-test assembly to CI, and run focused UI/device tests. Add controllable executors and an Activity/service integration seam so the following fixes are reviewable.
2. **Make monitoring state truthful and recoverable (H01):** propagate service status/failures, clear unavailable live data, and make retry restart the real session. Verify lifecycle stop/start overlap and disabled-provider recovery.
3. **Repair route correctness (H03, M01, M02):** protect restore from later edits; wire nearest selection; model picker/request identity and terminate loading on cancellation.
4. **Verify and repair map rendering contracts (H02, H04):** remove snapshot feedback in imperative marker bookkeeping and pass observable aircraft-position inputs to the renderer. Reproduce with the real preview and default compiler settings.
5. **Preserve measurement meaning (M06, M10):** retain negative altitude signs and introduce a shared freshness model for GPS-derived values and ETA; update city time independently of location lookups.
6. **Complete permission/external-action paths (M07, M08, M09):** notification opt-in/system-blocked state, recoverable precise-location upgrade denial, and visibility-safe intent launching.
7. **Preserve user work and improve runtime cost (M03, M04, M05):** restore navigation/drafts, avoid repeated catalog materialization, and move durable disk work off the main thread with failure/order handling.
8. **Finish release verification and reduce misleading scaffolding (L01):** connect tests to production owners, add lint/release/device gates, document toolchain/signing/backup choices, and profile/accessibility-test the assembled app.

This ordering reflects engineering dependencies and impact, not a ranking of the developer or the use of AI.

## Final Assessment

The repository has useful, testable components and reasonable offline/platform boundaries, but it is not ready to treat as production-validated. The largest gaps are between components: monitoring status, route operation ownership, renderer observation, and the real UI test gate. Addressing those specific contracts is more valuable than imposing a broad architectural rewrite. A successful formatting run and isolated controller tests do not replace an SDK build, an executable instrumentation suite, and lifecycle/device verification of the complete data flow.
