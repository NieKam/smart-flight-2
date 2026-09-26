# TASK-002 — Run the Compose UI tests on the JVM (Robolectric) and retire the instrumented source set

## Goal
Move the Compose UI tests from `app/src/androidTest` to `app/src/test` so they run with Robolectric as part of `testDebugUnitTest` in CI. No emulator and no instrumented tests run in CI. Tests that cannot run on the JVM are removed and their scenarios become explicit HUMAN on-device checks, so the project has no test code that nothing ever runs.

## Context
- Architecture review F12 (GAP, MEDIUM): "CI runs only `ktlintCheck testDebugUnitTest assembleDebug` (`.github/workflows/build.yml:36`), so the Compose tests in `androidTest` never run anywhere automatically." The report leaves "whether instrumented tests should run in CI" as a planner decision.
- Human decision: do not run UI tests on an emulator in GitHub Actions. Compose UI tests that run on the JVM with Robolectric inside `testDebugUnitTest` are allowed where they pay off; everything else in the UI is verified by the HUMAN on a device.
- The project already uses Robolectric (`robolectric = "4.17"`, `isIncludeAndroidResources = true` in `app/build.gradle.kts:34-38`; e.g. `LocationForegroundServiceTest` uses `@Config(sdk = [35])`).
- Most later tasks (ViewModel migration, parity restoration) change composables; without this task none of those changes would be checked by CI.
- Observed Compose tests: `app/src/androidTest/java/kniezrec/com/flightinfo/ui/about/AboutDialogTest.kt`, `ui/gnss/GnssStatusScreenTest.kt`, `ui/gnss/HorizonCardTest.kt`, `ui/gnss/NearbyCityCardTest.kt`, `ui/permission/PermissionOnboardingScreenTest.kt`, `ui/settings/UnitSettingsScreenTest.kt`. They use `createAndroidComposeRule<ComponentActivity>()` and `AndroidJUnit4`. There is also the template `ExampleInstrumentedTest.kt`.

## Dependencies
- TASK-001 (build files are edited in both; avoids conflicts).

## Scope
- Add `testImplementation(platform(libs.androidx.compose.bom))` and `testImplementation(libs.androidx.compose.ui.test.junit4)` (and `androidx-junit` if the tests keep `AndroidJUnit4`). `debugImplementation(libs.androidx.compose.ui.test.manifest)` already exists.
- Move each Compose test file to the same package under `app/src/test/java/...`. Run with `@RunWith(AndroidJUnit4::class)` or `RobolectricTestRunner` and a pinned SDK in one place: `app/src/test/resources/robolectric.properties` with `sdk=35` (existing `@Config(sdk = [35])` annotations may stay).
- Apply `@GraphicsMode(GraphicsMode.Mode.NATIVE)` only where a test needs real drawing (e.g. `captureToImage()` pixel checks used by later tasks).
- A test method that genuinely cannot run under Robolectric (for example one that needs a real osmdroid `MapView` rendering, if any) is deleted, not kept in `androidTest`. For each deleted method, write in the PR description what it checked and add that scenario to the HUMAN on-device checklist of this PR.
- Delete `ExampleInstrumentedTest.kt` and, once empty, the `app/src/androidTest` source set, the `androidTestImplementation` dependencies that only it used and `testInstrumentationRunner` if nothing needs it.
- `.github/workflows/build.yml`: unchanged (no emulator step, no instrumented compile step).

## Out of scope
- Changing what the tests assert (they pin current behavior; keep them identical apart from runner/config changes).
- Deleting `ExampleUnitTest.kt` (TASK-003).
- Adding new UI tests (TASK-004 onwards).

## Requirements
Required:
- Every moved test keeps its test methods and assertions. If an assertion must change to run under Robolectric (e.g. `assertIsDisplayed` on an off-screen node in a small default window), change the setup (window size via `@Config(qualifiers = "w411dp-h891dp")`) rather than weakening the assertion; list any unavoidable change in the PR description.
- CI runs the moved tests as part of `testDebugUnitTest`; no emulator, no `connected*` task.
- No production code changes.

Recommendations:
- Put shared Robolectric configuration in one place (`robolectric.properties`).

## Acceptance criteria
- [ ] The six Compose test classes run in `testDebugUnitTest` and pass — verified by: CI unit test (check the test report lists them)
- [ ] `app/src/androidTest` no longer exists (or the PR explains what remains and why) and the workflow has no emulator/instrumented step — verified by: code review
- [ ] Any deleted test method is listed in the PR with its scenario as a HUMAN on-device check — verified by: code review
- [ ] No file under `app/src/main` changed — verified by: code review
- [ ] ktlint passes — verified by: ktlint

## Tests to add or update
- Moved: `AboutDialogTest`, `GnssStatusScreenTest`, `HorizonCardTest`, `NearbyCityCardTest`, `PermissionOnboardingScreenTest`, `UnitSettingsScreenTest`.
- Deleted: `ExampleInstrumentedTest`.

## Risks and edge cases
- `GnssStatusScreenTest` creates zip files and hosts `MapCard`/`RoutePicker`, which embed an osmdroid `MapView` through `AndroidView`. Under Robolectric the `MapView` may fail to construct or render. Try first; only if it fails, delete those specific methods as described above.
- Robolectric SDK 37 may not be supported by Robolectric 4.17; pin SDK 35 as the existing tests do.
- CI time grows; keep an eye on the ~2 minute budget and mention the new duration in the PR.
