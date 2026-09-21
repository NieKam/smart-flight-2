# TASK-008 — Review iteration 6

## Result

CHANGES_REQUESTED

## Task

Reviewed against [TASK-008](../tasks/TASK-008.md), “Add offline route planning and map route overlay.”

## Design

Reviewed against [TASK-008](../designs/TASK-008.md), “Offline route card and map overlay design.”

## Blocking Findings

### 1. Picker callbacks are not invalidated when a picker is canceled or superseded

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/main/java/kniezrec/com/flightinfo/route/RouteController.kt:91-123`, `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt:130-208`
- location: `RouteController.search` and `RouteController.nearest` accept callbacks keyed only to the controller session. Opening/canceling/replacing `routePicker` in `MainActivity` does not invalidate an in-flight search or nearest-city request.
- problem: A request started for one picker can complete after the picker is canceled or another endpoint picker is opened, and its callback can overwrite `routeResults`, `routeSearchError`, `routeNearestDraft`, and loading state belonging to the current UI. The same race exists between successive searches because there is no per-request/query generation check.
- why it violates the task/design: TASK-008 requires stale search/restore/overlay callbacks to be ignored after cancellation or endpoint replacement. The design’s picker lifecycle requires cancellation and replacement to leave the current endpoint/draft unchanged and to localize asynchronous work to the active picker.
- required correction: Add a picker/request generation (or equivalent cancellation token) that is advanced on picker open, cancel, endpoint replacement, and each new search/nearest operation; apply callbacks only when the generation and endpoint still match. Clear or ignore callbacks when the picker closes. Add a focused regression test for a canceled/superseded search and nearest callback.

### 2. Required Compose accessibility/action coverage is missing

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/androidTest/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreenTest.kt` and `app/src/main/java/kniezrec/com/flightinfo/ui/route/RouteCard.kt`, `app/src/main/java/kniezrec/com/flightinfo/ui/route/RoutePicker.kt`
- location: The repository has no route-specific Compose/instrumentation test for `RouteCard` or `RoutePicker`; the only TASK-008 tests are `RouteControllerTest` and `RouteModelsTest`.
- problem: The implementation’s required UI behavior is unverified: endpoint role/value semantics, visible edit/clear/confirm/cancel actions, picker back/cancel draft preservation, result selection, error/retry states, and accessible route-map summary have no automated UI coverage.
- why it violates the task/design: TASK-008 acceptance criteria explicitly require focused automated coverage for picker/card semantics and actions, including accessibility/actions. The design’s verification checklist also requires route-card placement, 48 dp actions, 200% text scale, RTL wrapping, and picker keyboard/back behavior.
- required correction: Add focused Compose/instrumentation tests for the route card and picker covering endpoint semantics and actions, explicit confirm/cancel behavior, result selection and error/retry states, back/cancel draft preservation, and the specified accessibility/large-text/RTL behavior. Keep the existing domain/controller tests.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the Architect task, Designer specification, prior review iteration 5, current `feat/TASK-008` implementation, route models/controller/persistence, picker and route card, `MainActivity` lifecycle and shared fix wiring, dashboard placement, map overlay interop, resources, and route-related tests. Confirmed that the prior iteration’s endpoint icons/edit affordance, dashboard map preservation, and invalid-city confirmation changes are present. Reviewed repository status and ran `git diff --check`.

### Tests Verified

No tests were executed successfully. `./gradlew testDebugUnitTest --no-daemon` was started, but remained in Gradle task-graph calculation without reaching test execution and was stopped after bounded polling.

### Build Verified

No successful build was verified. The Gradle invocation did not reach compilation or test execution.

### CI Verified

CI was not run or otherwise verified.

## Recommended Next Action

Developer fixes the two `BLOCKING_IMPLEMENTATION` findings, adds the required stale-callback and route UI coverage, and reruns the configured checks.
