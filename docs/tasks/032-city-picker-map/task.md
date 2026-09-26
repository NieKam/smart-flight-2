# TASK-032 — City picker: large map that centers on the chosen city; single result auto-selected

## Goal
Make the city picker match the original: search field + Search button, the result, a large map that animates to the selected city with a purple pin (haptic feedback on selection), and a full-width Confirm button. Auto-select a single search result.

## Context
- Parity finding 10 (REGRESSION, MEDIUM): original `~/smart-flight/app/src/main/java/kniezrec/com/flightinfo/cards/route/FindCityActivity.kt:204-226` calls `mMapController.animateTo(geoPoint)` with haptic feedback when a city is chosen; the map fills most of the screen (`layout/activity_find_city.xml:13-52`, `promo/screen-3.png`). Rewrite: `RoutePicker.kt:190-204` only adds/moves a marker; camera stays at (0,0) zoom 3 (`:169-170`); the map is the last `LazyColumn` item with min height 180dp (`:116-132`).
- Parity MODERNIZATION note: "Unlike the original (`FindCityPresenter.kt:154`), a single search result is no longer auto-selected." Planner decision: restore auto-selection (original is the reference, and it drives the map-centering confirmation cue). Multiple results: inline list (accepted modernization), no dialog.
- After TASK-013, `RoutePickerViewModel` holds query, results, selection and typed errors; after TASK-018 the picker uses the brand theme.
- Screenshot `promo/screen-3.png`: search field with cyan underline + "SEARCH" button, result "Mountain View, United States", large map with purple pin, full-width "CONFIRM".

## Dependencies
- TASK-013, TASK-018.

## Original app reference
- `cards/route/FindCityActivity.kt`, `FindCityPresenter.kt`, `DisambiguationAdapter.kt`, `layout/activity_find_city.xml`, `layout/disambiguation_item.xml`, `res/drawable/ic_city_found_marker.xml`, `promo/screen-3.png`.

## Scope
- Layout: `Column` (not `LazyColumn` with the map inside): row with text field + Search button; result/selection text; results list (bounded height, scrollable) when > 1 result; map with `weight(1f)` taking remaining height (min ~240dp); full-width Confirm; Cancel via back/top bar.
- Map: on selection change, animate camera to the city (`controller.animateTo`) at a zoom showing the region (original used the current zoom; choose zoom 5 if at world zoom) and perform haptic feedback (`LocalHapticFeedback`). Marker: `ic_city_found_marker` tinted `page` #484685 (the original tinted it `purple_dark` via `setCustomMarker`). All picker colors come from the TASK-018 palette tokens (page background, `valueText` input and result text, `accent` underline, `card` buttons).
- `RoutePickerViewModel.search`: exactly one result → select it.
- Long-press on map (nearest city) keeps working (TASK-013) and also centers.

## Out of scope
- Route card visuals (TASK-033).

## Requirements
Required:
- Selected city is always visible on the map after selection; single result auto-selected; map is the dominant element.
- Behavior changes listed in PR.

## Acceptance criteria
- [ ] ViewModel test: single result auto-selected; multiple results not — verified by: CI unit test
- [ ] Pure/unit test for the camera target/zoom choice — verified by: CI unit test
- [ ] Compose test: map node height ≥ 50% of the picker on a phone-size window — verified by: CI unit test
- [ ] Search "mountain view" → map animates to the pin; matches `promo/screen-3.png` — verified by: HUMAN on device

## Tests to add or update
- `RoutePickerViewModelTest`, `RoutePickerTest`.

## Risks and edge cases
- Keyboard (`adjustResize`) shrinks the map; acceptable.
- Map gestures inside a scrolling container are gone (no LazyColumn) — improves panning.
