# TASK-004 — Review iteration 5

## Result

CHANGES_REQUESTED

## Task

Reviewed against [TASK-004](../tasks/TASK-004.md).

## Design

Reviewed against the [TASK-004 design specification](../designs/TASK-004.md).

## Blocking Findings

1. **classification:** BLOCKING_IMPLEMENTATION  
   **file:** `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`  
   **location:** `startObservation`, lines 196–204; `flightParametersController` registration-failure callback, line 184  
   **problem:** When foreground GPS-location registration fails, `FlightParametersController.start()` invokes `onRegistrationFailed`, which calls `gnssStatusController.showError()` and `courseController.stop()`. Control then returns to `startObservation()`, which unconditionally calls `courseController.start()` in the branch selected before that failure. This re-registers the compass sensor after the GNSS/location error and permits a new compass heading to populate the Course card.  
   **why it violates the task/design:** TASK-004 requires any observation-registration failure to clear compass and GPS-bearing values and stop/unregister added callbacks. It also requires course/bearing state to be reset with no active observation when the shared foreground location path is unavailable/error. Continuing with an active compass after the required GNSS error violates those lifecycle and error-state guarantees.  
   **required correction:** Make location-registration failure terminal for the current observation attempt: do not start (or immediately stop) `CourseController` after the failure, and ensure the Course state remains cleared until a foreground retry/restart succeeds. Add focused coverage for this activity/controller coordination or an equivalently testable orchestration boundary.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected TASK-004, its design specification, iteration-4 review, current branch history/diff, `MainActivity` lifecycle and permission coordination, GNSS/shared-location/course controllers and Android platform adapters, Course/dashboard Compose UI, resources, and focused unit/instrumentation tests. Confirmed the iteration-4 bearing-before-heading fix retains and clears the pending current-session bearing correctly, with regression tests. Identified the location-registration-failure coordination defect above.

### Tests Verified

Executed `./gradlew testDebugUnitTest --console=plain --no-daemon`; it completed with exit code 0. Instrumentation tests were not executed because no Android device/emulator verification was available in this environment.

### Build Verified

No separate assemble/build task was executed. The debug unit-test task completed successfully as above.

### CI Verified

Not verified.

## Recommended Next Action

Developer fixes the location-registration-failure lifecycle coordination and adds focused regression coverage, then reruns the relevant unit and instrumentation checks where available.
