# TASK-009: Review iteration 1

## Result
**PASS.** I found no blocking findings and nothing that needs escalation.

## Acceptance criteria
| criterion | status | verified by |
|---|---|---|
| ViewModels expose `StateFlow`; Activity has no GNSS/flight/pressure fields | MET | Source review. `gnssState`, `flightParametersState` and `pressureMillibars` are removed from `MainActivity`. Both ViewModels are `@HiltViewModel`, use `stateIn(viewModelScope, WhileSubscribed(5_000), …)`, and are collected with `collectAsStateWithLifecycle()` through `hiltViewModel()` in `setContent`. |
| Ported tests for vertical speed, speed conversion, first readable reading, pressure merge, GNSS empty/available | MET | Every case from the old controller tests maps to a new test in `FlightReadingsTest`, `FlightParametersViewModelTest`, `GnssStatusViewModelTest` or `PressureDataSourceTest`. The old `stop()`/`start()` cases became "restart after timeout" and "location off". The old test "accepted fixes are forwarded" now lives in the Activity fan-out and is covered by the characterization scenarios. CI is green as reported (run 36552457442 on 2dc7a60). |
| `PressureDataSource` registers/unregisters with collection | MET | `PressureDataSourceTest` checks register and unregister on cancel with `ShadowSensorManager`, invalid values, no sensor, refused registration, and late events after cancel. `FlightParametersViewModelTest` checks that the sensor is registered only while the state is collected. CI is green as reported. |
| TASK-004 characterization scenarios 3, 4, 5, 8 pass | MET | Scenario 5 now waits for the barometer listener. Scenario 8 was rewritten on purpose, which the task allows (see B1). CI is green as reported. |
| Rotation keeps the last readings visible without flicker to "Waiting" | MET in CI; HUMAN on device optional | `recreationKeepsFlightReadingsVisible` checks the same ViewModel instance, `Readings` state, 36.0 km/h shown and the readings layout. There is no flicker by construction: the upstream is not restarted within 5 s and `collectAsStateWithLifecycle` starts from `state.value`. The test checks only the end state, not intermediate frames. |

## Blocking findings
None.

## Escalations
None.

## Verification of the specific points

**A. The round-1 test fixes are genuine test defects, not production bugs.**
- **Recreation test:** `flight_parameters_waiting` and `nearby_city_waiting` are both "Waiting for GPS position…" (`strings.xml:22,90`). Nearby-city state still lives in the Activity and is reset on recreation, so the old "count 0" assertion was wrong. The new checks (same ViewModel, `Readings`, 36.0 km/h shown, "Vertical speed" row present) test the intended behavior.
- **Flight off/on test:** `LocationRepository.fixes` has `replay = 0`, and the fake drops fixes when nothing is registered. Re-registering after "on" takes a dispatcher turn through `flatMapLatest`. On a device a fix cannot arrive before its own registration, so adding `runCurrent()` and asserting the active registration is a correct ordering fix.

**B. Developer-reported behavior changes**
1. **Cards collected while STARTED; pause no longer resets them; only a stop longer than 5 s restarts from Waiting.** This is sanctioned. The task's Risks section says a short pause will no longer reset the card, asks the developer to "Decide which behavior to keep, update scenario 8 and list the change in the PR", and calls the >5 s restart "close enough". It also matches the original app: `~/smart-flight/.../services/SensorService.kt` registers sensors in a bound-service onStart/onStop scope. The Requirements line "reset after onPause/onResume (as today)" conflicts literally, but the Risks section and the mandated `WhileSubscribed(5_000)` resolve that. It must be listed in the PR.
2. **Barometer tied to collection (STARTED + 5 s); GPS stays up to 5 s after stop.** Required: the task says "registered only while the Flight card state is collected" and mandates `WhileSubscribed(5_000)`. The GPS registration is shared with the foreground service, so the extra 5 s adds no separate registration while the service runs.
3. **Location back on recovers immediately.** Not explicitly sanctioned, but it follows naturally from reactive flows, moves toward the original app, and does not go into TASK-019's disabled/error states. Acceptable; must be listed in the PR (see N1).
4. **Registration failure isolated per card.** Before, a failure in one of the three sibling `launch`es cancelled all of them. Acceptable; list it in the PR. One side effect is described in N2.
5. **Stale readings may flash after a >5 s stop.** Real, but likely at most one frame. See N3.

**C. `confirmedLocationEnabled`** (`LocationRepository.kt:65-68`). It maps `enabled || dataSource.isLocationEnabled()` and then applies `distinctUntilChanged`. A stale "off" is overridden by a fresh read. A stale "on" is corrected by the data source's initial `trySend(isLocationEnabled)` after the receiver registers (`AndroidLocationDataSource.kt:88`). That is the same protection TASK-008 had, applied the same way for all three consumers (Activity `collectLocation`, Flight VM, GNSS VM). `LocationRepositoryTest` covers the stale-off replay and the off/on sequence. The Activity's `collectLatest` stops the fan-out while off, as before. I see no consumer-specific regression.

**D. Out-of-scope notes are accurate.**
- `GnssStatusState.Error` is never produced in main code (only referenced in `GnssStatusScreen.kt:312`), so the GNSS `onRetry` is unreachable today. Correctly deferred to TASK-019.
- Leaving the `FlightParametersCard` move to TASK-016 is allowed by the task; state it in the PR.
- Real-time polling in the characterization tests is pre-existing.

**Out-of-scope check:** no pressure-before-fix change, no vertical-speed smoothing, no GPS-disabled states, no satellite chart. `ForegroundCourseObservationCoordinator` was adapted minimally: the flight controller was removed and `stopForegroundOnly` merged into `stop`, which is equivalent now.

## Non-blocking findings
- **N1 (PR description):** list every behavior change from B1–B5 and the `FlightParametersCard` decision in the PR. Also update `docs/tasks/README.md` line 41 (TASK-009 is still `TODO`); CLAUDE.md requires the task PR to do this.
- **N2** (`FlightParametersViewModel.kt:46-49`, `GnssStatusViewModel.kt:40-44`): after a `LocationRegistrationException`, the inner flow completes. The ViewModel registers again only when the location switch changes or after a stop longer than 5 s. Before, every resume retried, and the Activity fan-out still does. So after a failure, a short pause and resume brings the fan-out back but not the cards. This is rare (it needs a registration failure without process death) and error states belong to TASK-019. A retry hook there would close it.
- **N3 (B5):** `WhileSubscribed(5_000)` keeps the last value forever by default, so after a >5 s stop `collectAsStateWithLifecycle` starts from the old readings or satellites until the restarted upstream emits `Waiting`. `SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0)` would reset the cached value to `Waiting` when observation stops, without affecting rotation. Optional.
- **N4:** `PressureDataSource.hasPressureSensor()` is required by the task and implemented, but no production code uses it yet (TASK-020 may need it). Fine as is.
- **N5:** the recreation test does not observe intermediate frames, so "without flicker" is argued from the design rather than asserted. Acceptable given the design.

## Human checks on device
1. Rotate with readings shown: flight and GNSS cards keep their values with no flash of "Waiting" (optional per the task).
2. Pull down the notification shade or open a dialog (pause without stop): the flight card keeps updating and does not reset.
3. Go home for more than 5 s and come back: the flight card shows "Waiting for GPS position…" and then new readings with no vertical speed on the first fix. Watch for a brief flash of the old readings (N3).
4. Turn location off in quick settings while the dashboard is visible: the flight card shows "Waiting"; the GNSS card keeps its last report. Turn it back on: both recover without leaving the app.
5. On a device with a barometer: pressure appears only after the first GPS reading; the sensor stops about 5 s after leaving the app.

## Verification performed
- **Source review:** full diff `origin/ai-modernization...HEAD` (main and test). I read the full new `FlightReadings.kt`, `PressureDataSource.kt`, `FlightModule.kt`, both ViewModels, `LocationRepository.kt` and the relevant parts of `MainActivity.kt`, and compared them with the base `MainActivity` lifecycle (`onResume`/`onPause`/`refreshPermissionState`/`collectLocation`). I checked the round-1 recreation test at 02f1485 against the current version and the shared string resources. I checked the original app's sensor lifecycle in `~/smart-flight`.
- **CI:** PASS on 2dc7a60 as reported by the main session (run 36552457442). I did not inspect the CI logs myself.
- **Not verified:** runtime behavior on a device, visual flicker, actual sensor or GPS registration timing on hardware, and the Hilt graph beyond "CI compiled". I opened no screenshots because there is no UI layout change.

Relevant files:
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/flight/ui/FlightParametersViewModel.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/gnss/ui/GnssStatusViewModel.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/flight/data/PressureDataSource.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/flight/FlightReadings.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/location/data/LocationRepository.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt
- /home/ai-dev/smart-flight-2-modern/app/src/test/java/kniezrec/com/flightinfo/MainActivityCharacterizationTest.kt
- /home/ai-dev/smart-flight-2-modern/docs/tasks/README.md
