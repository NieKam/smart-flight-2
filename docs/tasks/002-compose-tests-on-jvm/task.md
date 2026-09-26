# TASK-002 — Run the Compose UI tests on the JVM (Robolectric) so CI executes them

## Goal
Move the Compose UI tests from `app/src/androidTest` to `app/src/test` and run them with Robolectric, so that every later UI change is checked by CI. Anything that cannot run on the JVM stays in `androidTest`, and CI at least compiles it.

## Context
- Architecture review F12 (GAP, MEDIUM): "CI runs only `ktlintCheck testDebugUnitTest assembleDebug` (`.github/workflows/build.yml:36`), so the Compose tests in `androidTest` never run anywhere automatically." The report leaves "whether instrumented tests should run in CI" as a planner decision.
- Planner decision: no emulator in CI (cost, ~2 min budget, low-resource setup). Instead, run Compose tests under Robolectric, which the project already uses (`robolectric = "4.17"`, `isIncludeAndroidResources = true` in `app/build.gradle.kts:34-38`, and e.g. `LocationForegroundServiceTest` uses `@Config(sdk = [35])`).
- Most later tasks (ViewModel migration, parity restoration) change composables. Without this task, none of those changes would be verified by CI.
- Observed Compose tests: `app/src/androidTest/java/kniezrec/com/flightinfo/ui/about/AboutDialogTest.kt`, `ui/gnss/GnssStatusScreenTest.kt`, `ui/gnss/HorizonCardTest.kt`, `ui/gnss/NearbyCityCardTest.kt`, `ui/permission/PermissionOnboardingScreenTest.kt`, `ui/settings/UnitSettingsScreenTest.kt`. They use `createAndroidComposeRule<ComponentActivity>()` and `AndroidJUnit4`.

## Dependencies
- TASK-001 (build files are edited in both; avoids conflicts).

## Scope
- Add `testImplementation(platform(libs.androidx.compose.bom))` and `testImplementation(libs.androidx.compose.ui.test.junit4)` (and `androidx-junit` if the tests keep `AndroidJUnit4`). `debugImplementation(libs.androidx.compose.ui.test.manifest)` already exists.
- Move each Compose test file to the same package under `app/src/test/java/...`. Run with `@RunWith(AndroidJUnit4::class)` or `RobolectricTestRunner`, and a pinned SDK (use `@Config(sdk = [35])` or an `app/src/test/resources/robolectric.properties` with `sdk=35`, whichever is simpler; be consistent).
- If Robolectric needs `@GraphicsMode(GraphicsMode.Mode.NATIVE)` for some assertions (e.g. drawing), apply it only where needed.
- Tests that genuinely cannot run under Robolectric (for example ones that need a real `MapView` rendering, if any) stay in `androidTest`. List each of them with the reason in the PR description.
- `.github/workflows/build.yml`: if any test remains in `androidTest`, add `compileDebugAndroidTestKotlin` (or `assembleDebugAndroidTest`) to the Gradle command so the instrumented sources at least compile.

## Out of scope
- Changing what the tests assert (they pin current behavior; keep them identical apart from runner/config changes).
- Deleting `ExampleInstrumentedTest.kt` (TASK-003).
- Adding new UI tests (TASK-004 onwards).

## Requirements
Required:
- Every moved test keeps its test methods and assertions. If an assertion must change to run under Robolectric (e.g. `assertIsDisplayed` on an off-screen node in a small default window), change the setup (window size via `@Config(qualifiers = "w411dp-h891dp")`) rather than weakening the assertion; list any unavoidable change in the PR description.
- CI runs the moved tests as part of `testDebugUnitTest`.
- No production code changes.

Recommendations:
- Put shared Robolectric configuration in one place (`robolectric.properties`).

## Acceptance criteria
- [ ] The six Compose test classes run in `testDebugUnitTest` and pass — verified by: CI unit test (check the test report lists them)
- [ ] Any test left in `androidTest` is listed with a reason, and its sources compile in CI — verified by: code review + CI
- [ ] No file under `app/src/main` changed — verified by: code review
- [ ] ktlint passes — verified by: ktlint

## Tests to add or update
- Moved: `AboutDialogTest`, `GnssStatusScreenTest`, `HorizonCardTest`, `NearbyCityCardTest`, `PermissionOnboardingScreenTest`, `UnitSettingsScreenTest`.

## Risks and edge cases
- `GnssStatusScreenTest` creates zip files and hosts `MapCard`/`RoutePicker`, which embed an osmdroid `MapView` through `AndroidView`. Under Robolectric the `MapView` may fail to construct or render. If so, keep only those specific test methods in `androidTest` (split the class) and document it.
- Robolectric SDK 37 may not be supported by Robolectric 4.17; pin SDK 35 as the existing tests do.
- CI time grows; keep an eye on the ~2 minute budget and mention the new duration in the PR.
