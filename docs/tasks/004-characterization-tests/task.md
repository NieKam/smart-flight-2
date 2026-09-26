# TASK-004 — Characterization tests that pin the dashboard orchestration before the refactor

## Goal
Pin the current observable behavior of `MainActivity` (the composition root and orchestrator) with JVM tests, so that the DI, Flow and ViewModel migrations (TASK-005 to TASK-016) can prove they did not change behavior. Tests assert at the UI/semantics and Android-effect level (texts, started services, stored preferences), not on internal classes that the migration will delete.

## Context
- CLAUDE.md workflow rule: "Before refactoring an area, ensure unit tests pin its current behavior."
- Architecture review F12: "The orchestration in `MainActivity` (F2) has no tests, unit or instrumented." "Pinning tests for `MainActivity` orchestration are needed before F2 can be done safely."
- Architecture review F7: "before deleting, pin with tests that the foreground path depends only on service-forwarded fixes." Architecture "Not verified" item 4 asks to confirm there is no hidden production path using the foreground registration.
- Observed orchestration in `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`:
  - Permission gate: only `PermissionOnboardingScreen` when not `Granted` (`:176,328-344`).
  - `onResume` (`:363-378`): marks foreground, calls `BackgroundMonitoringBridge.beginSession()` / `setActivityVisible(true)`, re-reads the three preference stores, applies display flags, starts `LocationForegroundService` when granted.
  - Fix fan-out (`:505-511`): each fix goes to the bridge, course (GPS bearing), nearby city, route and map rules. Fixes reach the Activity only through `BackgroundMonitoringBridge.forwardLocation` (`:153-156`).
  - Pressure merged into flight readings only when state is `Readings` (`:500-503,519-523`).
  - Settings overlay writes stores (`:183-202`).
  - `onPause` (`:387-396`) stops pressure, course, route, horizon, map; bridge visibility false.
- After TASK-002, Compose tests run under Robolectric, so `createAndroidComposeRule<MainActivity>()` can be used on the JVM.

## Dependencies
- TASK-002, TASK-003.

## Scope
Add `app/src/test/java/kniezrec/com/flightinfo/MainActivityCharacterizationTest.kt` (Robolectric, Compose test rule on `MainActivity`), covering at least:
1. Permission not granted: onboarding content is shown; dashboard cards, Settings and About are not reachable. (This behavior is changed on purpose later by TASK-024; the test is updated there.)
2. Permission granted (`ShadowApplication.grantPermissions(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)`): dashboard shows the GNSS card in "waiting" state; `LocationForegroundService` start was requested on resume (`shadowOf(application).nextStartedService`).
3. A fix forwarded with `BackgroundMonitoringBridge.forwardLocation(fix)` updates the Flight parameters card (speed/altitude text in the default units).
4. Satellites forwarded with `BackgroundMonitoringBridge.forwardGnssStatus(list)` update the GNSS card (used-count text).
5. Pressure: with a Robolectric pressure sensor (`ShadowSensorManager` / `ShadowSensor`), a pressure event is NOT visible before the first fix and IS visible after a fix. (Changed on purpose later by TASK-020.)
6. Settings overlay: changing the speed unit writes `display_units_speed` in the `display_units` SharedPreferences file and the flight card re-renders with the new unit.
7. Route persistence: pre-populate SharedPreferences file `route` with `route_departure_id` / `route_destination_id` (IDs of two real rows of `assets/databases/cities_info.db`) and assert that the route card shows both city names after launch.
8. Pause/resume: after `onPause`, forwarded fixes do not change the flight card; after `onResume`, they do again.
9. Foreground path depends only on forwarded fixes: with the service never started, no `LocationManager` listener is registered by the Activity (`shadowOf(locationManager).locationUpdateListeners` or equivalent is empty). This pins F7 before TASK-008 deletes the dead path.

Also add any missing controller-level pinning tests needed to describe behavior that TASK-009 to TASK-015 will move, if a behavior above cannot be reached through the Activity (document why).

## Out of scope
- Changing production code, except adding test-only seams if absolutely required. If a seam is required, it must be minimal (e.g. an `internal` constructor parameter with a default) and justified in the PR.
- Fixing any bug you find. File it in the PR description instead; parity bugs are already planned.

## Requirements
Required:
- Tests assert user-visible results (texts via string resources, content descriptions) or Android side effects (started services, stored preferences, registered listeners), not private fields.
- Tests reset `BackgroundMonitoringBridge` in `@After` (it is a global object; see `BackgroundMonitoringBridgeTest.kt:12-15`).
- Use string resources (`R.string...`) through `ApplicationProvider.getApplicationContext<Context>().getString(...)` for text lookups so a later localization task does not break the tests.

Recommendations:
- Write a small test helper for creating `FlightLocationFix` instances and advancing the main looper (`shadowOf(Looper.getMainLooper()).idle()`).

## Acceptance criteria
- [ ] Scenarios 1–9 exist and pass — verified by: CI unit test
- [ ] No production behavior change — verified by: code review
- [ ] ktlint passes — verified by: ktlint

## Tests to add or update
- New: `MainActivityCharacterizationTest` (+ helpers in `app/src/test/.../testutil/` if useful).

## Risks and edge cases
- The map card copies `osmdroid.zip` from assets on a background executor and then builds an osmdroid `MapView`. Under Robolectric this may be slow or fail. Scenarios do not need the map to be Ready; if map construction crashes the Activity, stop the map load at the Robolectric level (e.g. make the test not wait for it) rather than changing production code, and document it.
- Robolectric SQLite: the city DB is a real SQLite file; Robolectric's native SQLite mode should read it. If not, drop scenario 7 to a controller-level test and document.
- Foreground-service start under Robolectric is recorded, not executed; that is what we want.
