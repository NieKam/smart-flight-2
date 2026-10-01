# TASK-016 — Review iteration 1

## Result
**PASS.** I found no blocking findings and nothing that needs escalating.

## Acceptance criteria
| Criterion | Status | Verified by |
|---|---|---|
| Rotation (`ActivityScenario.recreate()`) keeps flight readings, nearby city, route, horizon calibration reference, open Settings overlay, open picker with query and selection, and the map expanded flag | Met (by source; CI reported green) | Each item has a test in `app/src/test/java/kniezrec/com/flightinfo/MainActivityCharacterizationTest.kt`. `recreationKeepsFlightReadingsAndNearbyCityVisible` and `recreationKeepsTheOpenCityPickerWithQueryAndSelection` already existed. This task adds `recreationKeepsTheOpenSettingsOverlay`, `recreationKeepsTheExpandedMap`, `recreationKeepsTheRestoredRoute` (also asserts it is the same `RouteViewModel`) and `recreationKeepsTheHorizonCalibrationReference` (a 20° sample after recreate reads as "20°", not level; also asserts the same `HorizonViewModel`). |
| All TASK-004 characterization scenarios pass | Met (CI as reported) | The characterization test diff only adds tests and fixes one comment. No scenario was removed or weakened. |
| Compose tests for each card container with fake ViewModels or fake repositories | Met | There is a container test for every card: GnssStatus, FlightParameters, Course, Horizon, NearbyCity, Route and Map. There are also `RoutePickerOverlayTest` and `SettingsOverlayTest`. They use real ViewModels over fake data sources (e.g. `FakeLocationDataSource`, the new `ListCityDataSource`), which the criterion allows. |
| MainActivity size and responsibilities as specified | Met | Code review of `MainActivity.kt` (153 lines, target "~150"). It keeps only: edge-to-edge and insets, the permission launcher and refresh on resume, the display-settings window effects, service start/stop (including the settings monitoring request), external intents, `AppVisibility` and `setContent { SmartFlightTheme { AppRoot(...) } }`. It builds no controllers, does no fan-out and calls no `getString` for state; snackbar strings moved to `stringResource` in `AppRoot`. |
| Rotating the device with portrait lock off keeps the dashboard state | HUMAN | On device. |

Scope items:
- **Dashboard:** `DashboardScreen` is in `dashboard/ui/` with header, card containers and a screen-level `RoutePickerOverlay` bound to `RoutePickerViewModel`.
- **Saved flags:** the Settings/About open flags use `rememberSaveable`. The map `expanded` flag uses `rememberSaveable` (`map/ui/MapCard.kt:81`).
- **GnssStatusScreen:** deleted. The GNSS card is `gnss/ui/GnssStatusCard.kt` and keeps the 180 ms Crossfade.
- **Packages:** UI files moved into `<feature>/ui`; only `ui/theme` remains. Tests moved with them.
- **Old `GnssStatusScreenTest`:** all 25 tests reappear under the new per-card, route, map and dashboard test files. I checked this name by name.
- **Parameter counts:** `DashboardScreen` 6, `AppRoot` 7, containers at most 4 (limit ~12).
- **Out of scope:** no visual changes, card order unchanged (checked by `cardsAreListedInTheirOrderBelowTheHeader`), and the permission gate is unchanged.

## Blocking findings
None.

## Escalations
None.

## Non-blocking findings
1. **`DashboardViewModel` (`dashboard/ui/DashboardViewModel.kt`) only passes `UnitSettingsRepository.units` through.**
   - It is acceptable: the task itself suggests "`UnitSettingsRepository` via a `DashboardViewModel`", the KDoc gives the reason (one source for three cards, so the card VMs stay about their own data), and it replaces the old coupling to `SettingsViewModel.state.units`.
   - Its test (`DashboardViewModelTest`) mostly tests the fake repository. It is not harmful, but it adds little.
2. **Small behavior change: open flags are dropped when permission is revoked.**
   - Before, `showUnitSettings` and `showAbout` lived in the Activity. If the flag was set, permission was revoked and then granted again, the overlay reappeared.
   - Now `DashboardScreen` leaves composition while permission is not granted, so its `rememberSaveable` flags are dropped and the dashboard comes back with both closed.
   - This is arguably better, and the developer disclosed it. It must be listed as a behavior change in the PR description, together with "overlays and the expanded map survive rotation and process recreation".
3. **GNSS "Try again" is still wired to `mapViewModel::retry`, but `GnssStatusState.Error` is never emitted.** This matches the previous wiring, so there is no regression. It is worth a follow-up to remove the state or give it a real source.
4. **`ui/theme/DashboardColors.kt` also holds `contrastRatio` and the permission page color, so the name is slightly misleading.** The KDoc says TASK-017 turns these into theme tokens.
5. **`docs/tasks/README.md` status is not updated yet.** The orchestrator will do this; CLAUDE.md says the task's PR must include it.
6. **The DashboardScreen KDoc documents only 2 of its 6 parameters.** This is cosmetic.

## Human checks on device
1. With portrait lock off, rotate while on the dashboard:
   - flight readings, the nearby city, the route and the horizon pitch reference stay;
   - the scroll position of the card list stays.
2. Open Settings, rotate: Settings is still open and system back returns to the dashboard.
3. Open About, rotate: the dialog is still open.
4. Open the route picker, type a query, select a city, rotate: the picker, query and selection remain, and Confirm saves.
5. Expand the map, rotate: the map is still expanded.
6. Open Settings (or About), revoke location permission in system settings, return and grant it again: the dashboard comes back with the overlays closed (intended behavior change).
7. With Settings open, check that system back closes Settings. With the picker open, check that back cancels the picker first.
8. Location services off: the GNSS card's "Open location settings" still opens system settings, and a snackbar appears if it cannot.
9. About → Send feedback and Rate still open the email app and the store page.
10. Process death: open Settings or expand the map, send the app to the background, kill the process (`adb shell am kill`), then reopen. The saved flags should be restored.

## Verification performed
- **Source review:**
  - `docs/tasks/016-dashboard-screen-slim-activity/task.md`;
  - the full diff `origin/ai-modernization...HEAD`, including renames;
  - full contents of `MainActivity.kt`, `AppRoot.kt`, `dashboard/ui/DashboardScreen.kt`, `dashboard/ui/DashboardViewModel.kt`, all `*CardContainer.kt`, `route/ui/RoutePickerOverlay.kt`, `settings/ui/SettingsOverlay.kt` and `gnss/ui/GnssStatusCard.kt`;
  - the deleted `GnssStatusScreen.kt` and the old MainActivity composition, compared with the new wiring callback by callback (every callback maps to the same VM method as before);
  - the tests: `DashboardScreenTest`, `DashboardViewModelTest`, `GnssStatusCardContainerTest`, `DashboardCardsUnitsTest`, and the recreation tests added to `MainActivityCharacterizationTest`.
- **CI result as reported:** PASS on 98e40db (run 36688072448). Round 1 failed only a `DashboardScreenTest` assertion about scroll position; that was fixed in the test only. I did not re-check the CI logs myself.
- **Not verified:**
  - Runtime behavior of any kind. I claim nothing works at runtime.
  - Process-death restoration (no test covers it).
  - Hilt resolving `SettingsViewModel` to the same Activity-scoped instance in the Activity (`by viewModels()`) and in `SettingsOverlay` (`hiltViewModel()`). This is the same pattern as before and not tested directly.
  - I did not compare with the original app in ~/smart-flight or its screenshots: this task adds no visual change, and parity 25 (rotation) only restores state retention.
  - I did not open `MapCardContainerTest`, `RouteCardContainerTest`, `RoutePickerOverlayTest` and `SettingsOverlayTest` beyond their test names.
