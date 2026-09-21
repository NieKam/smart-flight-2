# TASK-008 — Review iteration 1

## Result

CHANGES_REQUESTED

## Task

Reviewed against the Architect task [`.ai/tasks/TASK-008.md`](../tasks/TASK-008.md).

## Design

Reviewed against the Designer specification [`.ai/designs/TASK-008.md`](../designs/TASK-008.md).

## Blocking Findings

### 1. Picker state is never rendered

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt`
- location: lines 97–133
- problem: `GnssStatusScreen` receives `routePicker`, search results, loading/error state, and all picker callbacks, but only composes `RouteCard` and `MapCard`; it never composes `RoutePicker` or otherwise presents picker UI. Tapping either endpoint only changes `MainActivity.routePicker`, leaving the user on the dashboard with no way to search, select, confirm, or cancel a city.
- why it violates the task/design: TASK-008 requires independently selectable endpoints, explicit draft selection, confirmation/cancellation, search states, and an editing flow. The design requires the full-screen picker structure and its endpoint-specific behavior.
- required correction: Render the picker when an endpoint is active (or provide an equivalent complete navigation/sheet flow), wire its search/result/loading/error/retry/confirm/cancel state, and ensure cancel/back leaves the saved endpoint unchanged.

### 2. The picker has no offline map or long-press nearest-city flow

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/main/java/kniezrec/com/flightinfo/ui/route/RoutePicker.kt`
- location: lines 17–49
- problem: The picker implementation contains only text, search controls, a result list, and confirm/cancel buttons. It has no map preview, no osmdroid offline configuration, no long-press gesture, and no callback to `RouteController.nearest`.
- why it violates the task/design: A valid map long press is an explicit functional requirement and acceptance criterion. The design requires the picker map, long-press affordance, nearest-city resolution from the same bundled data, invalid-coordinate feedback, and no live aircraft tracking.
- required correction: Add the existing offline map boundary to the picker without creating a second foreground location session, handle valid/invalid long presses, resolve nearest cities asynchronously through the repository, and keep the result as draft state until Confirm.

### 3. Endpoint route markers are not implemented as distinct, accessible endpoint markers

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/MapCard.kt`
- location: lines 287–295
- problem: Departure and destination markers are both created as bare `Marker` instances with no icons, anchors, or other role-specific visual configuration. Their only labels are hard-coded `"Departure"` and `"Destination"` titles, and the map summary exposes raw coordinate strings rather than the selected city names.
- why it violates the task/design: TASK-008 requires visually distinguishable departure/destination endpoint markers, distinct from the aircraft marker, plus an accessible route description such as “Route from A to B”. The design explicitly says color alone is insufficient and requires the companion text summary to identify the route.
- required correction: Configure role-specific endpoint marker visuals/content descriptions (using resources/assets as appropriate), preserve the live aircraft marker distinction, and feed the selected endpoint names into the accessible route summary. Keep overlay updates in-place without resetting the viewport.

### 4. Required route/lifecycle/UI test coverage is absent

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/test/java/kniezrec/com/flightinfo/route/RouteModelsTest.kt`
- location: lines 11–36
- problem: The only new tests cover query normalization, identical-endpoint distance, invalid speed, one positive-speed calculation, and invalid overlay coordinates. There are no tests for repository result disambiguation, nearest-city long press, draft confirm/cancel/edit/clear behavior, persistence and invalid IDs, session/stale-fix invalidation, clear-route overlay behavior, Compose accessibility/actions, or map overlay add/update/remove/restoration.
- why it violates the task/design: The Architect acceptance criteria and implementation plan explicitly require focused automated coverage for those behaviors, including lifecycle invalidation and picker/card/map UI behavior. The current test set cannot detect the missing picker or the route integration failures above.
- required correction: Add focused unit, Compose/instrumentation, and map-boundary tests where supported for the listed acceptance behaviors, including at minimum picker presentation/actions, persistence, controller session invalidation, and overlay lifecycle.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the TASK-008 Architect task, Designer specification, current feature branch diff, route models/controller, picker and route card, dashboard composition, `MainActivity` lifecycle/shared-fix wiring, city repository, map overlay interop, resources, and new tests. No previous TASK-008 review artifact exists for this iteration.

### Tests Verified

Attempted `./gradlew testDebugUnitTest --no-daemon`. Tests did not execute because Gradle could not locate an Android SDK (`SDK location not found`; no `ANDROID_HOME` or `local.properties` SDK path is available). No test result is claimed.

### Build Verified

No successful build was verified. The Gradle invocation failed during task dependency/configuration resolution for the same missing Android SDK location.

### CI Verified

CI was not run or otherwise verified.

## Recommended Next Action

Developer fixes all `BLOCKING_IMPLEMENTATION` findings, adds the required focused coverage, and reruns verification in an Android environment with the SDK configured. Workflow should return for another review iteration afterward.
