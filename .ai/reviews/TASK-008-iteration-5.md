# TASK-008 — Review iteration 5

## Result

CHANGES_REQUESTED

## Task

Reviewed against the Architect task [TASK-008](../tasks/TASK-008.md), “Add offline route planning and map route overlay.”

## Design

Reviewed against the Designer specification [TASK-008](../designs/TASK-008.md), “Offline route card and map overlay design.”

## Blocking Findings

### 1. Route endpoint rows omit the required role icons and explicit edit affordance

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/main/java/kniezrec/com/flightinfo/ui/route/RouteCard.kt:84-108`
- location: `EndpointRow` renders only a text button and, for a populated endpoint, a `Clear` button. It does not render the departure/take-off or destination/landing icon, and it does not expose the resource-backed `Edit` action.
- problem: The incomplete route card does not visibly communicate the two endpoint concepts through the specified icon-plus-label rows. A populated endpoint can technically be tapped to edit, but the UI provides no explicit edit affordance or edit label.
- why it violates the task/design: TASK-008 requires departure and destination actions using take-off/departure and landing/destination concepts, without relying on icon color alone. The design requires a leading role icon and an obvious Edit action for an already selected endpoint.
- required correction: Add the role-specific departure/destination icon and accessible resource-backed content description to each endpoint row. Expose a visible, at-least-48dp `Edit` action for populated endpoints while retaining the per-endpoint Clear action. Add/adjust semantics tests for both affordances.

### 2. Opening the picker removes and later recreates the dashboard MapView

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt:105-120`
- location: When `routePicker != null`, `GnssStatusScreen` returns the picker before composing `MapCard`; after confirmation/cancellation it composes `MapCard` again.
- problem: Entering the picker disposes the existing `MapCard`/`MapView` through its `DisposableEffect`, and returning from the picker creates a new map instance. Selecting an endpoint therefore does not update the existing visible map instance; it tears it down and recreates it, which can disrupt the live marker and viewport.
- why it violates the task/design: TASK-008 explicitly requires route changes to update the existing offline map without recreating it, interrupting the live plane marker, or resetting the user viewport. The design likewise requires overlay changes through the existing map interop boundary.
- required correction: Keep the dashboard map instance alive across picker presentation (for example, present the picker as an overlay/sheet while retaining the dashboard composition), or otherwise preserve and reuse the same `MapView`/map interop instance. Add a UI/map regression test that records map identity and aircraft-marker preservation across picker confirmation and route overlay update.

### 3. Invalid city records can be confirmed and silently close the picker

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt:159-163`, `app/src/main/java/kniezrec/com/flightinfo/route/RouteController.kt:55-69`
- location: `MainActivity` always clears `routePicker` after calling `routeController.choose`; `RouteController.choose` silently returns when `validCity(city)` is false.
- problem: A malformed city returned by search or nearest-city lookup can be displayed as selected, then Confirm closes the picker even though `choose` rejects it. The endpoint remains unchanged (or empty), with no readable picker error or retry path.
- why it violates the task/design: TASK-008 requires malformed city/time-zone data and nearest/search failures to be handled locally with a readable picker/card error, and Confirm must only return a valid selected city. The design requires the existing endpoint to remain unchanged on database/data errors.
- required correction: Validate candidate records before enabling/handling Confirm and surface a localized error while keeping the picker open, or make the choose operation return an explicit success/failure result and close the picker only on success. Add tests for invalid-coordinate and invalid-time-zone records returned by both search and nearest lookup.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the Architect task, Designer specification, previous review iteration 4, current `feat/TASK-008` implementation, route models/controller/persistence, picker and route card, MainActivity lifecycle and shared fix wiring, dashboard placement, map overlay interop, resources, and updated unit/instrumentation tests. Verified that the prior system-back cancellation and resource-backed restore error findings are addressed. The added tests cover several prior gaps, but do not protect map identity/aircraft-marker preservation or invalid-record confirmation.

### Tests Verified

Attempted `./gradlew testDebugUnitTest --no-daemon`. No tests executed: Gradle failed during dependency/task setup because the Android SDK location is unavailable (`local.properties` has no `sdk.dir` and `ANDROID_HOME` is not configured).

### Build Verified

No successful build was verified. The same SDK-location failure prevented compilation.

### CI Verified

CI was not run or otherwise verified.

## Recommended Next Action

Developer fixes the three `BLOCKING_IMPLEMENTATION` findings, adds focused regression coverage for endpoint affordances, map identity/marker preservation, and invalid city confirmation, then reruns the configured checks.
