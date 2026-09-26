# TASK-013 — Route picker state holder, typed errors, and the lost nearest-city draft

## Goal
Give the city picker its own state holder (ViewModel) with typed errors and saveable input, move validation out of the composable, and fix the bug where the nearest-city draft from a map long-press never reaches the picker.

## Context
- Architecture review F11 (VIOLATION, MEDIUM), verified: `GnssStatusScreen` has a `routeNearestDraft` parameter (`ui/gnss/GnssStatusScreen.kt:109`) but the call in `MainActivity.kt:207-326` never passes it, although the Activity maintains it (`:105,260,302`). Architecture "Not verified" 5: possibly masked because `routeResults = listOf(city)` (`:303`) still lists the city. Result: after a long-press on the picker map, the found city is listed but not selected (`RoutePicker.kt:73` never fires).
- Architecture review F10: `routeSearchError` is a pre-resolved string (`getString` at `MainActivity.kt:253,280,296,300,306`); route-picker validation and selection logic lives in the composable (`ui/route/RoutePicker.kt:69-74,140-141`). Direction: typed error states resolved to strings in composables.
- Architecture review F1: picker state (`routePicker`, `routeResults`, `routeSearchError`, `lastRouteSearchQuery`, the query inside `RoutePicker.kt:68-71`) is lost on rotation. Parity finding 25 lists "an in-progress city search and selection" among state lost on rotation.
- Parity MODERNIZATION (accepted, keep): "No city found" and "multiple results" are shown inline instead of in a dialog.

## Dependencies
- TASK-012.

## Target conventions (from CLAUDE.md)
ViewModel with one `StateFlow<UiState>`; transient input in `SavedStateHandle` so it survives process death and rotation; typed UI errors resolved in composables; composables stay stateless (K4).

## Scope
- `RoutePickerViewModel` (or a picker section of `RouteViewModel`; pick the smaller design and justify) with state: `endpoint: RouteEndpoint?` (open/closed), `query`, `results`, `loading`, `error: RoutePickerError?` (`SearchFailed`, `NoCityAtLocation`, `InvalidCity`), `selected: NearbyCityRecord?` (initialised from the current endpoint), and actions `open(endpoint)`, `close()`, `search(query)`, `retry()`, `nearest(coordinate)`, `select(city)`, `confirm(): Boolean` (writes through `RouteRepository`/`RouteViewModel.choose`).
- `nearest(coordinate)` success sets `selected` to the found city and `results = listOf(city)` (fixes the draft bug); invalid city → `InvalidCity`; null → `NoCityAtLocation`.
- `endpoint`, `query` and selected city id stored in `SavedStateHandle`.
- `RoutePicker` composable takes the UI state + event lambdas only; maps `RoutePickerError` to string resources (`route_error`, `route_no_city_at_location`, `route_invalid_city`); no `validCity` calls in the composable.
- `MainActivity`: remove all picker fields and lambdas (`:101-105,131,224-311`).

## Out of scope
- Picker map centering and size, auto-select single result, visual changes (TASK-032).

## Requirements
Required:
- Long-press on the picker map selects the nearest city (Confirm enabled) — behavior change (bug fix), list it in the PR description.
- Picker open state, query and selection survive rotation.
- All other picker behavior unchanged.

## Acceptance criteria
- [ ] ViewModel tests: search success/failure/retry, nearest found/none/invalid, select/confirm, stale result ignored after close — verified by: CI unit test
- [ ] Nearest draft becomes the selection — verified by: CI unit test (ViewModel) + Compose test (`RoutePicker` shows "selected" text and Confirm enabled)
- [ ] State survives `ActivityScenario.recreate()` — verified by: CI unit test (Robolectric)
- [ ] No `getString` for picker errors in the Activity — verified by: code review

## Tests to add or update
- New: `RoutePickerViewModelTest`; Compose test for `RoutePicker` error mapping and selection.
- Update: route-picker cases in `GnssStatusScreenTest`.

## Risks and edge cases
- A search result arriving after the picker is closed or reopened for the other endpoint must be ignored (`collectLatest`/job cancellation).
- SavedStateHandle size: store IDs and the query, not full result lists.
