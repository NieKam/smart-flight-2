# TASK-005 — Introduce Hilt (KSP) as a dedicated DI step

## Goal
Add Hilt with KSP, an `Application` class, entry points on the Activity and the service, and modules for the objects that are application-scoped today. Replace ad-hoc construction of those objects with injection. No behavior change.

## Context
- CLAUDE.md: "DI: Hilt with KSP. Introduce it as a dedicated migration step after the review, not mixed with other changes. Verify Hilt compatibility with AGP 9 / Kotlin 2.2 before adding it."
- Architecture review F5 (GAP, HIGH): no DI and no Application class (`AndroidManifest.xml:12-20` has no `android:name`); all wiring is `by lazy` in `MainActivity.kt:444-593` plus ad-hoc construction in `LocationForegroundService.kt:141,200-216`.
- Architecture review section 3 "DI inventory" lists every construction site and a suggested scope (S = singleton, A = activity-bound, VM = ViewModel later).
- Architecture review F9: `AndroidNearbyCityRepository` is instantiated twice (`MainActivity.kt:536,548`).
- Architecture review K8: keep clock injection (`clock: () -> Instant` in `NearbyCityController.kt:76`, `RouteController.kt:17`); it maps to a Hilt-provided clock.
- Architecture review "Not verified" item 1: KSP and Hilt compatibility with AGP 9 built-in Kotlin was not checked. Observed: `app/build.gradle.kts:1-5` applies only `com.android.application`, `org.jetbrains.kotlin.plugin.compose` and ktlint (AGP `9.4.0`, Kotlin `2.2.10`); there is no `org.jetbrains.kotlin.android` plugin, i.e. the project uses AGP 9 built-in Kotlin. The catalog has no `ksp` plugin yet (TASK-001 deliberately left KSP to this task).
- Human decision: this task first verifies Hilt + KSP with AGP 9 in CI. If that fails with built-in Kotlin, adding the `org.jetbrains.kotlin.android` plugin is allowed (kapt is still not allowed).

## Dependencies
- TASK-001, TASK-004.

## Scope
- Step 1 — compatibility check (first commit(s) of the PR, pushed and checked in CI before the rest of the work):
  - Look up current stable versions of KSP (`com.google.devtools.ksp`), Dagger/Hilt (`com.google.dagger:hilt-android`, Hilt Gradle plugin) and `androidx.hilt`. Record versions, source links and any statement about AGP 9 / built-in Kotlin support from the release notes in the PR description. Do not assume versions.
  - Add the KSP plugin (catalog + `apply false` in the root `build.gradle.kts` if that is the project convention + applied in `app/build.gradle.kts`), the Hilt Gradle plugin, `hilt-android` and `ksp(hilt-compiler)`, plus an empty `@HiltAndroidApp` `SmartFlightApplication` registered in the manifest. Push and check that CI (`assembleDebug`, `testDebugUnitTest`) is green.
  - If CI fails because KSP or Hilt does not support AGP 9 built-in Kotlin: apply `org.jetbrains.kotlin.android` (via the catalog), plus whatever AGP 9 requires to opt out of built-in Kotlin (assumption: a Gradle property such as `android.builtInKotlin=false`; check the AGP 9 release notes). Record the failure log excerpt, the fix and the source in the PR description, and add a line to the README "Planner decisions" section stating the Kotlin plugin setup. Do not use kapt.
  - Only when step 1 is green, continue with the rest of the scope.
- Catalog + build: Hilt Gradle plugin, `hilt-android`, `hilt-compiler` via `ksp(...)`; `androidx.hilt:hilt-lifecycle-viewmodel-compose` (or the current artifact providing `hiltViewModel()` for Compose; verify name and version) so later tasks can call `hiltViewModel()`. Optional: `hilt-android-testing` + `kspTest` if you convert any test to `@HiltAndroidTest`.
- `SmartFlightApplication` annotated `@HiltAndroidApp`, registered in the manifest (`android:name`).
- `@AndroidEntryPoint` on `MainActivity` and `LocationForegroundService`.
- Modules (`di/` package or per-feature `data/di`, pick one and state it in the PR):
  - System services: `LocationManager`, `SensorManager`, `PackageManager`, `NotificationManager` (S).
  - Named `SharedPreferences` for the existing files, with the SAME names: `display_behavior`, `display_units`, `monitoring_behavior` (`BackgroundNotificationPreferencesStore.PREFERENCES_NAME`), `location_permission`, `route`. Use qualifiers.
  - `DisplayPreferencesStore`, `UnitPreferencesStore`, `BackgroundNotificationPreferencesStore` as singletons (one instance shared by Activity and service).
  - Dispatchers: qualifiers `@IoDispatcher`, `@DefaultDispatcher`, `@MainDispatcher` providing `CoroutineDispatcher`.
  - `@ApplicationScope CoroutineScope` = `CoroutineScope(SupervisorJob() + Dispatchers.Default)` (used by repositories from TASK-006 onward for `shareIn`/`stateIn`).
  - Clock: provide `java.time.Clock` (or keep `() -> Instant`; pick one and use it in both controllers).
  - `NearbyCityRepository` bound to a single `AndroidNearbyCityRepository` (S) used by both `RouteController` and `NearbyCityController`.
  - `MapArchiveRepository` (S).
- `MainActivity`: replace the `getSharedPreferences(...)`/store/repository/`MapArchiveRepository` `by lazy` blocks with `@Inject` fields. Controllers stay constructed in the Activity for now (they become ViewModels in TASK-009 to TASK-015). `AndroidOrientationSource(this, …)` stays Activity-constructed: it needs an Activity context for `context.display` (architecture review DI inventory, line 555 note).
- `LocationForegroundService`: inject `BackgroundNotificationPreferencesStore` and `LocationManager` instead of building them (`:133-143`). Keep the `protected open` test seams (`:133,140,183,187,200`) so `LocationForegroundServiceTest` subclasses still work.

## Out of scope
- ViewModels, Flow, repositories (TASK-006+).
- Removing `BackgroundMonitoringBridge` (TASK-008).
- osmdroid `Configuration` init in the Application (TASK-014).

## Requirements
Required:
- Behavior identical; TASK-004 characterization tests pass unchanged (they may need `@Config(application = SmartFlightApplication::class)` only if Robolectric does not pick the manifest application automatically).
- Preference file names and keys unchanged.
- One `AndroidNearbyCityRepository` instance in the process.
- No `kapt`. The Hilt compiler runs through KSP.
- Prefer AGP 9 built-in Kotlin. Adding `org.jetbrains.kotlin.android` is approved by the human only as the fallback described in step 1, and only if CI shows built-in Kotlin does not work; the PR must show the evidence.

Recommendations:
- Keep modules small and close to the feature they serve; avoid one giant `AppModule`.

## Acceptance criteria
- [ ] PR description records KSP/Hilt/androidx.hilt versions with sources, the CI result of step 1 and, if used, why `org.jetbrains.kotlin.android` was needed — verified by: code review
- [ ] `@HiltAndroidApp` Application registered; Activity and service are `@AndroidEntryPoint` — verified by: code review
- [ ] Build uses KSP for Hilt; no kapt anywhere — verified by: CI build + code review
- [ ] Single shared instances of the preference stores and city repository — verified by: CI unit test (Robolectric test obtaining the Hilt component or an `EntryPoint` and asserting same instance) or code review of `@Singleton` scoping
- [ ] TASK-004 characterization tests and all existing unit tests pass — verified by: CI unit test
- [ ] App launches and the dashboard receives GPS data on a device — verified by: HUMAN on device (smoke test)

## Tests to add or update
- Update `LocationForegroundServiceTest` / `LocationForegroundServiceEligibilityTest` only if Hilt injection in the test subclass requires it (e.g. use the real application so injection succeeds).
- Optional: a Robolectric test asserting that `DisplayPreferencesStore` injected into the Activity and service is the same instance.

## Risks and edge cases
- `@AndroidEntryPoint` on an `open` service subclassed by tests: Hilt generates `Hilt_LocationForegroundService`; the test subclass inherits injection. Verify under Robolectric.
- Robolectric + `@HiltAndroidApp`: the real application is used unless configured otherwise; it must not start heavy work in `onCreate`.
- DI-bound `SharedPreferences` read on first injection happen on the main thread, same as today.
- KSP applied with the Kotlin 2.2.x compiler: pick the KSP release whose compatibility table lists Kotlin 2.2.x (KSP 2.x releases are no longer tied one-to-one to a Kotlin version; check).
- Switching to `org.jetbrains.kotlin.android` (fallback) changes how Kotlin is configured for the whole module (e.g. `kotlin { jvmToolchain / compilerOptions }` blocks); keep `compileOptions` Java 11 and the Compose compiler plugin unchanged.
