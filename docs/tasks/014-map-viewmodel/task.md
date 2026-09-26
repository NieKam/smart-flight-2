# TASK-014 — Map ViewModel with immutable map UI state; suspend archive preparation; osmdroid config at startup

## Goal
Move the map card's logic state (archive readiness, latest position, marker course, first-fix centering) into a `MapViewModel` emitting an immutable UI state. Replace the executor/callback archive preparation with a `suspend` function. Initialise osmdroid configuration once at app startup. Stop deciding the map button icon from localized text. No user-visible behavior change except the button glyph robustness.

## Context
- Architecture review F10 (VIOLATION, MEDIUM), verified:
  - `MapCardState` (holds a `java.io.File`) is declared in `ui/gnss/MapCard.kt:62-72` but produced by `MainActivity` (`:99,600-612`).
  - `MapSessionRules` (`map/MapState.kt:34-90`) is a mutable non-snapshot object mutated by the Activity (`MainActivity.kt:510,598,611`) and read imperatively by composables (`MapCard.kt:124,253,322-336`); recomposition is forced by the counter `mapPositionVersion` (`MainActivity.kt:134,510,599`) read as a bare expression (`GnssStatusScreen.kt:114`).
  - The button glyph is chosen with `description.startsWith("Expand"/"Collapse")` (`MapCard.kt:187-194`), which breaks under translation (also parity finding 12, last bullet).
- Architecture review F4/F9: `MapArchiveRepository` owns `Executors.newSingleThreadExecutor()` and a callback API (`map/MapArchiveRepository.kt:11,15`); map load cancellation via `mapLoadToken` (`MainActivity.kt:133,595-613`).
- Architecture review F18 (VIOLATION, LOW): `Configuration.getInstance().load(...)` runs inside `AndroidView.factory` in `MapCard.kt:263` and `RoutePicker.kt:162`. Direction: app-startup work in the Application class.
- Architecture review K2 (KEEP): companion zoom rules and `applyMapZoomPolicy` (`map/MapState.kt:71-104`) are pure and tested (`MapZoomPolicyTest`, `MapStateTest`); keep.
- Architecture review K7 (KEEP): atomic, validated archive copy (`MapArchiveCopier`, or the `AssetExtractor` from TASK-011).
- Parity MODERNIZATION (accepted, keep): offline map tiles, zoom 1–6 / 1–9 with "larger map zoom" setting.
- Current behavior to preserve (changed later): marker rotates by GPS bearing, 0° when absent (`MapState.kt:50`) — TASK-030; straight cyan route line — TASK-030; text glyph buttons and expand sizes — TASK-031.
- Parity "Not verified": map panning inside the scrolling page (`verticalScroll` in `GnssStatusScreen.kt:119`) was not tested; the original called `requestDisallowInterceptTouchEvent` (`~/smart-flight/.../base/BaseMapView.kt:80-90`).

## Dependencies
- TASK-008 (location Flow), TASK-006 (display settings: larger zoom), TASK-011 (asset extraction helper).

## Target conventions (from CLAUDE.md)
ViewModel with one `StateFlow<UiState>`; repositories `suspend`; injected dispatchers; package by feature (`map/data`, `map/ui`); composables stateless except view-interop state.

## Scope
- `MapArchiveRepository.prepare(): File` as `suspend` on `@IoDispatcher` (no executor, no `close()`), reusing the shared extractor.
- `MapViewModel`: state `MapUiState` = `Loading | Unavailable | Inactive | Ready(archive, position: MapCoordinate?, markerCourseDegrees: Float, centerRequest: MapCoordinate? /* one-shot first-fix centering, consumed via event */, largerMapZoom: Boolean)`; `retry()`; `onMapOpenFailed()`. Loads the archive when collected; position/course from `LocationRepository.fixes` via the pure `MapCoordinate.from`/normalization; first-fix centering once per observation start (as `MapSessionRules.hasCenteredOnFirstFix` today). Model the one-shot centering as state with an acknowledgement (`onCentered()`), not a `Channel` that may drop events.
- Move `MapCardState` (renamed to `MapUiState`) into `map/ui`; delete `MapSessionRules` instance usage and `mapPositionVersion`; keep its pure companion functions.
- `MapCard` receives `MapUiState` + callbacks; the `update` block reads immutable values.
- Map buttons: pass an explicit icon/kind parameter (`MapButtonKind.Recenter/Expand/Collapse`) instead of parsing the description.
- Application class: `Configuration.getInstance().load(context, getSharedPreferences("osmdroid", 0))` once in `onCreate` (keep the same preferences file name); remove the two calls in composables.
- Route overlay still comes from the route state (TASK-012) as a parameter.
- `RoutePicker` map uses the archive from `MapUiState.Ready` as today.

## Out of scope
- Great-circle line, plane marker, controls, zoom tip (TASK-030/031); picker map centering (TASK-032).

## Requirements
Required:
- Same visible map behavior (default center 32,-32 zoom 3; first fix centers; recenter button uses latest position or default; zoom limits by setting; maximum-zoom caption).
- Button glyphs are correct regardless of locale.
- Map load is cancelled when observation stops (scope cancellation), no token counters.
- osmdroid configuration loaded exactly once per process.

## Acceptance criteria
- [ ] `MapViewModelTest`: loading → ready, failure → unavailable → retry, first fix centering once, marker course from bearing (0 when absent) — verified by: CI unit test
- [ ] `MapArchiveRepository` suspend tests (usable existing archive reused, corrupt archive re-extracted, failure cleans temporary) — verified by: CI unit test
- [ ] Compose test: expand/collapse and recenter buttons show the correct glyph under a non-English locale (`@Config(qualifiers = "pl")`) — verified by: CI unit test
- [ ] `MapStateTest`, `MapZoomPolicyTest` pass — verified by: CI unit test
- [ ] Map pans vertically/horizontally inside the scrolling dashboard without the page stealing the gesture — verified by: HUMAN on device (report result; if broken, fix with `Modifier.pointerInteropFilter`/`requestDisallowInterceptTouchEvent` in this task)

## Tests to add or update
- New: `MapViewModelTest`, `MapArchiveRepositoryTest`, map button Compose test.
- Update: `GnssStatusScreenTest` map cases.

## Risks and edge cases
- `AndroidView.update` runs on every recomposition; keep overlay updates idempotent (as today).
- Application `onCreate` must not do disk I/O beyond osmdroid's small config load.
