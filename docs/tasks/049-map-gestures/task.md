# TASK-049 — Map gestures win over the card list scroll

## Goal
Pinch-zoom and drags that start on the dashboard map move the map, not the card list.

## Context
- **Human report (2026-10-08):** "map pinch to zoom gestures doesn't work well. When I do a zoom gesture, cards are moving like scrolling."
- The map is an osmdroid `MapView` in an `AndroidView` inside the dashboard's `verticalScroll` column. The column intercepts vertical movement, so a pinch (fingers moving vertically) and vertical pans scroll the list.
- The original app's `BaseMapView.dispatchTouchEvent` called `parent.requestDisallowInterceptTouchEvent(true)` on `ACTION_DOWN` and `false` on `ACTION_UP`: every gesture starting on the map belonged to the map. The rewrite lost this. Compose's `AndroidView` honors the request (the view receives the events before the scroll container and consumes them).

## Dependencies
- TASK-047 (merged).

## Scope
- `map/ui/GestureOwningMapView`: `MapView` subclass that requests no interception on `ACTION_DOWN` and releases it on `ACTION_UP`/`ACTION_CANCEL`. `MapCard` creates it instead of `MapView`.
- The city picker's map is not in a scrolling container and is unchanged.
- Tests: the requests themselves, and in Compose a drag and a pinch on the map leave a surrounding `verticalScroll` at 0, while a drag outside the map scrolls it.

## Behavior change (restores the original)
- A drag that starts on the map no longer scrolls the card list; the list scrolls from any other card or the gaps between them.

## Acceptance criteria
- [ ] Drag and pinch on the map do not scroll the list; a drag elsewhere does — verified by: CI unit test (`GestureOwningMapViewTest`)
- [ ] Pinch-zoom and pan on the dashboard map work smoothly, also with the expanded map — verified by: HUMAN on device
