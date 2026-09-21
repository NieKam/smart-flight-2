# TASK-004 — Review iteration 2

## Result

CHANGES_REQUESTED

## Task

Reviewed against [TASK-004](../tasks/TASK-004.md).

## Design

Reviewed against the [TASK-004 design specification](../designs/TASK-004.md).

## Blocking Findings

1. **classification:** BLOCKING_IMPLEMENTATION  
   **file:** `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/CourseCard.kt`  
   **location:** `CourseCard`, line 51  
   **problem:** The composable accepts a `modifier` but constructs the `Card` with a new `Modifier` instead. Consequently the caller's `widthIn(max = 600.dp)` and `padding(bottom = 12.dp)` are discarded.  
   **why it violates the task/design:** The task requires the centered dashboard column/card to remain capped at 600 dp and have the established 12 dp margins/gaps. On expanded windows the Course card expands past the specified maximum width, and it also loses its required bottom page margin.  
   **required correction:** Apply the supplied modifier when constructing the `Card` (then add the card's own fill/height modifiers), and add focused UI coverage for the intended sizing/spacing integration if practical.

2. **classification:** BLOCKING_IMPLEMENTATION  
   **file:** `app/src/main/java/kniezrec/com/flightinfo/course/CourseController.kt`  
   **location:** `start`/`stop` and `onHeading`, lines 16–36 and 48–50  
   **problem:** `onHeading` accepts every callback held by the platform, even after `stop()` has cleared the session and unregistered the listener. A sensor callback already queued on the main executor can therefore repopulate `Available` with an old-session heading after pause, permission loss, retry, or another restart.  
   **why it violates the task/design:** TASK-004 requires every teardown/restart condition to clear heading and bearing before new current-session callbacks, with no stale course visible through waiting/onboarding/error states. The current controller has no registration/session guard to reject callbacks from a stopped or superseded listener.  
   **required correction:** Gate orientation callbacks by the active registration/session (for example, capture and validate a monotonically changing session token and/or active-registration state), invalidate it before teardown, and add a test that delivers a late callback after stop/retry and verifies it cannot restore a heading.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected TASK-004, its design specification, the prior iteration review, the current course state/controller/orientation platform, shared foreground location-bearing integration, activity lifecycle coordination, dashboard/card composition, resource strings, and focused unit and Compose instrumentation tests. Confirmed that the prior iteration's direction visual, resource-backed degree formatting, retry semantics/focus treatment, and substantially expanded focused tests are now present.

### Tests Verified

Attempted `./gradlew testDebugUnitTest --console=plain --no-daemon`. It could not start in this workspace because the sandbox reported `bwrap: loopback: Failed RTM_NEWADDR: Operation not permitted`; no test result was verified. Instrumentation tests were not run.

### Build Verified

No build was completed or verified because the available Gradle invocation could not start in the sandbox.

### CI Verified

Not verified.

## Recommended Next Action

Developer fixes the two BLOCKING_IMPLEMENTATION findings, including regression coverage for a late orientation callback, then reruns the relevant unit and Compose checks where the environment permits.
