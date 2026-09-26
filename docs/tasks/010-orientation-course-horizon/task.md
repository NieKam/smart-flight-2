# TASK-010 — One orientation Flow; Course and Horizon ViewModels

## Goal
Replace the orientation listener machinery (one source, three interfaces, two adapters, one dispatcher) with a single shared `Flow<OrientationSample>`, and move Course and Horizon card state into ViewModels. Remove `ForegroundCourseObservationCoordinator`. No user-visible behavior change.

## Context
- Architecture review F14 (OVER_ENGINEERING, LOW), verified: `OrientationSource` (`orientation/OrientationSource.kt:77-83`), `CourseOrientationPlatform` (`course/CourseController.kt:3-9`), `HorizonOrientationPlatform` (`horizon/HorizonController.kt:3-9`), pass-through adapters `SharedCourseOrientationPlatform`/`SharedHorizonOrientationPlatform` (`orientation/OrientationPlatformAdapters.kt:6-42`), and `OrientationEventDispatcher` (`OrientationSource.kt:92-123`). Direction: one data source exposing `Flow<OrientationSample>`, shared; course and horizon map it themselves.
- Architecture review F16: `ForegroundCourseObservationCoordinator` (`course/ForegroundCourseObservationCoordinator.kt:7-34`) couples course, flight and nearby; `CourseController.retry(isForeground)` / `HorizonController.retry(isForeground)` take a UI lifecycle flag. Direction: lifecycle gating belongs to the collector.
- Architecture review K6 (KEEP): `AndroidOrientationSource` reference-counts a single sensor listener shared by course and horizon (`OrientationSource.kt:138-152`); reproduce with `shareIn(WhileSubscribed)`.
- Architecture review K2 (KEEP): `DisplayRelativeOrientation.calculate` (`OrientationSource.kt:36-74`), `mapHorizonAttitude` (`horizon/HorizonState.kt:23`), `normalizeCourseDegrees`/`compassCardinal` (`course/CourseState.kt:16-42`) are pure; keep them and their tests. `DisplayRotation.fromSurfaceRotation` is the only Android-dependent piece and is trivially separable.
- Architecture review DI inventory note: `AndroidOrientationSource(this, …)` uses `context.display` (`OrientationSource.kt:130`); with an application context `getDisplay()` is not associated with a display. A singleton must obtain rotation differently.
- Parity review "Not verified": horizon roll direction and pitch sign in all four display rotations were not verified. Architecture "Not verified" 2: rotation is read per sensor event, so 0°↔180° rotations without Activity recreation may be harmless.
- Current behavior to preserve (changed later on purpose):
  - Rotation-vector sensor only; missing → `CourseState.Unavailable` / `HorizonState.Unavailable` (TASK-026 adds a fallback).
  - Heading floored (`normalizeCourseDegrees`) (TASK-026 rounds).
  - Horizon reference pitch captured on the first sample of each observation start, cleared on stop/pause (`HorizonController.kt:43-49,71`) (TASK-028 changes this).
  - Course shows GPS bearing from the latest fix (`CourseController.onGpsBearing`).
  - Observed dead logic: `MainActivity.kt:584-588` only starts course when `gnssState` is `Waiting`/`Available`; after `attachToExternalSession()` it is always `Waiting`, so course always starts. Drop the condition.

## Dependencies
- TASK-008 (LocationRepository fixes for GPS bearing).

## Target conventions (from CLAUDE.md)
ViewModels with one `StateFlow<UiState>` (`stateIn(viewModelScope, WhileSubscribed(5_000), …)`), `collectAsStateWithLifecycle()`, `callbackFlow` data sources, injected dispatchers, no Android types in logic, package by feature with layers (`orientation/data`, `course/ui`, `horizon/ui`).

## Scope
- `OrientationDataSource` (`@Singleton`): `val samples: Flow<OrientationSample>` = `callbackFlow` over `TYPE_ROTATION_VECTOR`, `shareIn(@ApplicationScope, WhileSubscribed())`; `fun isAvailable(): Boolean`.
- `DisplayRotationProvider` interface; Android implementation using `DisplayManager.getDisplay(Display.DEFAULT_DISPLAY).rotation` from the application context (works for the default display). Read per sample as today.
- `CourseViewModel`: `combine(samples → heading, LocationRepository.fixes → bearing (start with null))` → `CourseState`; `Unavailable` if no sensor; `Error` if registration fails; `retry()` restarts collection (e.g. a retry trigger + `flatMapLatest`).
- `HorizonViewModel`: samples → pitch/roll → `mapHorizonAttitude(pitch - reference, roll)`; `calibrate()` → `Recalibrating` then next sample becomes reference; reference cleared when collection restarts (preserves today's reset-on-resume); `retry()`.
- Delete `OrientationSource`, `AndroidOrientationSource`, `OrientationEventDispatcher`, `CapturedOrientationEvent`, `OrientationPlatformAdapters.kt`, `CourseController`, `HorizonController`, `ForegroundCourseObservationCoordinator`, `HorizonController.onDisplayRotationChanged` and the dead `MainActivity.onConfigurationChanged` override (`MainActivity.kt:380-385`). Nearby-city start/stop previously done by the coordinator moves to the Activity's observation start/stop until TASK-011.
- Keep `DisplayRelativeOrientation` and `DisplayRotation` (move `fromSurfaceRotation` into the Android implementation).

## Out of scope
- Accelerometer+magnetometer fallback, smoothing, rounding (TASK-026); compass rose visual (TASK-027); horizon calibration persistence/long-press/filter (TASK-028); hiding unsupported cards (TASK-029).

## Requirements
Required:
- Exactly one sensor registration while Course and/or Horizon are collected; none when neither is.
- Same visible behavior as listed in Context.
- New pure tests for `DisplayRelativeOrientation.calculate` with known rotation matrices: device flat facing north, pitched nose-up 10°, rolled right 10°, for each of the four `DisplayRotation`s; assert heading, pitch sign and roll sign. If a sign turns out wrong, do NOT fix it here: record it in the PR and in `docs/tasks/README.md` open questions (it is a behavior change; TASK-028 fixes it).

## Acceptance criteria
- [ ] Single shared sensor registration (2 collectors → 1 listener; 0 after both cancel) — verified by: CI unit test (Robolectric `ShadowSensorManager`)
- [ ] Course/Horizon ViewModel tests port every case of `CourseControllerTest`, `HorizonControllerTest`, `OrientationPlatformAdaptersTest`, `ForegroundCourseObservationCoordinatorTest` that still applies — verified by: CI unit test
- [ ] Rotation-matrix tests for all four display rotations exist — verified by: CI unit test
- [ ] Deleted types are gone — verified by: code review
- [ ] Compass and horizon respond correctly in portrait and landscape — verified by: HUMAN on device

## Tests to add or update
- New: `OrientationDataSourceTest`, `CourseViewModelTest`, `HorizonViewModelTest`, `DisplayRelativeOrientationTest`.
- Delete after porting: `CourseControllerTest`, `HorizonControllerTest`, `OrientationPlatformAdaptersTest`, `ForegroundCourseObservationCoordinatorTest`.

## Risks and edge cases
- `DisplayManager` default display vs. the Activity's display on multi-display/foldables. Acceptable for this app; note it.
- Sensor callbacks arrive on the main looper by default; heavy math is small, fine.
