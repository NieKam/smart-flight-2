# TASK-010 — Review iteration 1

## Result
**PASS.** I found no blocking findings and nothing that needs escalating.

One task requirement is still open on the branch, and the orchestrator has said it will do it at finish: the landscape sign defect must be recorded in `docs/tasks/README.md` open questions and in the PR description. The README status update is also still to do. The PASS depends on both of these being done before the PR is opened.

## Acceptance criteria
| criterion | status | verified by |
|---|---|---|
| Single shared sensor registration (2 collectors → 1 listener; 0 after both cancel) | MET | Source review of `AndroidOrientationDataSource` (callbackFlow + `shareRethrowingIn` → `WhileSubscribed(replayExpirationMillis = 0)`, replay 0). Tested in `OrientationDataSourceTest."two collectors share one sensor listener, released when both stop"` with Robolectric `ShadowSensorManager`. CI green as reported. |
| Course/Horizon VM tests port every applicable case of the four deleted tests | MET | Matched case by case. The CourseController tests for cardinals and normalization moved to `CourseStateTest`. The unavailable, failed-registration, bearing, pending-bearing and retry/no-accumulation cases moved to `CourseViewModelTest`. The HorizonController mapping test moved to `HorizonStateTest`. Calibrate, unavailable/failure/retry/stale and replacement-session moved to `HorizonViewModelTest`. The adapters test's four-rotation table moved to `DisplayRelativeOrientationTest.deviceMatrixIsRemappedForEachDisplayRotation` with identical expected values. Share-one-subscription moved to `OrientationDataSourceTest`. Queued-old-event moved to "a sample sent after the collection ended" plus the late-registration VM tests. Coordinator "start clears old compass data" is covered by the retry and restart tests. Coordinator "stop ends compass" is covered by "sensor registered only while collected". |
| Rotation-matrix tests for all four display rotations | MET | `DisplayRelativeOrientationTest` covers flat facing north, nose-up 10° and roll-right 10° in each rotation, and asserts heading, pitch and roll. |
| Deleted types are gone | MET | `OrientationSource`, `AndroidOrientationSource`, `OrientationEventDispatcher`, `CapturedOrientationEvent`, the `Shared*OrientationPlatform` adapters, `CourseController`, `HorizonController`, `ForegroundCourseObservationCoordinator`, `onDisplayRotationChanged`, `MainActivity.onConfigurationChanged` and `DisplayRotation.fromSurfaceRotation` are all gone. A grep of `app/src` finds no references. |
| Compass and horizon respond correctly in portrait and landscape | HUMAN on device | See the caveat under Human checks: landscape is known to be wrong, and this task pins that rather than fixing it. |
| Requirement: record the sign defect in the PR and in README open questions | PENDING (orchestrator) | Not on the branch. The orchestrator must add it. |

## Blocking findings
None.

## Escalations
None.

## Verification of the specific points

**A. Landscape sign finding: the developer's analysis is correct, and pinning it is what the task asks for.**
- **What Android does.** In `SensorManager.remapCoordinateSystem`, output column `idx(X)` is ±input column 0 and output column `idx(Y)` is ±input column 1.
  - The standard `ROTATION_90` call is `(AXIS_Y, AXIS_MINUS_X)`, which gives out col0 = −in col1 and out col1 = +in col0.
  - The standard `ROTATION_270` call is `(AXIS_MINUS_Y, AXIS_X)`, which gives out col0 = +in col1 and out col1 = −in col0.
- **What the code does.** `DisplayRelativeOrientation.kt:25-27` maps Landscape to `2 to -1`, which gives col0 = +col1 and col1 = −col0. That is Android's `ROTATION_270` mapping. ReverseLandscape (`-2 to 1`) is Android's `ROTATION_90` mapping. So the two landscape mappings are swapped.
- **What that does to the output.** Using the wrong one is the right mapping with col0 and col1 both negated, which is a 180° turn about the display Z axis:
  - heading `atan2(R[1], R[4])` shifts by 180°;
  - pitch `asin(−R[7])` flips sign;
  - roll `atan2(−R[6], R[8])` flips sign.
- **I checked this physically too.** With the device turned CCW (`ROTATION_90`), display-x is device −Y and display-y is device +X. That requires R' col0 = −R col1 and R' col1 = +R col0, which is Android's mapping. The test's `deviceInDisplay` poses use the same geometry.
- **The tests.** The test comments (heading 0, pitch −10, roll +10 expected) are correct. The code is unchanged from before this task; it was moved verbatim.
- **Task compliance.** task.md says: "If a sign turns out wrong, do NOT fix it here: record it in the PR and in `docs/tasks/README.md` open questions". TASK-028 then says "fix it here with the failing test turned green". Pinning the current values plus deferring therefore matches the task. The README entry and PR note are still owed, as above.
- **Developer note on the pitch label: correct.** Nose-up gives negative pitch (getOrientation convention). `HorizonCard.kt:133` maps positive to "up", so nose-up reads "N° down". The visual is right, though: `verticalOffsetFraction = −pitch/30·0.35` moves the horizon down for nose-up.
- **Developer note on roll: also correct.** Roll-right gives +roll, and `rotationZ = +roll` turns the sky/ground layer clockwise. The screen itself has already turned clockwise, so to stay level with the real horizon the layer should turn counter-clockwise. The picture therefore tilts the wrong way, about 2× off the real horizon.
- **These two are old, not new.** Both are in UI mapping this task did not touch, and they apply in portrait too. They are not caused by TASK-010. They should go into the same README open question for TASK-028; see N1.

**B. Behavior changes**
1. **STARTED + `WhileSubscribed(5_000)` instead of RESUMED (sensor stays up to 5 s after leaving): allowed.** task.md "Target conventions" explicitly requires `stateIn(viewModelScope, WhileSubscribed(5_000), …)` and `collectAsStateWithLifecycle()`. It matches the TASK-009 precedent. Needs a line in the PR.
2. **Rotation and pauses under 5 s keep the horizon reference and course state: acceptable.**
   - There is a tension inside the task: "reference cleared when collection restarts (preserves today's reset-on-resume)" versus the required `WhileSubscribed(5_000)`. The developer resolved it as the task's own wording implies: the reset happens when *collection* restarts.
   - It also follows CLAUDE.md ("must survive configuration changes") and the TASK-009 notes on the 5 s window.
   - It moves toward the original app: `HorizonCardViewPresenter` keeps `mPitchStartPosition` across pause, and TASK-028 restores exactly that.
   - Reset after pauses longer than 5 s is pinned by tests. Must be listed in the PR.
3. **GNSS retry no longer restarts Course/Horizon: acceptable.** This is the F16 decoupling direction ("lifecycle gating belongs to the collector"), and the coordinator is deleted as the task requires. The old side effect was a card reset and horizon re-level on a GNSS retry. List it in the PR.
4. **Retry no longer checks `isForeground`: acceptable.** The task asks for `retry()` without the UI flag (F16). The button is only reachable while the card is visible, and a collected `state` is required anyway.
5. **Course reads fixes through its own ViewModel: required by the task.** The task specifies `combine(samples → heading, LocationRepository.fixes → bearing (start with null))`.
   - Gating on `confirmedLocationEnabled` with `emptyFlow` keeps the last bearing while location is off, the same as the old Activity fan-out. This is tested.
   - A `LocationRegistrationException` is swallowed, and the compass keeps working. This is tested and matches the Flight/GNSS ViewModels.
   - Small difference: after a registration failure, the Activity feed used to stop until the next resume. The course bearing branch now re-registers on the next location on/off toggle or when collection restarts. Negligible.

**C. Shared flow and rotation provider: match the planner's decision.** README "Disagreements" line 206 says: singleton source plus a `DisplayManager` default-display rotation provider. The code matches:
- `callbackFlow` over `TYPE_ROTATION_VECTOR`, `@Singleton`, `shareRethrowingIn(@ApplicationScope, replay = 0)`, so `WhileSubscribed` with no stop timeout and no replay.
- A missing sensor or a refused or throwing registration becomes `OrientationRegistrationException`, which is delivered to every collector and cleared once all collectors leave. Tested.
- `awaitClose` unregisters the listener, and a failed registration unregisters defensively.
- `registerListener` is called from the Default-dispatcher scope without a Handler, so events still arrive on the main looper, as before.
- Rotation is read per sample via `displayManager.getDisplay(DEFAULT_DISPLAY)?.rotation`. The multi-display/foldable limitation is noted in KDoc.
- `fromSurfaceRotation` is now `displayRotationFromSurface` in the Android implementation file. Tested.

**D. Nearby: unchanged.**
- Before, `startObservation` ran route.start, then coordinator.start (course.stop, nearby.start, course.start), then horizon.start, then map. Now it runs route.start, nearby.start, map. Nearby still starts right after route.
- `onPause` and the not-granted branch of `refreshPermissionState` used to call coordinator.stop (course.stop, nearby.stop). They now call `nearbyCityController.stop()` at the same point.
- GNSS retry (`onRetry`) still reaches `nearbyCityController.start()` through `startObservation`.
- Fix fan-out to nearby is unchanged, and the characterization scenario 3 (fan-out) is still there.

**E. `data/ShareRethrowing.kt`: behavior unchanged.** The function body is byte-identical to the removed private function. Only the visibility changed (private → internal) and the KDoc grew. `LocationRepository` call sites are unchanged.

## Non-blocking findings
- **N1 (open question content).** When the orchestrator adds the README entry, it should cover three things: (a) the landscape heading ±180° and inverted pitch/roll; (b) the HorizonCard pitch label, where nose-up is shown as "down"; (c) the roll visual rotating the wrong way (`rotationZ = +roll`). All three are for TASK-028. Item (c) should be verified on device first, since I could only check it on paper.
- **N2.** The on-device check "respond correctly in landscape" cannot pass while the pinned defect stands. The on-device check should be read as "landscape unchanged from before TASK-010 (known defect, TASK-028)".
- **N3.** After a background stay longer than 5 s, `stateIn` still holds the last `Available` until the restarted upstream emits `Waiting` from `onStart`, so the old value may show for about a frame. This is the same pattern as TASK-009 and not worth changing here.
- **N4.** `DisplayRotation` and `OrientationSample` went from internal to public. This is required because the public `OrientationDataSource` interface and the `@HiltViewModel` classes use them. Fine.

## Human checks on device
1. **Portrait.** Turn in place: the compass heading follows. Pitch and roll respond on the Horizon card. Note the label and roll-direction caveats (N1) rather than treating them as regressions.
2. **Landscape and reverse landscape.** Expected to be wrong: heading about 180° off, pitch and roll inverted, because the known defect is pinned. Confirm and record for TASK-028.
3. **Rotation.** Rotate with the app open: the horizon keeps its level reference and the course card does not flash "Waiting" (new behavior).
4. **Pause.** Go home for more than 5 s and return: the course card starts from "Waiting" and the horizon re-levels on the first sample.
5. **Retry.** On a device without a rotation-vector sensor (or an emulator without one), both cards show "unavailable".
6. **GPS bearing.** Outdoors while moving, the course card shows the GPS bearing. Turning the location off keeps the last bearing; turning it back on resumes updates.
7. **Nearby city.** Still updates after resume and after the GNSS retry.
8. **Sensor release.** Background the app for more than 5 s and check (e.g. `dumpsys sensorservice`) that no rotation-vector listener remains.

## Verification performed
- **Source review:** task.md; the full diff `origin/ai-modernization...HEAD` (4 commits); the full new files (CourseViewModel, HorizonViewModel, OrientationDataSource, DisplayRotationProvider, OrientationModule, ShareRethrowing, DisplayRelativeOrientation); MainActivity lifecycle wiring (onCreate, onResume, onPause, refreshPermissionState, startObservation, collectLocation); LocationRepository; FlightParametersViewModel for consistency; HorizonState and HorizonCard; CoroutinesModule; all new and removed tests, including the old expected values; README "Disagreements"; task 028 and task 009 notes.
- **Original app:** `OrientationCalculator.kt`, `CourseCalculator.kt`, `HorizonCardViewPresenter.kt` in `~/smart-flight`. The original compass used accelerometer + magnetometer with no display remap. The original horizon kept its reference across pause.
- **Remap math:** worked through by hand against Android's `remapCoordinateSystem` and the physical device pose.
- **CI:** PASS on d9ffeaa (run 36559346272), as reported. I did not re-run or inspect CI logs.
- **Not verified:** runtime behavior on a device (sensor events, display rotation, roll/pitch direction on the real UI); that Robolectric's `ShadowSensorManager` behaves like the platform for the refused-registration path; multi-display or foldable behavior.

Relevant files:
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/orientation/DisplayRelativeOrientation.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/orientation/data/OrientationDataSource.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/orientation/data/DisplayRotationProvider.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/course/ui/CourseViewModel.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/horizon/ui/HorizonViewModel.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/data/ShareRethrowing.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/ui/gnss/HorizonCard.kt
- /home/ai-dev/smart-flight-2-modern/app/src/test/java/kniezrec/com/flightinfo/orientation/DisplayRelativeOrientationTest.kt
- /home/ai-dev/smart-flight-2-modern/docs/tasks/README.md (open-question entry and status still to add)
