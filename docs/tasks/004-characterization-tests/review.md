# TASK-004 — Review iteration 1

## Result
PASS

There are no BLOCKING findings. The one mismatch with the task text (scenario 8) could only be resolved one way under the task's own Goal and Out-of-scope rules, so I don't escalate it. It is listed below as a note for the PR description and as a follow-up for TASK-009.

## Acceptance criteria
| criterion | status | verified by |
|---|---|---|
| Scenarios 1–9 exist and pass | Met. Scenario 8 pins current behavior, which is not what the task text describes (see Escalations) | Source review of `MainActivityCharacterizationTest.kt`; CI run 36416348514 on 9a214e5 as reported (the workflow runs `ktlintCheck testDebugUnitTest assembleDebug`) |
| No production behavior change | Met | `git diff origin/ai-modernization...HEAD --name-only` shows only `app/src/test/.../MainActivityCharacterizationTest.kt` and `app/src/test/.../testutil/TestFixes.kt`. Nothing under `app/src/main` changed and no test seam was added |
| ktlint passes | Met, as reported | CI runs `ktlintCheck` in the same job, which was reported green. I did not run it locally |

Scenario by scenario, checked against `MainActivity.kt`:
1. **Permission not granted.** The test asserts that the permission title and grant button are shown, that zero nodes exist for the GNSS / flight / course / route / Settings / About titles, and that no service start was recorded. This is meaningful: without permission, `onCreate` and `onResume` only call `stopService`, so a start request would be a regression.
2. **Permission granted.** The test asserts that the GNSS card is in "waiting" state, that Settings and About are shown, that the flight card is waiting, and that `nextStartedService` is `LocationForegroundService`.
3. **Fix forwarded.** "36.0 km/h" and "100.0 m" are shown, and vertical speed is unavailable. An extra test checks that the same fix reaches the nearby-city card (Warsaw). Together they pin the fan-out at `MainActivity.kt:505-511`.
4. **Satellites forwarded.** The used-count plural (2) is shown and "waiting" is gone.
5. **Pressure.** A `ShadowSensor` pressure event before any fix shows neither the pressure value nor its label, and the card stays waiting. After a fix, "1,000.5 mbar" is shown. This proves the stored pressure is merged into the first readings (`:500-503`).
6. **Settings.** The test goes through Settings, picks the speed unit, chooses mph and navigates up. It then checks that `display_units/display_units_speed == "mph"`, that "22.4 mph" is shown and that "36.0 km/h" is gone.
7. **Route.** It seeds `route` preferences with IDs 31395 and 10409, then checks that departure Warsaw, destination Berlin and the distance label are shown. The `"$role: $city"` text matches `RouteCard.kt:128`.
8. **Pause/resume.** This pins the current behavior (see below). It is not tautological: "72.0 km/h" can only come from the fix forwarded while paused. After resume, the test checks that the card is back to waiting and that a new fix shows "108.0 km/h".
9. **No own location listener.** `locationUpdateListeners` is empty on both the activity's and the application's `LocationManager`. A simulated platform location does not change the flight card.

The task allowed extra controller-level tests only for behavior that cannot be reached through the Activity. Every scenario was reached through the Activity, so none were needed. That is correct.

## Blocking findings
None.

## Escalations
None affect the result. For the record, here is how the scenario 8 discrepancy was handled:
- The task text says that after `onPause`, forwarded fixes do not change the flight card. The task's own Context says `onPause` stops only pressure, course, route, horizon and map, and not flight.
- The code confirms this:
  - `onPause` calls `courseObservationCoordinator.stopForegroundOnly()`, which does not stop `flightParametersController` (`ForegroundCourseObservationCoordinator.kt:30-33`).
  - `acceptLocationFix` still forwards while `registered` is true.
  - `onResume` → `refreshPermissionState` → `startObservation` → `attachToExternalSession()` → `stop()` sends `FlightParametersState.Waiting` (`FlightParametersController.kt:57-77`).
- The task's Goal ("pin the current observable behavior") and Out of scope ("Fixing any bug … file it") leave only one correct option: pin current behavior and document it. The developer did that, with a clear comment on the test.
- Parity note: the original app attaches its presenter in `onStart` and detaches in `onStop` (`~/smart-flight/.../MainActivity.kt:46-54`). So updating the card while paused but still visible matches the original. The reset to "waiting" on every resume is the part that is questionable.

## Non-blocking findings
1. **`MainActivityCharacterizationTest.kt:97-104`.** If `scenario?.close()` throws, the bridge reset and the restore of `robolectric.createActivityContexts` are skipped. That would leak state into later test classes in the same JVM fork. Suggest wrapping the close in `try { … } finally { … }`. This can be a follow-up; it is not needed for this PR.
2. **Scenario 9 has no positive control.** Nothing shows that `ShadowLocationManager.locationUpdateListeners` sees a registration made through the executor-based `requestLocationUpdates` that `AndroidFlightLocationPlatform` uses. I expect it does in Robolectric 4.x, but that is not verified. Also, the `simulateLocation` step is vacuous when no listener exists; it is harmless. It does not check GNSS-status callback registrations, and the task did not ask for that. TASK-008 deletes this path anyway, so no action is needed.
3. **Scenario 8 paused step.** It relies on `ViewRootForTest.semanticsOwner` plus manual `mainClock.advanceTimeByFrame()` and `idleFor(16 ms)` × 50. It is single-threaded under Robolectric's paused looper, so it is deterministic, and it fails closed: if the frame never arrives, it returns false and the assert fails. It never passes falsely. It is sensitive to future Compose test-API changes; the comment explains why it exists.
4. **20 s `waitUntil` (scenarios 3b and 7).** The lookup runs on a real executor thread against the real SQLite DB. The timeout is generous, and a timeout would show up as a failure, not a false pass. The flakiness risk is low.
5. **The system property is set per class and restored correctly** (the previous value is kept, or the property is cleared). It is set in `@Before`, before `ActivityScenario.launch` inside each test. Good.
6. **Hygiene.** The map zip is written to `cacheDir`, which Robolectric keeps per test. `MapArchiveRepository.kt:22` accepts a one-entry archive, so the 28 MB copy is skipped. `@After` clears both bridge handlers, as the task requires.

## Follow-ups (for the orchestrator)
- **TASK-009:** decide whether "flight card resets to waiting on every `onResume`" should be kept (it is now pinned) or fixed toward the original. Also decide on "fixes forwarded while paused update the card", which matches the original's `onStart`/`onStop` scope. If it is changed, update scenario 8 in that task.
- **PR description / README note:** state that scenario 8 pins current behavior, which differs from the task text. Also list the developer's observations: pressure is reset to null on pause (not asserted), and `RouteController.restore` reads the city table twice.
- Optional: add a `try/finally` in `tearDown` (non-blocking item 1).

## Behavior changes
None. The change is test-only.

## Human checks on device
None required for this task, since it is test-only. Optional: on a device, confirm that when you return to the app, the flight card briefly shows "waiting" before the next fix. That is the current behavior pinned in scenario 8, and it is relevant to the TASK-009 decision.

## Verification performed
- **Source review:** the full diff, both new files, `MainActivity.kt` (onCreate, onResume, onPause, onDestroy, controller wiring), `FlightParametersController` (stop, attach, accept), `ForegroundCourseObservationCoordinator`, `BackgroundMonitoringBridge`, `RouteCard.kt`, the `MapArchiveRepository` usability check, `robolectric.properties` and the CI workflow. For the original app, I read its `MainActivity` lifecycle hooks.
- **CI result as reported:** PASS on 9a214e5 (run 36416348514), which includes ktlintCheck and testDebugUnitTest.
- **Not verified:**
  - I did not run the tests or ktlint locally.
  - I did not check whether Robolectric's `locationUpdateListeners` sees executor-based registrations.
  - I did not check runtime behavior on a device.
  - I did not check how the original's `refreshView()` on resume affects values.
