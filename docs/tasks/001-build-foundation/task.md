# TASK-001 — Build foundation: coroutines, lifecycle-compose, coroutine tests and KSP in the version catalog

## Goal
Add the libraries and plugins that every later migration task needs, at verified current versions, without changing any application code or behavior.

## Context
- Architecture review `docs/review/architecture.md`, F13 (GAP, MEDIUM): the catalog (`gradle/libs.versions.toml`) has no `ksp` plugin, no `hilt`, no `androidx-lifecycle-viewmodel-compose`, no `androidx-lifecycle-runtime-compose`, no `kotlinx-coroutines-android` / `kotlinx-coroutines-test`. Coroutines arrive only transitively (`MainActivity.kt:87` uses `kotlinx.coroutines.launch`).
- Architecture review "Not verified" item 1: KSP and Hilt compatibility with AGP 9 built-in Kotlin was not checked.
- Observed: `app/build.gradle.kts:1-5` applies only `com.android.application`, `org.jetbrains.kotlin.plugin.compose` and ktlint. There is no `org.jetbrains.kotlin.android` plugin: the project uses AGP 9 built-in Kotlin (AGP `9.4.0`, Kotlin `2.2.10` in the catalog).
- Hilt itself is added in TASK-005 as a dedicated step. This task only prepares the build so TASK-005 is a pure DI change.

## Dependencies
none

## Scope
- `gradle/libs.versions.toml`: add versions, libraries and plugins:
  - `kotlinx-coroutines-core`, `kotlinx-coroutines-android` (implementation) and `kotlinx-coroutines-test` (testImplementation), one shared version ref.
  - `androidx-lifecycle-viewmodel-compose`, `androidx-lifecycle-runtime-compose` (use the existing `lifecycleRuntimeKtx` version ref if the artifacts share the version; they are in the same `androidx.lifecycle` release train).
  - Plugin `ksp` (`com.google.devtools.ksp`).
  - Optional: `app.cash.turbine:turbine` (testImplementation) for Flow tests. Add it only if you plan to use it in this task's sample test; otherwise leave it to the first task that needs it.
- `app/build.gradle.kts`: apply the KSP plugin (no processors yet) and add the dependencies above.
- `build.gradle.kts` (root): declare the KSP plugin with `apply false` if that is the project convention (check the root file).
- One trivial JVM test proving `kotlinx-coroutines-test` is wired (for example `runTest` with a `StandardTestDispatcher` in `app/src/test/.../BuildSmokeTest.kt`). Delete `ExampleUnitTest.kt` only in TASK-003, not here.

## Out of scope
- Hilt / Dagger (TASK-005).
- Any production code change.
- Changing `compileOptions` from Java 11 (see README "Deferred": not blocking).

## Requirements
Required:
- Look up current stable versions (Maven Central / Google Maven / GitHub releases) for: kotlinx-coroutines, androidx.lifecycle, KSP, and — for information only, to unblock TASK-005 — Hilt/Dagger and `androidx.hilt`. Record the versions found and their source links in the PR description.
- Confirm from KSP release notes that the chosen KSP version supports AGP 9 built-in Kotlin with Kotlin 2.2.x (KSP 2.x versions are no longer tied to the Kotlin version; check the compatibility table). Record the evidence in the PR description.
- Also record whether the Hilt Gradle plugin release notes state AGP 9 / built-in Kotlin support, and which minimum version. If no Hilt version supports it, write that in the PR description and in `docs/tasks/README.md` "Open questions" so the human can decide before TASK-005 starts.
- The app still builds and all existing tests pass. No source file under `app/src/main` changes.

Recommendations:
- Keep catalog naming consistent with existing entries (`androidx-...` kebab-case keys).

## Acceptance criteria
- [ ] Catalog contains coroutines (core, android, test), lifecycle-viewmodel-compose, lifecycle-runtime-compose and the KSP plugin — verified by: code review
- [ ] KSP plugin applied in `app/build.gradle.kts` and `assembleDebug` succeeds — verified by: CI (build step)
- [ ] Smoke test using `runTest` passes — verified by: CI unit test
- [ ] PR description lists versions, sources, and the KSP/Hilt AGP 9 compatibility evidence — verified by: code review
- [ ] `./gradlew ktlintCheck` passes — verified by: ktlint

## Tests to add or update
- `app/src/test/java/kniezrec/com/flightinfo/BuildSmokeTest.kt` (or similar): one `runTest` test.

## Risks and edge cases
- KSP applied without processors may print a warning; that is acceptable.
- If the KSP plugin fails with AGP 9 built-in Kotlin in CI, do not work around it by adding `org.jetbrains.kotlin.android`; stop, document the failure in the PR and ask the human (it changes the Kotlin setup of the whole project).
- Local environment has no Android SDK: only `./gradlew ktlintCheck` may be run locally; everything else is verified in CI.
