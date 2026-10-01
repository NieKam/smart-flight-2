# TASK-001 — Build foundation: coroutines, lifecycle-compose and coroutine tests in the version catalog

## Goal
Add the libraries every later migration task needs, at verified current versions, without changing any application code or behavior. Record the release identity decided by the human (versionCode 1, versionName "1.0.0").

## Context
- Architecture review `docs/review/architecture.md`, F13 (GAP, MEDIUM): the catalog (`gradle/libs.versions.toml`) has no `ksp` plugin, no `hilt`, no `androidx-lifecycle-viewmodel-compose`, no `androidx-lifecycle-runtime-compose`, no `kotlinx-coroutines-android` / `kotlinx-coroutines-test`. Coroutines arrive only transitively (`MainActivity.kt:87` uses `kotlinx.coroutines.launch`).
- Observed: `app/build.gradle.kts:1-5` applies only `com.android.application`, `org.jetbrains.kotlin.plugin.compose` and ktlint. There is no `org.jetbrains.kotlin.android` plugin: the project uses AGP 9 built-in Kotlin (AGP `9.4.0`, Kotlin `2.2.10` in the catalog).
- KSP and Hilt (architecture review "Not verified" item 1: compatibility with AGP 9 built-in Kotlin) are deliberately NOT part of this task. Human decision: TASK-005 adds KSP and Hilt together, verifies them first in CI, and may add the `org.jetbrains.kotlin.android` plugin if built-in Kotlin does not work. Keeping KSP out of this task means this task can never block on that question and the compatibility evidence is gathered once, in TASK-005.
- Human decision (no Play Store release): the app keeps `versionCode = 1`; `versionName` is `"1.0.0"`. Observed today: `app/build.gradle.kts:17-18` has `versionCode = 1`, `versionName = "1.0"`.

## Dependencies
none

## Scope
- `gradle/libs.versions.toml`: add versions and libraries:
  - `kotlinx-coroutines-core`, `kotlinx-coroutines-android` (implementation) and `kotlinx-coroutines-test` (testImplementation), one shared version ref.
  - `androidx-lifecycle-viewmodel-compose`, `androidx-lifecycle-runtime-compose` (use the existing `lifecycleRuntimeKtx` version ref if the artifacts share the version; they are in the same `androidx.lifecycle` release train).
  - Optional: `app.cash.turbine:turbine` (testImplementation) for Flow tests. Add it only if you use it in this task's smoke test; otherwise leave it to the first task that needs it.
- `app/build.gradle.kts`: add the dependencies above; change `versionName` to `"1.0.0"` (keep `versionCode = 1`).
- One trivial JVM test proving `kotlinx-coroutines-test` is wired (for example `runTest` with a `StandardTestDispatcher` in `app/src/test/.../BuildSmokeTest.kt`). Delete `ExampleUnitTest.kt` only in TASK-003, not here.

## Out of scope
- KSP, Hilt / Dagger and their compatibility research (TASK-005).
- Any production code change.
- Changing `compileOptions` from Java 11 (see README "Deferred": not blocking).
- Any other version bump (no Play Store release; see README "Deferred / rejected").

## Requirements
Required:
- Look up current stable versions (Maven Central / Google Maven / GitHub releases) for kotlinx-coroutines and androidx.lifecycle. Record the versions found and their source links in the PR description. Do not assume versions.
- The app still builds and all existing tests pass. No source file under `app/src/main` changes.
- `versionCode` stays 1; `versionName` becomes `"1.0.0"` (listed as the only user-visible change: About shows "1.0.0 (1)").

Recommendations:
- Keep catalog naming consistent with existing entries (`androidx-...` kebab-case keys).

## Acceptance criteria
- [ ] Catalog contains coroutines (core, android, test), lifecycle-viewmodel-compose and lifecycle-runtime-compose — verified by: code review
- [ ] `assembleDebug` succeeds with the new dependencies — verified by: CI (build step)
- [ ] Smoke test using `runTest` passes — verified by: CI unit test
- [ ] `versionCode = 1`, `versionName = "1.0.0"` — verified by: code review
- [ ] PR description lists versions and sources — verified by: code review
- [ ] `./gradlew ktlintCheck` passes — verified by: ktlint

## Tests to add or update
- `app/src/test/java/kniezrec/com/flightinfo/BuildSmokeTest.kt` (or similar): one `runTest` test.

## Risks and edge cases
- Local environment has no Android SDK: only `./gradlew ktlintCheck` may be run locally; everything else is verified in CI.
- If a Compose test pins the About version text from `BuildConfig`, update it; the existing `AboutDialogTest` passes its own `AppVersion("1.0", 1)` and is unaffected.
