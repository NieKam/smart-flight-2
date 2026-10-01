# TASK-005 — Review iteration 1

## Result
**PASS.** I found no blocking issues and nothing that needs escalating. One criterion (AC1, the PR description) is outside what I'm allowed to inspect, so the orchestrator needs to confirm it. Details are in the table below.

## Acceptance criteria
criterion | status | verified by
---|---|---
PR description records KSP/Hilt/androidx.hilt versions with sources, the step-1 CI result, and why `org.jetbrains.kotlin.android` was needed if it was | NOT VERIFIED by reviewer | I can only use git commands and cannot read the PR body. Orchestrator: check it lists KSP 2.3.12, Hilt 2.60.1 and androidx.hilt 1.4.0 with sources, and step-1 run 36420757862 (green, no fallback). The commit 5a2614c message confirms the probe versions. No fallback was used, so no README "Planner decisions" line is needed.
`@HiltAndroidApp` Application registered; Activity and service are `@AndroidEntryPoint` | MET | Source review: `SmartFlightApplication.kt`; `AndroidManifest.xml` `android:name=".SmartFlightApplication"`; `MainActivity.kt:93`; `LocationForegroundService.kt:34`
Build uses KSP for Hilt; no kapt anywhere | MET | `app/build.gradle.kts` has `ksp(libs.hilt.compiler)` and the ksp/hilt plugins. `git grep` finds no kapt, `kotlin.android` or `builtInKotlin` in `*.kts`/`*.toml`/`*.properties`. CI build green (as reported).
Single shared instances of the preference stores and city repository | MET | All three stores are `@Provides @Singleton`. `AndroidNearbyCityRepository` is `@Singleton @Inject` plus `@Binds`, and both controllers use the one injected field (`MainActivity.kt:547,558`). `HiltSingletonScopeTest` asserts `assertSame` across activity recreation and between the Activity and the service. That test would fail if `@Singleton` were removed.
TASK-004 characterization tests and all existing unit tests pass | MET (per CI as reported) | Run 36423205574 green on 8e46b46. The diff touches no existing test files; the only test change is the new `HiltSingletonScopeTest`.
App launches and the dashboard receives GPS data on a device | HUMAN | Smoke test on a device

## Blocking findings
None.

## Escalations
None.

## Non-blocking findings (fine as follow-ups)
1. **Commit e76119b does not compile on its own** (`NearbyModule` binds `AndroidNearbyCityRepository` before its `@Inject` constructor exists in b8249ab). This makes bisect harder but breaks nothing at HEAD, and CLAUDE.md only asks for "small, focused commits", not that each commit compiles. Options: squash-merge, or accept. It should not be rewritten now, since that would cost the last CI round.
2. **Module placement.** The task says "pick one" (`di/` or per-feature `data/di`); the developer used both: cross-cutting bindings (system services, coroutines, clock) in `di/`, feature bindings in `<feature>/data/di`. The split is sensible and follows the "keep modules close to the feature" recommendation. The PR should state it; the e76119b commit message already does.
3. **Bindings that nothing uses yet** (`SensorManager`, `PackageManager`, `NotificationManager`, the three dispatchers, `@ApplicationScope`). The task lists every one of them explicitly (Scope → Modules). They are required by the task, not premature abstractions. Follow-up: wire them in when TASK-006+ introduce repositories/ViewModels. At that point the Activity's own `getSystemService(LocationManager/SensorManager)` calls (`MainActivity.kt:503,510,528`) and the service's `getSystemService(NotificationManager)` (`LocationForegroundService.kt:178,183,202`) can use the injected instances. Leaving those calls alone now is within scope, since the task only requires stores, preferences, repositories and the service's `LocationManager`/store.
4. **Visibility changes are justified.** `MainActivity` is public and injects fields of these types. A public property cannot expose an `internal` type, and field injection needs non-private fields, so `NearbyCityRepository` and `MapArchiveRepository` becoming public is the smallest fix. `AndroidNearbyCityRepository` correctly stays internal behind `@Binds`.
5. **Map-archive executor lifetime.** The task requires `MapArchiveRepository` to be a singleton, so the executor can no longer be shut down per Activity, and removing `close()` from `onDestroy` follows from that. Effects:
   - After recreation, a copy already in progress keeps running on the one shared worker instead of being interrupted.
   - Later `prepare` calls queue behind it. Previously a new repository/executor per Activity could run a second copy writing the same `osmdroid.zip.partial` at the same time as the old one, since file I/O ignores the interrupt.
   - The callback's token/`isForeground` guard (`MainActivity.kt:612`) drops late results for a destroyed Activity.
   - Downsides: one idle thread for the life of the process, and the old Activity stays reachable until its in-flight copy finishes.

   This is acceptable, and arguably safer. It should be listed as an internal (not user-visible) behavior change in the PR description.
6. **Test strategy (no `HiltTestApplication`) is acceptable.** The task allows using the real application with Robolectric, and the application does no work in `onCreate`. `HiltSingletonScopeTest.applicationIsTheHiltRoot` pins that Robolectric picks it up from the manifest.
7. **Minor.** The `SharedPreferences` providers are unscoped, which is fine because the platform caches instances per file name. The `@DisplayBehaviorPreferences`/`@DisplayUnitsPreferences`/`@MonitoringBehaviorPreferences` qualifiers are used only inside their own modules; they could be dropped later if nothing else needs the raw preferences.

## Behavior changes observed (should be in the PR description)
- `MapArchiveRepository` worker thread now lives for the whole process and is never shut down on Activity destroy (finding 5).
- Everything else looks unchanged:
  - Preference file names are the same: `display_behavior`, `display_units`, `BackgroundNotificationPreferencesStore.PREFERENCES_NAME`, `location_permission`, `route`. Keys are untouched.
  - `Clock.systemUTC()::instant` is equivalent to the controllers' `Instant.now()` default.
  - The service reads the same prefs file through a shared store instead of a new store each time; the underlying `SharedPreferences` instance is the same.
  - The service's `LocationManager` now comes from the application context rather than the service context. It is the same system service, and the test confirms it is the same instance under Robolectric.
- Injection timing is safe:
  - **MainActivity:** property initialisers run before `super.onCreate` (`permissionLauncher`, `mapRules`, lazies). None of them touches an injected field; every use is inside a `by lazy` or runs after `super.onCreate` (line 170).
  - **Service:** it has no `onCreate` override, so Hilt's generated base injects before `onStartCommand`. The `protected open` seams are kept (`isMonitoringEligible`, `showBackgroundNotification`, `canPostNotifications`, `startForegroundServiceNotification`, `createMonitoringSession`).

## Human checks on device
- Fresh install:
  - The app launches.
  - After the permission grant, the dashboard gets GPS/GNSS data.
  - The map card loads.
  - The nearby city and route search work.
- Upgrade over a previous build: unit/display/background-notification settings, permission request history and the saved route are kept (same pref files).
- Rotate while the map is loading: the map still ends up Ready and nothing crashes.
- Background the app with the background notification enabled: the foreground service starts and the notification appears. Turning location off stops it.

## Verification performed
- Source review of `git diff origin/ai-modernization...HEAD` (20 files), including the full `MainActivity.kt` wiring (lines 93-625), `LocationForegroundService.kt`, the `RouteController`/`NearbyCityController` clock defaults, all new modules and the new test. Checked for kapt and the fallback plugin with `git grep`; looked at commit contents and history.
- CI as reported by the orchestrator: step-1 run 36420757862 green on 5a2614c; run 36423205574 green on HEAD 8e46b46.
- Not verified:
  - The PR description (AC1).
  - Runtime behavior on a device.
  - That e76119b fails to compile; this is taken from the developer's statement.
  - Library versions against upstream release notes.

Relevant files:
- /home/ai-dev/smart-flight-2-modern/docs/tasks/005-hilt-di/task.md
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/monitoring/LocationForegroundService.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/map/MapArchiveRepository.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/nearby/AndroidNearbyCityRepository.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/di/ (ClockModule, CoroutinesModule, SystemServicesModule)
- /home/ai-dev/smart-flight-2-modern/app/src/test/java/kniezrec/com/flightinfo/HiltSingletonScopeTest.kt
