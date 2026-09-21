# TASK-008 — Review iteration 4

## Result

CHANGES_REQUESTED

## Task

Reviewed against the Architect task [TASK-008](../tasks/TASK-008.md), “Add offline route planning and map route overlay.”

## Design

Reviewed against the Designer specification [TASK-008](../designs/TASK-008.md), “Offline route card and map overlay design.”

## Blocking Findings

### 1. System back does not cancel the city picker

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt:105-122`, `app/src/main/java/kniezrec/com/flightinfo/ui/route/RoutePicker.kt:38-128`
- location: The picker is conditionally rendered as the whole screen, but it has no `BackHandler` or equivalent activity back callback. `onRouteCancel` is only connected to the visible Cancel button.
- problem: Pressing Android system back while selecting an endpoint is not routed to picker cancellation. It can finish the activity instead of dismissing the draft picker, and there is no implementation-level guarantee that the existing endpoint remains unchanged.
- why it violates the task/design: TASK-008 requires back/cancel to dismiss the picker without changing the current endpoint. The design explicitly requires unambiguous Cancel/back semantics for the full-screen picker.
- required correction: Handle system back while the picker is visible by invoking the same cancellation path as the Cancel action, clearing only transient draft/search state and retaining the saved endpoint. Add an instrumentation test for back dismissal and unchanged endpoint state.

### 2. Restore failure text bypasses Android resources

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/main/java/kniezrec/com/flightinfo/route/RouteController.kt:146,200`
- location: `ROUTE_RESTORE_ERROR` is a hard-coded user-visible English sentence passed through `RouteState.error` and rendered by `RouteCard`.
- problem: A visible route error is defined in Kotlin rather than in `res/values/strings.xml`, unlike the other route messages.
- why it violates the task/design: The technical requirements require all visible strings, labels, errors, picker actions, and accessibility text to use Android resources. The design also requires localized route/card database failure messaging.
- required correction: Add a resource string for the restore failure and pass a resource-backed/localized value through the UI boundary (or model an error type and resolve it in Compose). Ensure the retry message remains localized.

### 3. Required route/map regression coverage remains incomplete

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/androidTest/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreenTest.kt:264-348`; `app/src/test/java/kniezrec/com/flightinfo/route/RouteControllerTest.kt`; `app/src/test/java/kniezrec/com/flightinfo/route/RouteModelsTest.kt`
- location: The added tests cover result selection, a retry callback, route-card semantics, basic overlay add/clear, timestamp ordering, persistence, and core calculations, but do not verify picker system-back/IME behavior, RTL/wrapping at the required configuration, invalid-coordinate nearest lookup, or map overlay update/restoration and preservation of the same map/aircraft marker instance.
- problem: Several behaviors required by the acceptance checklist are still unprotected by automated regression tests. The map test only checks content descriptions before and after clearing; it does not exercise route replacement or map readiness restoration, and it cannot detect recreation or aircraft-marker disruption.
- why it violates the task/design: TASK-008 explicitly requires focused tests for keyboard/back behavior, RTL and 200% accessibility behavior, invalid long-press coordinates, and map add/update/remove/restoration without a second map instance where supported. The design verification checklist repeats these cases.
- required correction: Add focused tests for picker back and IME search, RTL/large-text wrapping, invalid nearest input/error handling, and route overlay update/removal/restoration while asserting map identity and live-marker preservation where the Android test environment supports it. Run the configured checks after adding them.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the Architect task, Designer specification, prior review iteration 3, current `feat/TASK-008` implementation, route controller/models, persistence and city repository boundary, picker and route card, dashboard placement, shared location-fix wiring, map overlay interop, resources, and route unit/instrumentation tests. Verified that the prior out-of-order-fix issue is addressed by timestamp rejection and that the new tests cover several previously identified gaps.

### Tests Verified

`./gradlew testDebugUnitTest --no-daemon` was started, but Gradle remained in task-graph calculation without executing tests during the local verification window; it was interrupted. No test result was verified.

### Build Verified

No successful build was verified.

### CI Verified

CI was not run or otherwise verified.

## Recommended Next Action

Developer fixes the three `BLOCKING_IMPLEMENTATION` findings, adds the missing focused coverage, and reruns the configured checks. Workflow may return for another review iteration.
