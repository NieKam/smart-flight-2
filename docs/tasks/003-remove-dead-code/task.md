# TASK-003 — Remove dead and test-only production code

## Goal
Delete production code that nothing uses, so later migration tasks (DI, Flow, ViewModels) have less surface to move. No behavior change.

## Context
- Architecture review F15 (OVER_ENGINEERING, LOW), all verified in code:
  - `monitoring/BackgroundMonitoringSession.kt` (`BackgroundMonitoringSession`, `BackgroundMonitoringState`) is referenced only from `app/src/test/.../monitoring/BackgroundMonitoringTest.kt`.
  - `MapLoadAttemptGate` (`map/MapArchiveCopier.kt:52-58`) is used only in `MapArchiveCopierTest.kt`.
  - `formatKilometres` (`route/RouteModels.kt:111-117`) has no references.
  - `normalizeCourse` (`map/MapState.kt:28-32`) duplicates `normalizeCourseDegrees` (`course/CourseState.kt:16-19`) except that it returns an unrounded `Float`.
  - Unused constructor parameter `callbackExecutor` in `flight/AndroidPressurePlatform.kt:11`.
  - Unreachable pre-API-28 branch in `about/AboutPlatform.kt:38-43` (minSdk is 31).
  - `LocationGnssMonitoringSession.isActive` (`monitoring/LocationGnssMonitoringSession.kt:57`) is test-only. It is deleted together with the whole class in TASK-007; leave it here.
- Architecture review F12: template leftovers `app/src/test/.../ExampleUnitTest.kt` and `app/src/androidTest/.../ExampleInstrumentedTest.kt`.

## Dependencies
- TASK-002 (tests were moved; avoids conflicts in test directories).

## Scope
- Delete `BackgroundMonitoringSession.kt` and the parts of `BackgroundMonitoringTest.kt` that test it. Check whether the rest of `BackgroundMonitoringTest.kt` tests live code (it contains a `SharedPreferences` fake at about line 43 used for `BackgroundNotificationPreferencesStore`); keep those tests.
- Delete `MapLoadAttemptGate` and its test case in `MapArchiveCopierTest.kt`.
- Delete `formatKilometres`.
- Keep `normalizeCourse` behavior for the map marker, which needs a fractional `Float` in [0, 360): do NOT replace it with `normalizeCourseDegrees` (that floors to `Int`, which would change marker rotation). Instead, rename or document so the difference is explicit, or implement `normalizeCourseDegrees` on top of a shared `normalizeDegrees(Double): Double?` helper used by both. Pick one; behavior of both call sites must be unchanged.
- Remove the unused `callbackExecutor` parameter from `AndroidPressurePlatform` and its call site in `MainActivity.kt:517`.
- Remove the pre-API-28 branch in `AndroidAppVersionProvider.read()`; use `info.longVersionCode` directly.
- Delete `ExampleUnitTest.kt` and `ExampleInstrumentedTest.kt` (if TASK-002 left the `androidTest` source set empty, it may be removed; keep `androidTestImplementation` dependencies only if tests remain).

## Out of scope
- `LocationGnssMonitoringSession` (TASK-007), `BackgroundMonitoringBridge` (TASK-008).
- Any refactor beyond deletion.

## Requirements
Required:
- No behavior change. Marker rotation, course display and version display stay identical.
- All remaining tests pass.

## Acceptance criteria
- [ ] Listed symbols are gone; `grep` for `BackgroundMonitoringSession`, `MapLoadAttemptGate`, `formatKilometres` finds nothing in `app/src` — verified by: code review
- [ ] Map marker course normalization still returns fractional degrees; existing `MapStateTest` passes unchanged — verified by: CI unit test
- [ ] `AboutPlatformTest` passes — verified by: CI unit test
- [ ] Build, tests, ktlint green — verified by: CI + ktlint

## Tests to add or update
- Update `BackgroundMonitoringTest.kt`, `MapArchiveCopierTest.kt` (remove cases for deleted code only).
- If a shared `normalizeDegrees` helper is introduced, add unit tests for negative, >360, NaN and fractional inputs.

## Risks and edge cases
- Accidentally switching the marker to integer degrees; covered by keeping `MapStateTest` green and adding the helper test.
