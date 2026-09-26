# TASK-008 — Remove BackgroundMonitoringBridge and the dead foreground registration paths

## Goal
The Activity collects fixes and satellites directly from `LocationRepository` (TASK-007). The process-global `BackgroundMonitoringBridge` event bus and the dead "own registration vs external session" code paths are deleted. The service derives its background decisions from injected, observable state instead of from a global object.

## Context
- Architecture review F3 (VIOLATION, HIGH): `monitoring/BackgroundMonitoringBridge.kt:18-127` is a mutable global `object` holding the service reference, generation counter, visibility flag, "has usable fix" flag and three Activity lambdas; plain `var`s touched from service and Activity without synchronization (correctness note). Direction: "Activity visible" and "has usable fix" become observable state; service and dashboard are both collectors of the repository.
- Architecture review F7 (OVER_ENGINEERING, MEDIUM), verified: `startObservation` always calls `attachToExternalSession()` (`MainActivity.kt:582-583`); `FlightParametersController.externalSession` is never reset (`FlightParametersController.kt:74`), so `start()` always takes the early return (`:31-35`); the Activity's `AndroidFlightLocationPlatform` (`MainActivity.kt:499`) and `AndroidGnssStatusPlatform` (`:492`) never register; `GnssStatusController.start()` is never called; `onRegistrationFailed → gnssStatusController.showError()` (`:504`) is unreachable.
- Parity review "Not verified": `startForegroundService` is wrapped in `runCatching` (`MainActivity.kt:569-573`); if it fails silently the dashboard stays in "Waiting" because it only gets data through the service. After this task the dashboard collects the repository directly, so a service start failure no longer blocks foreground data.
- Architecture "Not verified" 4 (dead paths) is pinned by TASK-004 scenario 9.
- Observed service/bridge interactions to preserve (`LocationForegroundService.kt:105-131`, `BackgroundMonitoringBridge.kt:66-96`):
  - Activity invisible + usable fix already received → service stops.
  - Activity invisible + notification preference off → service stops.
  - Notification shows the "waiting" copy when invisible, no usable fix, and notifications are allowed; otherwise the normal copy.
  - Preference disabled while invisible → stop.
  - Eligibility lost (permission revoked or providers off) → service stops; the Activity stops flight and GNSS controllers.
  - `beginSession()` resets "has usable fix" when no service is attached.

## Dependencies
- TASK-007.

## Target conventions (from CLAUDE.md)
Repositories expose `Flow`; structured concurrency; lifecycle-aware collection (`repeatOnLifecycle`); every abstraction must justify its existence.

## Scope
- `MainActivity`: collect `LocationRepository.fixes` and `.satellites` with `repeatOnLifecycle(Lifecycle.State.RESUMED)` (matches today's onResume/onPause gating) and feed the existing fan-out (flight controller, course bearing, nearby, route, map rules) and GNSS controller. This fan-out is temporary; TASK-009 to TASK-014 replace it with ViewModels.
- Replace the bridge's shared state with an injected `@Singleton` holder, e.g. `AppVisibility` (`StateFlow<Boolean>` set by the Activity in `onResume`/`onPause`). The service computes "has usable fix" itself from the fixes it collects, observes `AppVisibility` and `BackgroundNotificationSettingsRepository` (TASK-006), and applies the same rules as today.
- Eligibility: the Activity reacts to `LocationRepository.locationEnabled` (and permission state) instead of `setEligibilityLostHandler`; keep today's visible result (controllers stop → cards show their waiting state). The GPS-disabled UI is TASK-019.
- Delete: `BackgroundMonitoringBridge`, `BackgroundMonitoringService` interface, `BackgroundMonitoringBridgeTest`; the `externalSession`/`attachToExternalSession`/own-registration branches of `FlightParametersController` and `GnssStatusController` (they become pure consumers: `acceptLocationFix`, `acceptStatus`, `stop`); the unused `AndroidFlightLocationPlatform`/`AndroidGnssStatusPlatform` Activity instances and classes if nothing else uses them; `ForegroundCourseObservationCoordinator` may keep working with a simplified `FlightParametersController` (it is removed in TASK-010).
- Update tests: `FlightParametersControllerTest`, `GnssStatusControllerTest` (drop dead `start()` path cases; keep the consumer cases), `LocationForegroundServiceTest`, `ForegroundCourseObservationCoordinatorTest`, TASK-004 characterization tests (replace `BackgroundMonitoringBridge.forwardLocation` in tests by a fake `LocationRepository`/data source injected via Hilt test module or Robolectric `ShadowLocationManager.simulateLocation`).

## Out of scope
- ViewModels (TASK-009+). GNSS disabled/unavailable/error UI (TASK-019). POST_NOTIFICATIONS request (TASK-025).

## Requirements
Required:
- All service lifetime rules listed in Context behave as before.
- Dashboard receives fixes while visible even if the service failed to start.
- No process-global mutable object remains for monitoring state; shared state is injected and observable.
- No duplicate `LocationManager` registrations (Activity and service share the repository's single registration).

## Acceptance criteria
- [ ] `BackgroundMonitoringBridge` and dead foreground paths are deleted — verified by: code review (grep)
- [ ] Service rules (first-fix stop in background, preference off stop, waiting copy, eligibility stop) covered by tests — verified by: CI unit test
- [ ] With the service never started, a simulated fix reaches the flight card — verified by: CI unit test (Robolectric)
- [ ] TASK-004 scenarios still pass (adapted only in how data is injected). Scenario 9 ("Activity registers no listener") is intentionally replaced by "exactly one `LocationManager` registration while Activity and service both collect" — verified by: CI unit test
- [ ] Background waiting notification and stop-on-first-fix on a device — verified by: HUMAN on device

## Tests to add or update
- New: `AppVisibility` tests if it has logic; service tests for the rules above using a fake repository.
- Update/delete as listed in Scope.

## Risks and edge cases
- Race: Activity pauses while a fix is in flight → service sees `visible=false` then first fix → stops. Same as today; test it.
- Configuration change (rotation) briefly sets visibility false (onPause → onResume). Today the same happens with the bridge; the service would stop if a usable fix exists. Preserve behavior, but note it in the PR; TASK-016 revisits lifecycle once ViewModels hold state.
- Thread-safety: `StateFlow` removes the unsynchronized `var`s.
