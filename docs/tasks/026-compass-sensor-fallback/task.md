# TASK-026 — Compass on devices without a rotation-vector sensor, heading smoothing and rounding

## Goal
Keep the compass (and horizon) working on devices without `TYPE_ROTATION_VECTOR` by falling back to accelerometer + magnetometer as the original did, smooth the heading, and round it instead of truncating.

## Context
- Parity finding 5 (REGRESSION, MEDIUM): original heading from accelerometer + magnetometer with a low-pass filter (α = 0.97) and a 10-sample moving average (`~/smart-flight/app/src/main/java/kniezrec/com/flightinfo/avionic/calculators/CourseCalculator.kt:35-69`, `services/SensorService.kt:61-64`, `AverageCalculator.kt`); card disabled only when those features are missing (`cards/course/CourseCardViewPresenter.kt:33-36`). Rewrite uses only `TYPE_ROTATION_VECTOR` (before TASK-010: `orientation/OrientationSource.kt:131,136`); missing → "Compass unavailable". The rewrite computes heading relative to the display (keep — improves landscape).
- Parity finding 15 (REGRESSION, LOW): original `roundToInt()` (`CourseCalculator.kt:61-62`); rewrite floors (`course/CourseState.kt:16-19`, `normalizeCourseDegrees`): 271.6° shows 271° instead of 272°.
- Original horizon: its presenter only checked `FEATURE_SENSOR_ACCELEROMETER` for the overlay (`cards/horizon/HorizonCardViewPresenter.kt:26,35`), but its data actually came from `TYPE_ROTATION_VECTOR` with a low-pass filter (`services/SensorService.kt:64,154`, `avionic/calculators/OrientationCalculator.kt:24`, `Filter.kt` α = 0.25). So the original horizon did not work without a rotation-vector sensor either.
- Parity "Not verified": how many users lack a rotation-vector sensor is unknown (README open question); the fallback is cheap, so it is planned regardless.
- After TASK-010: `OrientationDataSource` exposes `Flow<OrientationSample>` built from the rotation-vector matrix and `DisplayRelativeOrientation.calculate` (pure).

## Dependencies
- TASK-016 (TASK-010 content).

## Original app reference
- `avionic/calculators/CourseCalculator.kt`, `AverageCalculator.kt`, `Filter.kt`, `services/SensorService.kt`, `cards/course/CourseCardViewPresenter.kt`.

## Scope
- `OrientationDataSource`: if `TYPE_ROTATION_VECTOR` exists use it (current behavior); else if accelerometer + magnetometer exist, combine them (`SensorManager.getRotationMatrix`) with the original low-pass filter on the raw vectors, then feed the same `DisplayRelativeOrientation.calculate`. Availability = either path available.
- Horizon: because both paths produce the same `OrientationSample` (heading, pitch, roll), the horizon also works on the accel+mag path. This goes slightly beyond the original (whose horizon needed a rotation vector); it costs nothing and is accepted. Accelerometer-only devices (no magnetometer) are out of scope: both cards stay unavailable there.
- Heading smoothing in a pure class: circular moving average over the last 10 samples (average of unit vectors, so 359°→0° does not jump through 180°) applied to both paths. Document that the original averaged raw degrees (wrap bug) and this is intentionally fixed.
- Rounding: `normalizeCourseDegrees` uses `roundToInt()` then normalizes to [0, 360) (so 359.6 → 0).

## Out of scope
- Compass rose visuals (TASK-027); horizon calibration (TASK-028).

## Requirements
Required:
- Compass and horizon available on devices with accelerometer + magnetometer but no rotation-vector sensor.
- Heading rounded; smoothing handles wrap-around.
- Behavior changes listed in PR.

## Acceptance criteria
- [ ] Pure tests: rounding (271.4→271, 271.6→272, 359.6→0, -0.4→0), circular average across 359/1, filter math — verified by: CI unit test
- [ ] Data source test: no rotation vector + accel + mag → samples emitted; accel only or nothing → unavailable; with rotation vector → rotation vector used (not the fallback) — verified by: CI unit test (Robolectric `ShadowSensorManager`)
- [ ] Heading stable and correct on a device; if possible also on a device without gyroscope — verified by: HUMAN on device

## Tests to add or update
- `CourseStateTest`/`NormalizeCourseTest`, `HeadingSmootherTest`, `OrientationDataSourceTest`, `CourseViewModelTest`.

## Risks and edge cases
- Magnetometer accuracy in aircraft is poor; the card shows GPS bearing as a secondary line already.
- Smoothing adds latency (~10 samples at `SENSOR_DELAY_UI`); acceptable, matches original.
