# TASK-008 — Review iteration 3

## Result

CHANGES_REQUESTED

## Task

Reviewed against the Architect task [TASK-008](../tasks/TASK-008.md), “Add offline route planning and map route overlay.”

## Design

Reviewed against the Designer specification [TASK-008](../designs/TASK-008.md), “Offline route card and map overlay design.”

## Blocking Findings

### 1. Route state accepts out-of-order fixes instead of only the newest fix

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/main/java/kniezrec/com/flightinfo/route/RouteController.kt:40-45`
- location: `RouteController.onFix`; the fix timestamp is available as `FlightLocationFix.elapsedRealtimeNanos` in `app/src/main/java/kniezrec/com/flightinfo/flight/FlightParametersState.kt:15-22`.
- problem: Every valid-coordinate callback overwrites `fix` without comparing its `elapsedRealtimeNanos` to the latest accepted fix. A delayed callback from the same active session can therefore replace a newer position/speed and publish stale remaining-distance and ETA values.
- why it violates the task/design: TASK-008 requires consuming only the newest valid current-session fix and explicitly says stale callbacks must not overwrite current route state. The design repeats that out-of-order/older fixes must not update live details.
- required correction: Track the timestamp of the accepted fix for the active session, reject valid-coordinate fixes whose timestamp is not newer, and reset that freshness state on session start/stop. Add a regression test that delivers a newer fix followed by an older valid fix and verifies that distance/ETA remain derived from the newer fix.

### 2. Required focused route/map automated coverage is still incomplete

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/test/java/kniezrec/com/flightinfo/route/RouteModelsTest.kt:14-50`; `app/src/test/java/kniezrec/com/flightinfo/route/RouteControllerTest.kt:14-95`; `app/src/androidTest/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreenTest.kt:224-259`
- location: The current route tests and the two picker tests cover only a subset of the acceptance checklist; there are no focused tests for search retry/error state, multiple-result disambiguation in the picker, invalid-coordinate/nearest lookup behavior, endpoint edit/clear semantics, route-card accessibility at large text/RTL, or map overlay add/update/remove/restoration without recreating the map.
- problem: The implementation adds route behavior across the controller, picker, card, and osmdroid boundary, but the repository contains no automated regression coverage for several explicitly required behaviors. The existing “multiple results” model test only filters a list and does not exercise the picker’s explicit selection behavior; the map implementation has no corresponding overlay lifecycle test.
- why it violates the task/design: TASK-008 acceptance criteria require focused automated coverage for search normalization/result disambiguation, nearest-city long-press resolution, persistence/invalid IDs, distance/ETA, session invalidation, route clear, Compose accessibility/actions, and map overlay add/update/remove/restoration where supported. The design’s verification checklist makes the same coverage expectations explicit.
- required correction: Add focused unit/UI/map-boundary tests for the missing behaviors, including exact retry parameters and error recovery, explicit multi-result selection, edit/cancel/clear actions, accessibility/48 dp and wrapping behavior where the test environment supports it, and overlay identity/preservation of the live marker across add/update/remove and map readiness. Run the configured checks in an Android environment.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the Architect task, Designer specification, previous review iteration 2, current `feat/TASK-008` implementation, latest developer commit, route models/controller, picker, route card, dashboard placement, shared location-fix wiring, city repository, map overlay interop, resources, and route-related unit/instrumentation tests. Verified that the previous long-press draft, search retry, and restore-error findings are addressed in the current source.

### Tests Verified

No tests were successfully executed. `./gradlew testDebugUnitTest --no-daemon` was attempted with a bounded timeout; Gradle remained in task-graph calculation and exited with timeout status 124 before test execution completed.

### Build Verified

No successful build was verified.

### CI Verified

CI was not run or otherwise verified.

## Recommended Next Action

Developer fixes both `BLOCKING_IMPLEMENTATION` findings, adds the required focused route/map coverage, and reruns the configured checks in an Android environment. Workflow may return for another review iteration.
