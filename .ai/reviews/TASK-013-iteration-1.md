# TASK-013 — Review iteration 1

## Result

CHANGES_REQUESTED

## Task

Reviewed against `.ai/tasks/TASK-013.md` (TASK-013 — Add optional background flight monitoring notification).

## Design

Reviewed against `.ai/designs/TASK-013.md` (TASK-013 Design Specification — Optional background flight-monitoring notification).

## Blocking Findings

### 1. Foreground-service startup is performed only after the activity is backgrounded

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- location: lines 369–380 and 548–551
- problem: `onPause()` stops the foreground controllers and then calls `startForegroundService()`. The service is never started or handed the session while the authorized activity is visible.
- why it violates the task/design: TASK-013 requires starting the location foreground service from the visible authorized activity so Android foreground-service start restrictions are satisfied, then transferring/retaining the monitoring session when the activity backgrounds. Starting only from `onPause()` can be rejected as a background start and leaves the required background monitoring/notification unavailable; it also creates a stop-then-create lifecycle gap rather than the specified handoff.
- required correction: Start or reconcile one authoritative service/session from the visible authorized dashboard, and make the background transition transfer ownership without stopping the session first. Handle startup failure explicitly and preserve idempotence across resume, pause, recreation, and notification tap.

### 2. The service does not reuse or bridge the existing location/GNSS observation path

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/main/java/kniezrec/com/flightinfo/monitoring/LocationForegroundService.kt`
- location: lines 19–21 and 56–71
- problem: The service creates a separate raw `LocationListener`, whose callback only stops the service, and registers an empty `GnssStatus.Callback`. It does not use or bridge `AndroidFlightLocationPlatform`, `FlightParametersController`, `GnssStatusController`, or a testable session adapter. The `BackgroundMonitoringSession` class is not wired into either the activity or service.
- why it violates the task/design: TASK-013 requires one authoritative location/GNSS session, reuse of the existing platform/controller abstractions, and exposure/forwarding of the same `FlightLocationFix`/GNSS information needed by the existing foreground path. The empty GNSS callback means the background session does not actually preserve GNSS observation, while the independent listener creates a second observation pipeline and cannot preserve the existing controller state/session handoff guarantees.
- required correction: Introduce and wire a single authoritative session boundary shared by the activity and service. Forward location/GNSS events through the existing abstractions or an equivalent small adapter, ensure first-fix state and generation guards are shared, and make foreground/background transitions idempotent without parallel listeners.

### 3. Location-services disablement is not handled after service startup

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/main/java/kniezrec/com/flightinfo/monitoring/LocationForegroundService.kt`
- location: lines 49–54 and 56–78
- problem: `isLocationEnabled` is checked only once in `onStartCommand()`. If the user disables device location services while the service is backgrounded, no provider-state callback or subsequent eligibility check stops the service or cancels the waiting notification.
- why it violates the task/design: TASK-013 requires revoked/approximate-only permission or disabled location services to stop background monitoring and produce no waiting notification, including while backgrounded. The current implementation can retain the foreground service and notification after location services are disabled.
- required correction: Observe or otherwise re-check location-service and permission eligibility during the background session, stop/unregister promptly when eligibility is lost, and cancel the stable notification ID.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the TASK-013 Architect task, TASK-013 Designer specification, the feature commit on `feat/TASK-013`, manifest declarations, `MainActivity` lifecycle wiring, Settings UI and resource strings, monitoring service/session/preference sources, existing location controller/platform semantics, and focused monitoring tests. No previous TASK-013 review artifact exists because this is iteration 1.

### Tests Verified

Not executed. `./gradlew testDebugUnitTest ktlintCheck` could not configure because the local Android SDK is unavailable (`sdk.dir`/`ANDROID_HOME` is not configured).

### Build Verified

Not verified. Gradle configuration failed before compilation for the same missing Android SDK location.

### CI Verified

Not verified. No CI run was available in the local environment.

## Recommended Next Action

Developer fixes all BLOCKING_IMPLEMENTATION findings, adds focused lifecycle/service failure-path coverage, and reruns the configured checks in an Android SDK environment. Then the workflow may proceed to another review iteration.
