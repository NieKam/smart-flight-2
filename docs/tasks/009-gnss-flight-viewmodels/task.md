# TASK-009 — GNSS status and flight parameters as ViewModels (with a pressure data source)

## Goal
Move the state of the GNSS card and the Flight parameters card out of `MainActivity` into two `@HiltViewModel`s that expose `StateFlow` and are collected with `collectAsStateWithLifecycle`. Wrap the barometer in a `callbackFlow` data source. No user-visible behavior change.

## Context
- Architecture review F1 (VIOLATION, HIGH): UI state lives in 23 `mutableStateOf` fields of `MainActivity` (`MainActivity.kt:91-110,134-135`), plus plain field `pressureMillibars` (`:95`); lost on rotation.
- Architecture review F4: `PressurePlatform.registerPressureListener` callback (`flight/PressureController.kt:7`), `onStateChanged` callbacks in every controller.
- Architecture review F2: pressure merged into flight state in two places (`MainActivity.kt:500-503,519-523`); "duplicates the merge logic that belongs in one combine".
- Architecture review K2 (KEEP): the vertical-speed computation (`FlightParametersController.kt:95-112`) is pure; keep it as a pure function with its tests.
- Architecture review K3 (KEEP): `GnssStatusState`, `FlightParametersState` are immutable, exhaustive, Android-free; keep them as UI state.
- Architecture review K4 (KEEP): `FlightParametersCard` stays stateless; ViewModels live one level above the cards.
- Current behavior to preserve (it is changed deliberately later):
  - Pressure is shown only once a GPS reading exists (merged only into `Readings`) — changed in TASK-020.
  - Vertical speed = raw delta between two fixes, first value null — changed in TASK-020.
  - GNSS state is `Waiting` until satellites arrive, then `Available`; empty list → `Waiting` (`GnssStatusController.kt:62-66`). Disabled/unavailable/error states are wired in TASK-019.
  - On pause, flight state resets to `Waiting` and vertical-speed history is cleared (`FlightParametersController.stop()`, `:57-64`).

## Dependencies
- TASK-008.

## Target conventions (from CLAUDE.md)
UI state in ViewModels, one `StateFlow<UiState>` per ViewModel via `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)`; collected with `collectAsStateWithLifecycle()`; must survive configuration changes; repositories expose `Flow`; `callbackFlow` for Android callbacks; injectable dispatchers; keep Android types out of logic; package by feature with layers (`flight/data`, `flight/ui`, `gnss/ui`).

## Scope
- `PressureDataSource` (`callbackFlow` over `SensorManager` TYPE_PRESSURE; `hasPressureSensor()`), replacing `AndroidPressurePlatform` + `PressureController`.
- `GnssStatusViewModel`: `satellites` from `LocationRepository` → `GnssStatusState`.
- `FlightParametersViewModel`: `LocationRepository.fixes` scanned through the pure vertical-speed function → `Readings`; combined with pressure exactly as today (pressure only attached to `Readings`).
- Lifecycle equivalence: history reset when collection restarts (use the upstream starting inside `stateIn(WhileSubscribed)`; e.g. `flow { … }` builder re-created per subscription, or `onStart` reset).
- `MainActivity`: obtain the two ViewModels (`hiltViewModel()` in `setContent` or `by viewModels()`), pass their state into `GnssStatusScreen`; delete `gnssStatusController`, `flightParametersController`, `pressureController` and their fan-out hooks. Other fan-out consumers (course bearing, nearby, route, map) keep receiving fixes from the Activity's collector until their tasks.
- Delete `GnssStatusController`, `FlightParametersController`, `PressureController`, `AndroidPressurePlatform`; port their tests to ViewModel tests (`runTest`, `StandardTestDispatcher`, fake repository/data source), keeping every behavioral case.
- Move `FlightParametersCard.kt` from `ui/gnss/` to `flight/ui/` only if it does not create large conflicts; otherwise leave file moves to TASK-016 (state which in the PR).

## Out of scope
- Pressure-before-fix and vertical-speed smoothing (TASK-020); GPS-disabled states (TASK-019); satellite chart (TASK-022).
- `ForegroundCourseObservationCoordinator` (TASK-010) — adapt it minimally if it referenced `FlightParametersController` (it calls `flightParametersController.start()`; replace with the Activity's collection being active).

## Requirements
Required:
- Same visible behavior as before, including the two "changed later" behaviors above.
- State survives rotation (ViewModel), but the flight readings still reset when observation restarts after `onPause`/`onResume` (as today).
- Pressure sensor registered only while the Flight card state is collected; unregistered on stop.

## Acceptance criteria
- [ ] ViewModels expose `StateFlow`; Activity has no GNSS/flight/pressure fields — verified by: code review
- [ ] Ported tests for vertical speed, speed conversion, first readable reading, pressure merge, GNSS empty/available — verified by: CI unit test
- [ ] `PressureDataSource` registers/unregisters with collection — verified by: CI unit test (Robolectric `ShadowSensorManager`)
- [ ] TASK-004 characterization scenarios 3, 4, 5, 8 pass — verified by: CI unit test
- [ ] Rotation keeps the last readings visible without flicker to "Waiting" — verified by: CI unit test (Robolectric `ActivityScenario.recreate()`), HUMAN on device optional

## Tests to add or update
- New: `GnssStatusViewModelTest`, `FlightParametersViewModelTest`, `PressureDataSourceTest`.
- Delete after porting: `GnssStatusControllerTest`, `FlightParametersControllerTest`, `PressureControllerTest`.

## Risks and edge cases
- `WhileSubscribed(5_000)`: after rotation (< 5 s) the upstream is not restarted, so readings persist — intended. After a real pause > 5 s the upstream restarts and history resets — matches today's pause behavior closely enough; document the 5 s window.
- Non-finite values: keep the existing `isFinite` filters.
