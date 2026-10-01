# CLAUDE.md

## Project
Smart Flight: offline-capable Android app (Jetpack Compose, single `:app` module) showing GNSS
satellite status, flight parameters (altitude, speed, pressure), course, horizon, nearby cities
and an offline osmdroid map. Package `kniezrec.com.flightinfo`. minSdk 31, compile/target SDK 37,
Kotlin 2.2, AGP 9.

This code is a fully AI-generated rewrite and is the SUBJECT OF REVIEW, not a reference for
how things should be done.

## Sources of truth
- `~/smart-flight` — clone of the original app (github.com/NieKam/smart-flight).
  This is the behavioral reference. Read-only: never modify it.
- `~/smart-flight/promo/` — screenshots of the original app. Open and analyze the images
  themselves; do not rely on file names alone.

## Environment (critical)
- Low-resource machine WITHOUT Android SDK. Never run assemble*, test*, lint*, connected*
  or any Gradle task other than ktlint locally.
- Local verification: `./gradlew ktlintCheck` and `./gradlew ktlintFormat` only.
- Build + unit tests run in GitHub Actions on every push (~2 min):
  - `gh run list --branch "$(git branch --show-current)" --limit 3`
  - `gh run watch <run-id>`
  - `gh run view <run-id> --log-failed`
  - `gh run download <run-id> -n test-reports` for full test reports
- Never claim code compiles or tests pass until CI for that exact commit is green.

## Git workflow
- Base branch: `ai-modernization`. It changes only through PRs merged by the human.
  Never commit or push to it directly. Never merge PRs.
- Before starting any work: `git fetch origin`, then create a new branch from
  `origin/ai-modernization` (never from the local branch, which may be stale).
- Branch names: `plan/<slug>` for review and planning, `task/<NNN>-<slug>` for tasks.
- When done: push the branch and open a PR with `gh pr create --base ai-modernization`.
- One task = one branch = one PR. Work on tasks sequentially; start a task only when
  all its dependencies are merged into `origin/ai-modernization`.

## Workflow rules
- Small, focused commits. A task's PR is ready only when CI is green on its last commit.
- Change behavior only where the task explicitly requires it (e.g. restoring behavior
  of the original app); list every behavior change in the PR description.
- Before refactoring an area, ensure unit tests pin its current behavior.

## Tasks
- Review reports: `docs/review/`.
- Tasks: `docs/tasks/<NNN>-<slug>/task.md`; index with order, dependencies and status:
  `docs/tasks/README.md`.
- A task's PR updates its status in `docs/tasks/README.md`.

## Current architecture (descriptive only — under review)
Feature packages under `app/src/main/java/kniezrec/com/flightinfo/` (gnss, flight, course,
horizon, nearby, route, map, monitoring, permission, display, displayunits, orientation, about),
Compose UI in `ui/<feature>/`. No ViewModel, no DI framework.
- `*State` — sealed/data UI state types.
- `*Controller` — plain Kotlin logic, receives a `*Platform` interface and an
  `onStateChanged` callback; free of Android types, tested on JVM with fakes.
- `Android*Platform` / `*PlatformAdapters` — Android implementations of those interfaces.
- `*Preferences` / `*PreferencesStore` — persisted settings.
- `MainActivity` is the composition root: builds platforms/controllers, keeps state in
  `mutableStateOf`, wires permissions and lifecycle.
- `monitoring/`: `LocationForegroundService`, process-local singleton
  `BackgroundMonitoringBridge` (generation tokens), `*MonitoringSession` classes.
- Assets: `databases/cities_info.db`, `osmdroid.zip` (copied out by `map/MapArchiveCopier`).
- Tests: `app/src/test` (JUnit4 + Robolectric, fakes), `app/src/androidTest/.../ui/` (Compose).

## Target architecture
Goal: scalable and easy to understand. Clean Architecture principles, applied pragmatically.
- Layers: ui (Compose + ViewModel) → domain (optional) → data (repositories + data sources
  wrapping Platform APIs).
- Dependency rule: inner layers never depend on outer ones; domain has no Android imports.
- Use cases only when logic is reused across ViewModels or combines several repositories.
  No pass-through use cases.
- Repositories expose Flow / suspend functions; single source of truth per data type.
- No mapper layers unless the models genuinely differ.
- Package by feature, layers inside each feature. Modularization (core/feature modules):
  propose after review, with justification.
- Every abstraction must justify its existence; prefer fewer, clearer types.

## Target standards
- UI state held in ViewModels, exposed as `StateFlow`, collected with
  `collectAsStateWithLifecycle`; must survive configuration changes.
- Coroutines: structured concurrency, no `GlobalScope`, injectable dispatchers;
  Android callbacks wrapped with `callbackFlow`.
- Keep Android types out of logic classes (keep the Platform-interface idea).
- DI: Hilt with KSP. Introduce it as a dedicated migration step after the review, not mixed
  with other changes. Verify Hilt compatibility with AGP 9 / Kotlin 2.2 before adding it.
- KSP instead of kapt; Gradle version catalog.
- Reference: Android "Guide to app architecture" and Now in Android.
- Check current library versions instead of assuming them.