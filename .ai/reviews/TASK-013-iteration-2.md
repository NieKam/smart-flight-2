# TASK-013 — Review iteration 2

## Result

CHANGES_REQUESTED

## Task

Reviewed against `.ai/tasks/TASK-013.md` (TASK-013 — Add optional background flight monitoring notification).

## Design

Reviewed against `.ai/designs/TASK-013.md` (TASK-013 Design Specification — Optional background flight-monitoring notification).

## Blocking Findings

### 1. The foreground service is not the authoritative background location/GNSS session

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/main/java/kniezrec/com/flightinfo/monitoring/LocationForegroundService.kt`
- location: lines 22–23, 78–94; `app/src/main/java/kniezrec/com/flightinfo/monitoring/BackgroundMonitoringBridge.kt` lines 5–41
- problem: `LocationForegroundService` registers only a provider-state receiver and never registers location or GNSS callbacks, nor forwards `FlightLocationFix`/GNSS events. `BackgroundMonitoringBridge` stores only visibility and a boolean fix flag. The actual location callback remains owned by `MainActivity`/`FlightParametersController`; `onPause()` leaves that activity callback alive through `stopForegroundOnly()`, but the service cannot preserve or recreate it as the background session owner.
- why it violates the task/design: TASK-013 requires one authoritative location/GNSS session that is bridged through the service/session boundary, reuses the existing platform/controller abstractions, and remains alive when the dashboard is backgrounded. The design explicitly prohibits an activity-only callback handoff. This implementation therefore does not provide the required service-backed monitoring session and cannot maintain the session independently of the activity callback owner.
- required correction: Move or bridge the existing `AndroidFlightLocationPlatform` and GNSS registration through a single testable session boundary shared by the activity and service. Forward the same location/GNSS events needed by the existing controllers, with idempotent registration/unregistration and a generation guard across foreground/background transitions, recreation, and service teardown.

### 2. Required service/session lifecycle behavior is not covered by focused tests

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/test/java/kniezrec/com/flightinfo/monitoring/BackgroundMonitoringTest.kt`
- location: lines 8–42
- problem: The only focused tests cover preference defaults/round-trip and a small standalone state machine. There are no tests for the implemented service/bridge lifecycle, location/GNSS callback handoff, visible-to-background transition, foreground return/tap, notification dismissal, first-fix cancellation, setting changes stopping an active service, permission/location-service failure, notification-permission degradation, or duplicate prevention.
- why it violates the task/design: The TASK-013 acceptance criteria explicitly require focused coverage for session start/stop and duplicate prevention, notification eligibility, fix/dismissal/foreground transitions, and permission/service failure paths. These are the core behaviors of this feature, and the existing tests would pass even if the service never registered or forwarded a monitoring callback, as is currently the case.
- required correction: Add focused unit/fake-platform tests for the shared session and bridge, plus service/instrumentation or equivalent platform-facing tests where supported, covering the required lifecycle, eligibility, notification, dismissal, fix, and failure paths. Ensure the tests assert that only one location/GNSS registration exists and that events reach the existing state path.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the Architect task, Designer specification, iteration-1 review, current `feat/TASK-013` implementation, the developer fix commit, manifest, resources, Settings UI, `MainActivity` lifecycle, `FlightParametersController`, `AndroidFlightLocationPlatform`, `GnssStatusController`, `ForegroundCourseObservationCoordinator`, monitoring service/bridge/session/preferences, and focused tests. The source review verified that the service starts from `onResume()` but does not own or bridge location/GNSS callbacks.

### Tests Verified

Attempted `./gradlew testDebugUnitTest ktlintCheck`. It did not execute because Gradle could not locate an Android SDK (`ANDROID_HOME`/`sdk.dir` is not configured).

### Build Verified

Not verified. Gradle configuration failed before compilation for the missing Android SDK location.

### CI Verified

Not verified. No CI run was available locally.

## Recommended Next Action

Developer fixes both BLOCKING_IMPLEMENTATION findings by implementing and wiring the authoritative service/session callback boundary, then adds the required focused lifecycle/failure tests and reruns the configured checks in an Android SDK environment.
