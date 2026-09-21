# TASK-005 — Review iteration 1

## Result

CHANGES_REQUESTED

## Task

Reviewed against [TASK-005](../tasks/TASK-005.md).

## Design

Reviewed against [TASK-005 design specification](../designs/TASK-005.md).

## Blocking Findings

1. **classification:** BLOCKING_IMPLEMENTATION
   **file:** `app/src/main/java/kniezrec/com/flightinfo/orientation/OrientationSource.kt`
   **location:** lines 33-34 and 57-79
   **problem:** A callback from an unregistered/replaced Android `SensorEventListener` dispatches to `listeners` when the executor eventually runs, rather than to a listener snapshot and registration generation captured when the sensor event was received. If a prior sensor callback is queued across stop/retry/configuration recreation, it can therefore be delivered to the newly registered Horizon (and Course) callback. The controller session token cannot reject it because the source invokes the new session's listener.
   **why it violates the task/design:** TASK-005 requires stale callbacks from stopped or replaced registrations to be ignored and requires pause, retry, display rotation, and new sessions to clear attitude before a current callback establishes a reference. A stale sample can currently establish the fresh Horizon reference or restore a reading.
   **required correction:** Make the shared source reject callbacks from obsolete registrations and dispatch only the subscription snapshot that belonged to the event (or use a generation token checked both before scheduling and before delivery). Add a focused test that queues an old source callback, replaces the registration, and proves it cannot affect either a new Horizon or Course session.

2. **classification:** BLOCKING_IMPLEMENTATION
   **file:** `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/HorizonCard.kt`
   **location:** lines 59-64 and 68-104
   **problem:** The Horizon UI has no polite live-region announcement at all, and the resource set contains no calibration-entered-waiting or calibration-complete announcement. This means recalibration completion, unavailable, and read-error transitions are silent to TalkBack.
   **why it violates the task/design:** The task and design explicitly require one polite announcement for calibration-entered waiting, calibration completion, unavailable, and error, while routine sensor updates remain silent. The required calibration-complete state cannot be represented with the current `Waiting`/`Available` rendering alone.
   **required correction:** Add resource-backed transition announcements and state/transition tracking so each required transition is announced once through a polite live region, without making initial lifecycle waiting or normal live updates chatty. Cover the behavior with Compose semantics tests.

3. **classification:** BLOCKING_IMPLEMENTATION
   **file:** `app/src/test/java/kniezrec/com/flightinfo/horizon/HorizonControllerTest.kt`
   **location:** lines 7-50
   **problem:** The added tests do not cover the required display-rotation reset/re-reference behavior, a stale callback reaching a replacement active session, or display-relative axis behavior across all four display rotations. The existing stale test only invokes an old controller callback after `stop()`, which does not exercise the shared source race in finding 1.
   **why it violates the task/design:** TASK-005 acceptance criteria explicitly require focused automated coverage for display-rotation reset, stale callback/listener cleanup, shared Course+Horizon delivery, and the display-relative orientation behavior. The submitted tests leave these central lifecycle and rotation requirements unverified.
   **required correction:** Add focused tests for configuration/display-rotation session reset and re-calibration, old-event delivery after a replacement registration, and controlled samples for all four display rotations. Keep the shared Course+Horizon single-listener test and extend it to validate the lifecycle cases.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the TASK-005 implementation commit (`b49ea37`), including `MainActivity`, the shared orientation source/adapters, Horizon state/controller, Horizon Compose UI, strings, the new unit/UI tests, and the existing Course lifecycle/UI integration. Compared the implementation with the Architect task and Designer specification.

### Tests Verified

No tests completed successfully in this environment. Attempted `./gradlew testDebugUnitTest`; Gradle could not configure because no valid Android SDK location is configured (`ANDROID_HOME`/`sdk.dir`). Source-level review of the test files was completed.

### Build Verified

No build was verified. `assembleDebug` was not run after the unit-test task failed during Android SDK configuration. No Android SDK components were installed.

### CI Verified

No CI run was verified. Reviewed `.github/workflows/build.yml`; it defines ktlint, unit-test, and debug-APK build steps.

## Recommended Next Action

Developer fixes the blocking stale-event isolation and accessibility announcements, and adds the required lifecycle/rotation test coverage. Then submit for the next review iteration.
