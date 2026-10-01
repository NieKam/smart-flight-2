# TASK-015 — Review iteration 1

## Result
PASS

## Acceptance criteria
| criterion | status | verified by |
|---|---|---|
| `SettingsViewModelTest`: each setter persists and emits | Met (source). CI reported green | `app/src/test/java/kniezrec/com/flightinfo/settings/ui/SettingsViewModelTest.kt`. Each of `setUnits`, `setDisplay` and `setShowBackgroundNotification` is checked against the fake repository value and against `state.value`. The monitoring-request event is also covered: it fires on enable, does not fire on disable, and is not replayed to a later collector. |
| Permission state tests ported from `LocationPermissionStateControllerTest` | Met (source). CI reported green | `permission/ui/LocationPermissionViewModelTest.kt` ports both original tests (history survives recreation; refresh after returning from Settings) and adds tests for first launch, rationale, coarse-only, the announce flag and `snapshot()`. The old controller and its test are deleted, as the task allows. |
| `UnitSettingsScreenTest`, `AboutDialogTest`, `PermissionOnboardingScreenTest` pass | Met per the CI result you reported (files unchanged) | CI run 36681942306 on d097f24, as you reported it |
| TASK-004 scenarios 1, 2, 6 pass | Met per the CI result you reported | Scenarios 1, 2 and 6 in `MainActivityCharacterizationTest.kt` are unchanged. A new scenario 6 test covers the monitoring setting. That test clears the recorded service starts before switching the notification on, so it would fail if the event path broke. |

## Scope compliance
- SettingsViewModel: `StateFlow<SettingsUiState>` combines the three repositories. It is seeded with their current `.value`, so the first frame shows no default flash. Setters run in `viewModelScope`. The service still starts on enable: the Activity collects `backgroundMonitoringRequests` under `repeatOnLifecycle(RESUMED)` and checks for Granted, which matches the old `isForeground && Granted` check. The event is sent after the value is persisted, as before.
- About: the simpler option was chosen. `AppVersionProvider` is `@Singleton` and gets its `PackageManager` from the existing `SystemServicesModule` and the `@ApplicationContext`. The nullable Context and lambda default (F16) are gone. `formatAppVersion` is still pure, and `appVersion(PackageInfo?)` is a pure helper that is tested.
- LocationPermissionViewModel: it has `state` and `announceChange` StateFlows. `refresh(snapshot, announceChange)` uses the pure `locationPermissionState`, and `onRequestLaunched()` records history before the launcher is called. The Activity reads the rationale on every refresh (onCreate, onResume, permission result) through `AndroidFineLocationPermissionPlatform(this)`. It is never cached in a singleton, which covers the task's risk item.
- MainActivity: it keeps the launcher, `openAppSettings`, `openLocationSettings` and external intent launching. The settings write lambdas, `permissionState`, `announcementVersion`, `isForeground` and the three settings/permission injections are removed.
- Out of scope was respected. Onboarding still gates Settings/About (TASK-024), POST_NOTIFICATIONS is untouched (TASK-025), and `showUnitSettings`/`showAbout` remain plain `mutableStateOf` (overlay saveability is TASK-016).

## Blocking findings
None.

## Escalations
None.

## Non-blocking findings
1. **`internal val` repositories on the ViewModels, only for tests** (`settings/ui/SettingsViewModel.kt:38-40`, `permission/ui/LocationPermissionViewModel.kt:26`). Acceptable. The widening stays inside the module, is documented in KDoc, and keeps HiltSingletonScopeTest checking what it checked before (repositories shared across separately injected consumers). A Hilt `@EntryPoint` in the test would avoid the widening, but that is a preference, not a defect.
2. **Behavior changes you listed are acceptable and consistent with the task:**
   - The announce flag now lives in the ViewModel, so it survives rotation. Before, `announcementVersion` reset on recreation. The task asks for an `announceChange` flag in the state holder, and the effect is limited to accessibility live-region announcements after a permission result.
   - Settings writes now run in `viewModelScope` instead of `lifecycleScope`. That is an improvement: a write started just before a configuration change is no longer cancelled.
   - Both must be listed in the PR description, as CLAUDE.md requires.
3. **`FineLocationPermissionPlatform` kept (K1 KEEP).** Correct. It is still the seam that `snapshot()` reads and that the test fake implements.
4. **`PermissionSnapshot` lives in `LocationPermissionPlatform.kt`, next to the platform interfaces.** The file name no longer matches the old controller file, but that is fine.
5. **`SettingsUiState.showBackgroundNotification` defaults to `true`.** The default is never used at runtime because the initial value comes from the repositories. It only matters to anyone who builds the state directly.
6. **`docs/tasks/README.md` status is still TODO.** CLAUDE.md says the task PR updates it. You said the orchestrator does this after review; make sure it is in the PR before merge.

## Human checks on device
1. **First launch, no permission.** Onboarding shows Requestable.
   - Deny once: the rationale state is still Requestable.
   - Deny twice: Settings required. Open App Settings, grant, return: the dashboard appears and the foreground service notification starts.
2. **Rotate on the onboarding screen after a denial.** The state stays correct. After the next permission result, the TalkBack announcement still happens (the flag is now kept across rotation).
3. **Kill the process after one denial and relaunch.** The request history persists, so the state is correct and not reset to a first launch.
4. **Settings screen.** Change units, keep screen on, portrait lock and larger map zoom. Each applies immediately, and the unit change shows up in the dashboard cards.
5. **Settings screen, background notification.** Switch it off and on while the app is visible with permission granted. Monitoring or the service restarts on enable, and the service reflects disable.
6. **Settings screen, rotation.** Rotate with Settings open and change a value right before or after the rotation. The value persists.
7. **About.** It shows the same version text as before (name and code). Send feedback and Rate open the expected apps, and a snackbar or fallback appears where no handler exists.
8. **Revoke location from system settings while the app is in the background, then return.** Onboarding shows and the service stops.

## Verification performed
- **Source review:**
  - `git diff origin/ai-modernization...HEAD` (5 commits, 13 files).
  - Full contents of `MainActivity.kt`, `SettingsViewModel.kt`, `LocationPermissionViewModel.kt`, `AboutPlatform.kt`, `AndroidFineLocationPermissionPlatform.kt`, `LocationPermissionPlatform.kt`, and all changed or new tests.
  - Checked that `PackageManager` is provided by `di/SystemServicesModule.kt:40`.
  - Checked that no references to `AndroidAppVersionProvider`, `LocationPermissionStateController` or `isForeground` remain in main sources.
  - Checked the TASK-004 scenario 1/2/6 tests are present.
- **CI result as reported by the caller:** PASS on d097f24, run 36681942306 (round 1 failed compiling HiltSingletonScopeTest and was fixed in d097f24). I did not check the CI logs or test reports myself.
- **Not verified:**
  - Runtime behavior on a device, including the permission dialog flow, rationale behavior, service start and stop, and TalkBack announcements.
  - Whether the three UI tests actually ran in CI, beyond the reported overall green result.
  - I did not compare against the original app in ~/smart-flight or the promo screenshots. This task is a refactor with no intended user-visible change, apart from the two listed behavior changes.
