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

## Environment (critical)
- Low-resource machine WITHOUT Android SDK. Never run assemble*, test*, lint*, connected*
  or any Gradle task other than ktlint locally.
- Local verification: `./gradlew ktlintCheck` and `./gradlew ktlintFormat` only.
- Build + unit tests run in GitHub Actions on every push (~2 min):
  - `gh run list --branch ai-modernization --limit 3`
  - `gh run watch`
  - `gh run view --log-failed`
  - `gh run download <run-id> -n test-reports` for full test reports
- Never claim code compiles or tests pass until CI for that exact commit is green.

## Workflow rules
- Work only on branch `ai-modernization`. Never push to `main`.
- One logical change = one commit = one CI run. Small, reviewable diffs.
- Preserve behavior unless a change is explicitly agreed; list any behavior change
  in the commit message.
- Before refactoring an area, ensure unit tests pin its current behavior.

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