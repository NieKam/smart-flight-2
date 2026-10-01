# TASK-001 — Review iteration 1

## Result
**PASS**

There are no blocking findings and nothing needs escalating. One acceptance criterion, the PR description, can't be checked yet because the PR hasn't been opened.

## Acceptance criteria

| criterion | status | verified by |
|---|---|---|
| Catalog contains coroutines (core, android, test), lifecycle-viewmodel-compose and lifecycle-runtime-compose | MET | Code review: `gradle/libs.versions.toml`. The three coroutines libraries share one `kotlinxCoroutines = "1.11.0"` ref. Both lifecycle-compose libraries reuse `lifecycleRuntimeKtx` (2.11.0). The keys follow the existing kebab-case naming. |
| `assembleDebug` succeeds with the new dependencies | MET (as reported) | CI run 36254743908 on bd2e396, reported as PASS |
| Smoke test using `runTest` passes | MET (as reported) | `BuildSmokeTest.kt` exists and uses `runTest(StandardTestDispatcher())`. CI reported as PASS. |
| `versionCode = 1`, `versionName = "1.0.0"` | MET | Code review: `app/build.gradle.kts:17-18` |
| PR description lists versions and sources | NOT YET VERIFIABLE | No PR is open yet. Check it when the PR is created. It must list coroutines 1.11.0 and lifecycle 2.11.0, each with a source link (Maven Central / Google Maven / GitHub releases). |
| `./gradlew ktlintCheck` passes | MET (as reported) | CI `check` depends on `ktlintCheck`. I did not run it myself because I'm limited to read-only commands. |

Scope checks:
- No file under `app/src/main` changes. The diff touches only `app/build.gradle.kts`, `gradle/libs.versions.toml` and the new test.
- Nothing from "Out of scope" was done: no KSP, no Hilt, no change to Java 11, and no explicit version bumps.
- Turbine was correctly left out because the smoke test doesn't use it.
- `ExampleUnitTest.kt` is untouched, as the task requires.

## Blocking findings
None.

## Escalations
None.

## Non-blocking findings
1. **Mixed Compose versions (the developer's question).** `lifecycle-runtime-compose` / `lifecycle-viewmodel-compose` 2.11.0 pull `androidx.compose.runtime` 1.11.x. Gradle resolves that above the BOM's 1.10.4, while `compose-ui` and `material3` stay on the BOM versions. My assessment is that this is **acceptable** for this task:
   - AndroidX aligns versions within each library group, not across Compose groups.
   - Using a newer `runtime` with an older `ui` is a supported, binary-compatible combination (newer runtime is backward compatible), and CI builds and tests pass.
   - The upgrade is transitive and comes from the lifecycle version the task says to reuse. It is not an explicit bump, so it doesn't break the "no other version bump" rule.
   - The other options would break the task: downgrading lifecycle-compose goes against "current stable versions", and bumping the BOM is an out-of-scope version bump.
   - Suggestions:
     - Mention the transitive `compose-runtime` 1.11.x upgrade in the PR description.
     - When a later task bumps the BOM, align it with the lifecycle release.
     - If you want proof, have CI run `./gradlew :app:dependencies --configuration debugRuntimeClasspath` once and record the resolved versions.
2. **`docs/tasks/README.md` still shows TASK-001 as `TODO`.** CLAUDE.md says "A task's PR updates its status in docs/tasks/README.md". Make sure this happens before or when the PR is opened, in whatever form the loop uses.
3. **Coroutines built with a newer Kotlin.** Coroutines 1.11.0 is built with Kotlin 2.2.20, and the project compiler is 2.2.10. They are in the same 2.2 metadata line and CI compiled fine, so no action is needed. It's worth noting for TASK-005 if the Kotlin version changes.
4. **Minor style points in `BuildSmokeTest.kt`, all optional.**
   - `assertEquals(true, completed)` could be `assertTrue(completed)`.
   - `kotlinx-coroutines-core` next to `-android` is redundant because `-android` already brings it in, but the task explicitly asks for both.

## Human checks on device
- The About dialog should now show "1.0.0 (1)". This is the only user-visible change.

## Verification performed
- **Source review:**
  - I read the task.md and the full diff `origin/ai-modernization...HEAD` (2 commits).
  - I read the full content of `gradle/libs.versions.toml`, `app/build.gradle.kts` and `app/src/test/java/kniezrec/com/flightinfo/BuildSmokeTest.kt`, and checked the task status line in `docs/tasks/README.md`.
  - The smoke test checks virtual time (`testScheduler.currentTime == 60_000`) and whether the launched coroutine completed. Without `kotlinx-coroutines-test` on the test classpath it would not compile, so it is not tautological. It runs on the JVM only.
- **CI result as reported:** PASS on bd2e396 (run 36254743908). I did not open the run logs.
- **Not verified:**
  - That 1.11.0 and 2.11.0 are the latest stable releases (I have no network lookup).
  - The resolved dependency graph, i.e. the actual Compose runtime version on the classpath.
  - The PR description, since the PR isn't open yet.
  - Whether anything works at runtime on a device.
