# TASK-003 — Review iteration 1

## Result
PASS

## Acceptance criteria
criterion | status | verified by
---|---|---
The listed symbols are gone: `git grep` for `BackgroundMonitoringSession`, `MapLoadAttemptGate` and `formatKilometres` finds nothing in `app/src` | MET | `git grep` at HEAD across `app/` (main and test) finds none of them. It also finds no `BackgroundMonitoringState` and no bare `normalizeCourse`.
The map marker course normalization still returns fractional degrees, and the existing `MapStateTest` passes unchanged | MET (source) / CI | The function body is byte-identical; only the name and KDoc changed. `MapStateTest.kt` is not in the diff. CI is green on c2dbb77 as reported.
`AboutPlatformTest` passes | MET (source) / CI | `AboutPlatformTest.kt` is unchanged and still exercises `read()`: normal code 42, null info, and code 0 filtered to null. CI green as reported.
Build, tests and ktlint are green | MET per CI | CI run 36408802665 passed on HEAD c2dbb77 (as reported by the orchestrator; I did not check it myself).

## Scope check
- **`BackgroundMonitoringSession.kt`:** deleted. `BackgroundMonitoringTest.kt` keeps its `BackgroundNotificationPreferencesStore` test and the `FakePreferences` fake. Only the two session tests were removed, plus the now-unused `assertEquals` import.
- **`MapLoadAttemptGate`:** deleted, along with its single test. `MapArchiveCopierTest` still uses `assertTrue`/`assertFalse` for the remaining copier tests, so those imports stay.
- **`formatKilometres`:** deleted, along with the unused `NumberFormat` import. `Locale` stays because other code in the file uses it.
- **`normalizeCourse`:** renamed to `normalizeMarkerCourse`, with KDoc explaining how it differs from `normalizeCourseDegrees`. The body is unchanged: null or non-finite input returns null, otherwise `((c % 360) + 360) % 360` as a Float. This is the task's "rename or document" option. The only call site, `MapSessionRules`, is updated and keeps its `?: 0f` fallback.
- **`callbackExecutor` in `AndroidPressurePlatform`:** removed, along with the `Executor` import. The `MainActivity` call site is updated. The other `callbackExecutor = mainExecutor` arguments in `MainActivity` (lines 539 and 550) belong to `AndroidFlightLocationPlatform` and `AndroidGnssStatusPlatform`, which use them. Correctly left alone.
- **`AndroidAppVersionProvider.read()`:** now uses `runCatching { info.longVersionCode }.getOrNull()?.takeIf { it > 0 }`. With minSdk 31, the old `SDK_INT >= P` branch always ran exactly this expression, so behavior is identical, including exception handling and the `> 0` filter. The `android.os.Build` import is removed.
- **`ExampleUnitTest.kt`:** deleted. There is no `androidTest` source set left at HEAD.
- **Out of scope respected:** `LocationGnssMonitoringSession` (including `isActive`) and `BackgroundMonitoringBridge` are untouched. Nothing beyond deletion and the rename was done.

## Behavior changes
None. All removed code was either unreachable (pre-API-28 branch, given minSdk 31) or referenced only from tests or nowhere (confirmed with `git grep` over main and test).

## Blocking findings
None.

## Escalations
None.

## Non-blocking findings
- The new `/home/ai-dev/smart-flight-2-modern/app/src/test/java/kniezrec/com/flightinfo/map/MarkerCourseTest.kt` is a useful addition: fractional, negative, above 360, NaN and infinity cases. It would catch an accidental switch to whole degrees, for example `10.5` → `10`. The task only required such tests if a shared helper was introduced, so this is extra coverage and does not count as scope creep.
- `AboutPlatformTest` sets the deprecated `versionCode` field, and `read()` now reads `longVersionCode`. On the Robolectric SDK 35 test platform these are linked, so the existing assertions still exercise the path. No action needed.

## Human checks on device
- Optional smoke check, since no behavior change is expected:
  - The About screen still shows "name (code)".
  - The map marker still rotates smoothly with bearing.
  - The pressure value still updates on devices that have a barometer.

## Verification performed
- **Source review:** I read `task.md` and the full diff of `origin/ai-modernization...HEAD` (7 commits, 11 files). I read the full HEAD content of `AndroidPressurePlatform.kt`, `AboutPlatform.kt` and `AboutPlatformTest.kt`. I ran `git grep` at HEAD for every removed or renamed symbol and for `callbackExecutor` / `Build.VERSION`. I checked that `MapStateTest.kt` and `AboutPlatformTest.kt` still exist and are unmodified, and that no `androidTest` sources remain.
- **CI result as reported:** PASS on c2dbb77, run 36408802665. Test counts are unavailable (no artifact for green runs).
- **Not verified:** runtime behavior on a device; the test counts or the list of executed tests in CI; ktlint (not run locally, since this review is read-only).

Files reviewed:
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/about/AboutPlatform.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/map/MapState.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/flight/AndroidPressurePlatform.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt
- /home/ai-dev/smart-flight-2-modern/app/src/test/java/kniezrec/com/flightinfo/map/MarkerCourseTest.kt
- /home/ai-dev/smart-flight-2-modern/app/src/test/java/kniezrec/com/flightinfo/monitoring/BackgroundMonitoringTest.kt
- /home/ai-dev/smart-flight-2-modern/app/src/test/java/kniezrec/com/flightinfo/map/MapArchiveCopierTest.kt
