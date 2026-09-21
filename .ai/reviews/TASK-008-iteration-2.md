# TASK-008 — Review iteration 2

## Result

CHANGES_REQUESTED

## Task

Reviewed against the Architect task [.ai/tasks/TASK-008.md](../tasks/TASK-008.md).

## Design

Reviewed against the Designer specification [.ai/designs/TASK-008.md](../designs/TASK-008.md).

## Blocking Findings

### 1. A nearest-city long press does not create the draft selection

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt:168-179`; `app/src/main/java/kniezrec/com/flightinfo/ui/route/RoutePicker.kt:100-121`
- problem: The nearest-city callback only replaces `routeResults` with a one-item list. `RoutePicker` keeps its existing `selected` value and only assigns a draft when the user separately taps the result row. A valid long press therefore does not update the selected-city preview or enable Confirm for a new endpoint. The picker also has no selected-city marker update after the long press.
- why it violates the task/design: TASK-008 requires a valid map long press to resolve the nearest city and allow confirmation. The design explicitly requires the long press to update the draft preview and expose Confirm, while retaining the current endpoint until confirmation.
- required correction: Propagate the resolved nearest city as draft-selection state (without committing it), update the preview/marker, and enable Confirm for that draft. Keep Cancel non-mutating.

### 2. Picker Retry does not retry the failed operation

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt:146-166`
- problem: The search callback does not retain the submitted query, and `onRouteRetry` calls `routeController.search("")`. The repository treats an empty query as an empty result, so pressing Retry after a database/search error does not repeat the failed search and leaves the picker without the requested recovery behavior. The retry callback also does not restore the error/result state from the retry result.
- why it violates the task/design: TASK-008 requires city-database failures to expose a safe retry, and the design says Retry repeats only the failed database operation while preserving the draft/current endpoint.
- required correction: Retain the last search request (or operation parameters), retry that exact request, and apply both success and failure results to picker state while preserving the draft endpoint.

### 3. Restore/read failures are silently converted into missing endpoints

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/main/java/kniezrec/com/flightinfo/route/RouteController.kt:118-142`
- problem: Exceptions from `repository.findById` during saved-route restoration are swallowed by `runCatching { ... }.getOrNull()`. The controller then clears the endpoint and publishes a normal incomplete route with no error or retry state. Thus database open/read failures during restore are indistinguishable from a missing or invalid saved ID and cannot be retried from the route UI.
- why it violates the task/design: The task requires city-database open/read failures to produce a readable picker/card error with retry where applicable, while invalid saved IDs must be discarded safely. The implementation does not distinguish those cases.
- required correction: Preserve the distinction between a missing/invalid record and a read failure; publish a localized route/picker error with a retry path for the latter, without affecting the other dashboard cards.

### 4. Required focused route UI and map coverage remains absent

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/test/java/kniezrec/com/flightinfo/route/RouteModelsTest.kt:14-45`; `app/src/test/java/kniezrec/com/flightinfo/route/RouteControllerTest.kt:14-66`; `app/src/androidTest/`
- problem: The current tests cover a small subset of pure calculations and controller persistence/stop behavior, but there are no focused tests for picker draft confirm/cancel/edit actions, nearest-city long-press selection, retry/error handling, Compose accessibility/actions, or map overlay add/update/remove/restoration without a second map instance. The nearest-city controller test also returns the record with the minimum latitude rather than verifying geographic nearest resolution.
- why it violates the task/design: The acceptance criteria explicitly require focused automated coverage for search disambiguation, nearest-city selection, persistence/invalid IDs, distance/ETA, session invalidation, route clear, Compose accessibility/actions, and map overlay lifecycle. The missing coverage leaves the concrete regressions above undetected.
- required correction: Add the required unit and Compose/instrumentation/map-boundary tests where supported, including tests that verify long-press draft selection and retry semantics, and rerun the configured checks in an Android environment.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the Architect task, Designer specification, iteration 1 review, current feature branch implementation and diff, route controller/models, picker, route card, dashboard composition, MainActivity lifecycle/shared-fix wiring, city repository boundary, map overlay interop, resources, and route tests. Verified that the prior picker-rendering and distinct endpoint-marker findings are addressed in the current source.

### Tests Verified

Attempted `./gradlew testDebugUnitTest --no-daemon`. Tests did not execute because the Android SDK location is not configured (`SDK location not found`; no valid `ANDROID_HOME` or `local.properties` SDK path).

### Build Verified

No successful build was verified. The Gradle test invocation failed during task dependency/configuration resolution because the Android SDK location was unavailable.

### CI Verified

CI was not run or otherwise verified.

## Recommended Next Action

Developer fixes all `BLOCKING_IMPLEMENTATION` findings, adds the required focused coverage, and reruns verification in an Android environment with the SDK configured. Workflow may return for another review iteration.
