# TASK-016 — Dashboard screen composition, slim MainActivity, state survives rotation

## Goal
Replace the 38-parameter `GnssStatusScreen` with a dashboard composed of per-card containers that obtain their own ViewModels, make the route picker and overlays independent of the dashboard parameter list, reduce `MainActivity` to Android-bound concerns, and finish the package-by-feature layout. After this task the whole app follows the target architecture.

## Context
- Architecture review F11 (VIOLATION, MEDIUM): `GnssStatusScreen` takes 38 parameters, most defaulted (`ui/gnss/GnssStatusScreen.kt:73-113`), which hid a wiring bug (fixed in TASK-013); `RoutePicker` is embedded inside the dashboard composable (`:170-187`). Direction: screen-level composable takes one UiState + event sink or per-card state from feature ViewModels; route picker is its own overlay with its own state holder; keep cards stateless (K4).
- Architecture review F2 (VIOLATION, HIGH): `MainActivity` is composition root, event router and logic host. Direction: the Activity keeps only `setContent`, the permission launcher, window flags/orientation, starting the service.
- Architecture review F1: overlay flags (`showUnitSettings`, `showAbout`) and other transient UI flags must survive configuration changes (`rememberSaveable` or `SavedStateHandle`).
- Parity finding 25 (REGRESSION, LOW): rotating the screen (portrait lock off) resets dashboard state; no `configChanges` in the manifest (`AndroidManifest.xml:25-30`); `onConfigurationChanged` (`MainActivity.kt:380-385`) is dead code (deleted in TASK-010). With ViewModels (TASK-009..015) state survives; this task verifies it end-to-end and covers the remaining UI-only state (map expanded flag, overlay open flags, picker open).
- Architecture review K5 (KEEP): package by feature; add layer sub-packages inside features, do not reorganise by layer.
- Architecture review K4 (KEEP): stateless cards.
- Planner decision: no navigation library. Settings, About and the route picker remain overlays/dialogs (as today), with saveable open state. Reason: one screen plus three overlays does not justify a navigation graph; "prefer fewer, clearer types".

## Dependencies
- TASK-009, TASK-010, TASK-011, TASK-013, TASK-014, TASK-015.

## Target conventions (from CLAUDE.md)
UI (Compose + ViewModel) → data; `hiltViewModel()` at container level; `collectAsStateWithLifecycle()`; stateless cards; package by feature with layers.

## Scope
- `DashboardScreen` (in `dashboard/ui/` or `ui/dashboard/`): header + scrolling list of card containers. Each container (`GnssStatusCardContainer`, `FlightParametersCardContainer`, …) calls `hiltViewModel()`, collects state, and renders the existing stateless card. Unit preferences come from a small shared source (e.g. `UnitSettingsRepository` via a `DashboardViewModel` or each card VM combining units — choose one and justify).
- Route picker overlay hosted at screen level with `RoutePickerViewModel` (TASK-013).
- Settings/About overlay open state in `rememberSaveable` (or `SavedStateHandle` in a small `DashboardViewModel`).
- Map card `expanded` flag becomes `rememberSaveable`.
- `MainActivity`: `setContent { SmartFlightTheme { AppRoot(...) } }`, permission launcher + refresh on resume, window effects from display settings, service start/stop, external intents, `AppVisibility` updates. Target: under ~150 lines.
- Move remaining files into feature packages with layers (e.g. `ui/gnss/CourseCard.kt` → `course/ui/CourseCard.kt`, `ui/route/*` → `route/ui/*`), keeping `ui/theme` as the shared design package. Move tests alongside.
- Delete `GnssStatusScreen` (or reduce it to the GNSS card itself, renamed `GnssStatusCard`).

## Out of scope
- Visual changes (TASK-017+), permission-less cards (TASK-024), card order (TASK-034).

## Requirements
Required:
- No behavior change except: state now survives rotation (parity 25 restored — list in the PR).
- No composable with more than ~12 parameters in the dashboard path.
- `MainActivity` contains no business logic (no fan-out, no controller construction, no `getString` for state).

## Acceptance criteria
- [ ] Rotation (`ActivityScenario.recreate()`) keeps: flight readings, nearby city, route, horizon calibration reference (as long as collection does not restart beyond the WhileSubscribed window), open settings overlay, open picker with query and selection, map expanded flag — verified by: CI unit test (Robolectric)
- [ ] All TASK-004 characterization scenarios pass — verified by: CI unit test
- [ ] Compose tests for each card container with fake ViewModels or fake repositories — verified by: CI unit test
- [ ] MainActivity size and responsibilities as specified — verified by: code review
- [ ] Rotating the device with portrait lock off keeps the dashboard state — verified by: HUMAN on device

## Tests to add or update
- New: `DashboardScreenTest`, rotation test.
- Update: `GnssStatusScreenTest` → split into per-card tests.

## Risks and edge cases
- Horizon: with state surviving rotation, the calibration reference may persist across rotation while it resets on pause/resume; that is acceptable (closer to the original) and becomes fully persistent in TASK-028.
- `hiltViewModel()` scoping: all dashboard VMs are Activity-scoped (single screen); that is intended.
