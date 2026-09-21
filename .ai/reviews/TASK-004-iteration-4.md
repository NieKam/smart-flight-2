# TASK-004 — Review iteration 4

## Result

CHANGES_REQUESTED

## Task

Reviewed against [TASK-004](../tasks/TASK-004.md).

## Design

Reviewed against the [TASK-004 design specification](../designs/TASK-004.md).

## Blocking Findings

1. **classification:** BLOCKING_IMPLEMENTATION  
   **file:** `app/src/main/java/kniezrec/com/flightinfo/course/CourseController.kt`  
   **location:** `onGpsBearing`, lines 51–54; `onHeading`, line 59  
   **problem:** `onGpsBearing` returns unless a compass heading has already made the state `Available`. A valid bearing delivered by the current foreground location session before its first orientation callback is therefore discarded. When the heading subsequently arrives, the card displays `—` instead of that current-session GPS bearing. The focused controller test exercises only the reverse callback order (heading, then bearing).  
   **why it violates the task/design:** TASK-004 defines compass heading and the optional GPS bearing as independent current-session observations: a bearing-equipped GPS fix updates the secondary bearing value, while the card remains waiting until an orientation callback. Callback ordering is not constrained, so a valid current-session bearing must be retained for presentation once the compass becomes available; only a subsequent current no-bearing fix may clear it.  
   **required correction:** Track the normalized current-session GPS bearing independently while compass state is waiting, clear it on every reset/retry/error/unavailable transition, and combine it with the first valid heading. Add regression coverage for bearing-before-heading and for clearing that pending bearing with a current no-bearing fix before the heading arrives.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected TASK-004, its design specification, iteration-3 review, the full branch history and current diff from `main`, activity lifecycle coordination, GNSS and shared foreground location paths, course controller/state/platform, Course and dashboard composables, resources, manifest/dependencies, and focused unit/instrumentation tests. Confirmed iteration-3's requested session guard, responsive layout, polite state announcement, and resource-backed cardinal changes are present. Identified the remaining valid-bearing-before-heading ordering defect above.

### Tests Verified

Executed `./gradlew testDebugUnitTest --console=plain --no-daemon`; the command completed with exit code 0. Instrumentation tests were not executed because no Android device/emulator verification was available in this environment.

### Build Verified

No separate assemble/build task was executed. The debug unit-test task completed successfully as above.

### CI Verified

Not verified.

## Recommended Next Action

Developer fixes the GPS-bearing callback-order defect and adds the focused regression coverage, then reruns the relevant unit and instrumentation checks where available.
