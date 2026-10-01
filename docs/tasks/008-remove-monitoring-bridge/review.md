# TASK-008 — Review iteration 2

## Result
PASS

The B1 fix in commit e1d6b99 is correct and a new test covers it. No blocking findings, no escalations.

## Acceptance criteria
| Criterion | Status | Verified by |
|---|---|---|
| `BackgroundMonitoringBridge` and dead foreground paths deleted | MET | Grep of `app/`: no `BackgroundMonitoringBridge`, `BackgroundMonitoringService`, `attachToExternalSession`, `externalSession`, `FlightLocationPlatform`, `GnssStatusPlatform`, `AndroidFlightLocationPlatform`, `AndroidGnssStatusPlatform` or `showError` remain. `BackgroundMonitoringBridgeTest` is deleted. `FlightParametersController` and `GnssStatusController` only consume data (`start`/`acceptLocationFix`/`acceptStatus`/`stop`). |
| Service rules covered by tests (first-fix stop in background, preference-off stop, waiting copy, eligibility stop) | MET | `LocationForegroundServiceTest` covers: first fix in background, hiding after a fix, pause while a fix is in flight (the race the task asks for), preference turned off while hidden, preference off then hide, waiting copy with and without notification permission, eligibility loss on visibility change and on the periodic check, and a new run starting without a fix. CI green as reported. |
| With the service never started, a simulated fix reaches the flight card | MET | `MainActivityCharacterizationTest` scenario 3 (`forwardedFixUpdatesFlightParametersInDefaultUnits`). It uses the real Hilt graph and `ShadowLocationManager.simulateLocation`; the service is never created. |
| TASK-004 scenarios still pass; scenario 9 replaced by "exactly one registration" | MET, one deviation | Scenario 9 is replaced by `activityAndServiceShareOneLocationRegistration`. Scenario 8's expected result changed, not only how data is injected (see Non-blocking 1). |
| Background waiting notification and stop-on-first-fix on a device | HUMAN | Cannot be verified here. |

## Blocking findings
None.

Previous finding B1 (the Activity stopped the controllers because of an old cached `locationEnabled == false`) is FIXED in `/home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt` (`collectLocation`, about lines 565-574):
- The filter is now `!enabled && !locationRepository.isLocationEnabled()`, so a "location off" value is only acted on after a fresh read confirms it.
- Case "old `false`, location now on": ignored. The following fresh `true` is also ignored, so the controllers started in `onResume` keep running.
- Case "location really off at resume": the fresh read is `false`, so the controllers stop as before.
- Case "old `true`, location off": the upstream sends `false`, the fresh read confirms it, and the controllers stop.
- Before B1, the old service-driven path also used a fresh read, so this matches it.

New test `locationBackOnAfterPauseLetsFixesReachFlightCard`:
- Steps: fix shown → location off with a `PROVIDERS_CHANGED` broadcast → card Waiting → pause → location on → resume → a new fix reaches the card.
- It checks both the "off stops the cards" path and the B1 regression.
- Without the fix, the old `false` would stop the controllers on resume and the final `forward(...)` would time out.

## Escalations
None.

## Non-blocking findings
1. **Scenario 8 (developer change 1).**
   - The task Scope explicitly asks for `repeatOnLifecycle(Lifecycle.State.RESUMED)` and says this "matches today's onResume/onPause gating". The previous code did not do that: its `onPause` only called `stopForegroundOnly()`, so fixes kept updating the flight card while paused.
   - The difference only shows when the dashboard is paused but still visible (PiP, or a translucent activity on top). In both versions the card resets to Waiting on resume.
   - The original app gated on `onStart`/`onStop`.
   - Not escalated, because the developer followed the explicit Scope. List it as a behavior change in the PR. TASK-009/016 may revisit STARTED vs RESUMED.
2. **Dashboard gets fixes when the service fails to start (change 2).** The task requires this. Correct.
3. **Service applies its rules immediately on start (change 3).** `combine` emits once at start and re-posts the normal notification copy that `startForeground` already showed. It also re-posts once when the first fix arrives while visible. No visible difference.
4. **Toggling the notification setting off while visible re-posts the same notification (change 4).** `notify` with the same ID and content changes nothing. Harmless.
5. **Revoked permission no longer reported to the Activity by the service (change 5).** Android kills the process on runtime permission revoke, and `onResume` → `refreshPermissionState()` handles the path. The service still stops on eligibility loss. Note it in the PR.
6. **Eligibility signal granularity changed.**
   - Before: the Activity stopped its cards when the service's check failed (GPS or network provider on, plus permission).
   - Now: the Activity reacts to the master location switch.
   - With GPS off but location on, the service may stop by its own check while the cards keep collecting. Mention it in the PR.
7. **Rotation.**
   - `mainDispatcher` is `Dispatchers.Main` (not `.immediate`), and `AppVisibility` is a conflating StateFlow. On an Activity relaunch, the service's collector probably sees only `visible=true`.
   - So a service that already has a fix likely no longer stops and restarts on rotation. The end state is the same.
   - This is inferred from the code, not observed. The task asks for it to be noted in the PR.
8. **Every new run resets "has usable fix".** Previously `beginSession()` reset it only when no service was attached. If the service is stopped and started again immediately, a later pause can briefly show the waiting copy. Negligible, and tested.
9. **`satellites` replays the latest report on resume.** The report comes from the same live registration, so no data from an earlier registration is shown. Acceptable.
10. **`GnssStatusState.LocationServicesDisabled/Unavailable/Error` have no producer.** They could not be produced before this task either. Deferred to TASK-019.
11. **Test timing and sensitivity.**
    - The Activity-level Robolectric tests poll in real time because repository sharing runs on the Default dispatcher.
    - The new test's regression check depends on two things: Robolectric's `setLocationEnabled(true)` not sending a `PROVIDERS_CHANGED` broadcast, and the upstream receiver being gone before re-enable. If either were false, the cached value would already be `true` and the test would pass even without the fix. My understanding of Robolectric is that the broadcast is not sent, but I have not verified it.
    - A Hilt test module that puts `@ApplicationScope` on the main looper, or uses a fake `LocationDataSource`, would make these tests deterministic.

## Human checks on device
- Background waiting notification: open the app with no fix, press Home. The notification switches to the "waiting" copy (with POST_NOTIFICATIONS granted), and the service stops on the first fix.
- Location round-trip: turn location off, open location settings from the app, turn location on, return. The cards receive data with no extra pause/resume.
- Rotation with a fix: does the notification flicker, and does the service keep running?
- If it can be arranged, block the foreground service from starting: the dashboard still shows fixes.

## Verification performed
- **Source review:**
  - Full branch diff `origin/ai-modernization...HEAD` from iteration 1, and commit e1d6b99 (`MainActivity.kt`, `MainActivityCharacterizationTest.kt`).
  - Re-checked `LocationRepository.locationEnabled` (`stateIn`, `WhileSubscribed()`) against the new filter.
- **CI result as reported:** PASS on e1d6b992969fea97ce607439f98dfbd9178e27ed (run 36538327690). Not independently re-checked.
- **Not verified:**
  - Any runtime behavior.
  - Robolectric's broadcast behavior for `setLocationEnabled` (Non-blocking 11).
  - Main-looper timing of an Activity relaunch (Non-blocking 7).
  - Screenshots were not relevant: this task has no UI change.
