# TASK-007 — Review iteration 1

## Result
**PASS.** I found no blocking findings and nothing to escalate. The notes below are non-blocking and suitable as follow-ups; none of them needs another CI round in this loop.

## Acceptance criteria
| criterion | status | verified by |
|---|---|---|
| Data sources are `callbackFlow`-based; registration on first collection, unregistration on cancel (Robolectric: 1 listener with two collectors, 0 after cancel) | MET | Source review of `AndroidLocationDataSource` / `registerUntilClosed`. `AndroidLocationDataSourceTest`: "fix collection registers one one-second gps request…" and "repository over the platform keeps one listener for two collectors and none after both stop". CI green as reported. |
| Registration failure propagates as an exception or typed signal | MET | `RegisterUntilClosedTest` covers refused, throwing (SecurityException cause) and failing-unregister cases. `LocationRepositoryTest` covers fix and satellite failure. `LocationForegroundServiceTest` covers location and GNSS registration failure, with stopSelf and the notification cleared. |
| `LocationForegroundServiceTest` and `LocationForegroundServiceEligibilityTest` pass | MET (CI as reported) | The service test is updated to the new seam (the repository is swapped after `create()`). The eligibility test needed no change; it doesn't touch sessions. |
| TASK-004 characterization tests pass unchanged | MET (CI as reported) | `MainActivity` and `MainActivityCharacterizationTest` are not in the diff. |
| Background notification and first-fix stop work on a device | HUMAN | See "Human checks on device". |

## Scope and boundary with TASK-008
- The following are all done:
  - `location/data/` interface, Android implementation, Hilt `@Binds` and the `@Singleton` repository.
  - The service collects the repository in its own `SupervisorJob() + @MainDispatcher` scope. That scope is cancelled in `stopMonitoring()`, which is reached from `onDestroy`, `onTaskRemoved`, ACTION_STOP, eligibility loss, first usable fix and the preference being turned off.
  - Forwarding still goes through `BackgroundMonitoringBridge.forwardLocation/forwardGnssStatus(generation, …)`.
  - `LocationGnssMonitoringSession`, `MonitoringSession` and their test are deleted.
- `AndroidFlightLocationPlatform` and `AndroidGnssStatusPlatform` stay only because `MainActivity.kt:471,478` still builds them. They now share the mapping and request helpers, which is allowed.
- Nothing from "Out of scope" was touched: the bridge and the Activity's data path are unchanged, and there is no GPS-disabled UI.
- The five cases in the deleted session test are all covered by the new tests:
  - forwarding and no duplicates: repository share tests
  - GNSS failure releases location: service test
  - throwing registration gets unregistered: `RegisterUntilClosedTest`
  - throwing GNSS registration releases both: service test
  - late callback from a stopped registration is ignored: repository test and service recreation test

## Blocking findings
None.

## Escalations
None.

## Focus-area analysis
**Flow instead of SharedFlow, and `shareRethrowingIn`** (`LocationRepository.kt:73-88`)
- Justified. A plain `shareIn` would send an upstream failure to the application scope and leave collectors waiting forever. The public type is `Flow`, but it keeps the single-registration and WhileSubscribed semantics the task asks for.
- Failure after the fact: the failure is stored in a per-flow `MutableStateFlow`. The failed sharing coroutine stays "started" in `awaitCancellation()` until the subscriber count reaches 0. The `finally` then clears the failure, and the next collector registers again (proven by `registerCount == 2` in the repository failure test).
- Hangs: I found no case where a collector hangs. Both branches of the merge are subscribed together, so the sharing cannot stop between them. Either a collector sees the stored failure, or its shared subscription triggers a fresh START and a new registration.
- Leaks: the failure is dropped when the last collector leaves, and there is no extra coroutine. Nothing leaks.
- A later collector can see an old failure only in a narrow window. The count goes 0→1 before the STOP command is processed, and `subscriptionCount` conflates the change, so there is no restart. That collector fails with the previous failure instead of retrying. This is documented in the KDoc. The cause (permission or provider) is persistent in practice, so it is non-blocking (N2).

**Sharing semantics**
- `fixes` has replay 0, and `satellites` uses `conflate()` with replay 1 and `replayExpirationMillis = 0`. Both choices are documented and tested ("not replayed to a later collector", "released registration not replayed", "late callback of a released registration").
- A new service run cannot receive a replayed fix, so the first-fix stop is not triggered by cached shared-flow data.

**Threading**
- Sharing runs in `@ApplicationScope` (`Dispatchers.Default`), so `requestLocationUpdates`, `registerGnssStatusCallback` and `registerReceiver` run off the main thread.
- Callbacks still arrive on `context.mainExecutor` with the same request, `LocationRequest.Builder(1_000L)`.
- The service collectors run on Main, so bridge forwarding stays on the main thread.
- Listener leaks on cancel: `register` does not suspend, and `awaitClose`'s block runs even if the producer is already cancelled. If registration fails, `unregister` runs first. I found no leak path.

**Service lifecycle**
- `monitoringScope === scope` guards the stop-on-failure path against a stale scope.
- Only `LocationRegistrationException` is caught, so cancellation is not swallowed. A stack-trace-recovered copy keeps the same type because the `(String, Throwable?)` constructor exists.
- Calling `stopMonitoring` twice (for example the failure path and then `onDestroy`) is safe.

**Accepted modernizations preserved**
- Stop at first usable fix: the bridge's `onUsableLocationFix` → `onUsableFix` path is unchanged and tested.
- The 1 s GPS request is kept and asserted in the Robolectric test.
- The enabled-services and GNSS-hardware checks still run before collection, in the same order after `attach`.

## Behavior changes (listed by the developer; acceptable)
1. Registration is asynchronous and off the main thread.
   - A registration failure now stops the service after `registerProviderReceiver()` and the eligibility-check post, instead of inside `onStartCommand`.
   - `stopMonitoring()` removes both, so the end state is the same.
2. Location and GNSS registrations happen concurrently instead of in sequence. If one fails, the sibling is cancelled and released (tested both ways).
3. Not listed: fixes now reach the bridge through a dispatch hop instead of synchronously inside the callback. The delay is negligible and invisible to the user. Worth adding to the PR description.

## Non-blocking findings
- **N1** (`LocationRepository.kt:47-51`): `fixes` and `satellites` are typed `Flow` rather than the `SharedFlow` the task asks for. The deviation is justified above. Record it in the PR description and in the TASK-008 notes so ViewModel consumers don't expect `SharedFlow`.
- **N2** (`LocationRepository.kt:73-88`): a collector that joins inside the stop window can receive the previous failure instead of retrying. Possible follow-up: tag failures with a registration generation, or let the service retry once. Not required now.
- **N3** (`LocationForegroundService.kt:44-45`): the test seam replaces an `@Inject lateinit var` after `onCreate()`. It is not a constructor seam or a `protected open` factory. It works and is documented; TASK-008 or a later Hilt test module (`@TestInstallIn` binding a fake `LocationDataSource`) could make it cleaner.
- **N4** (`AndroidLocationDataSourceTest`): there is no Robolectric test for the two-collector GNSS callback count or for a real `SecurityException` from `ShadowLocationManager`. The AC only requires the listener count, and the fake-based tests cover failure.
- **N5** (`LocationRepository.kt:54-57`): `locationEnabled` has no consumer yet, and its initial value is read in the constructor, during Hilt injection on the main thread. That is fine as it stands; revisit when TASK-008 or TASK-019 consume it.
- **N6** (`AndroidLocationDataSource.kt:92`): `.distinctUntilChanged()` becomes redundant once the flow goes through `stateIn`. It is harmless, and it helps direct collectors of the data source.
- **N7** (`LocationForegroundServiceTest`, "old registration callbacks are ignored after recreation"): the test passes because the old channel is closed, not because of the bridge's generation check. The bridge generation logic is covered elsewhere.

## Human checks on device
1. With the Activity in the background and no GPS fix, the "waiting" notification appears. When the first usable fix arrives, the notification disappears and the service stops.
2. Turning location services off while the service runs stops it and removes the notification. Starting the service with location off, or on a device without GPS, stops it immediately.
3. Dismissing the notification (ACTION_STOP) stops monitoring. Coming back to the foreground swaps the waiting copy for the plain notification without a duplicate.
4. Dashboard data (satellites with signal strength, speed, altitude) is unchanged while the service runs. A second background cycle registers GPS again, which `dumpsys location` should show as a single GPS request from the app's service.

## Verification performed
- Read `docs/tasks/007-location-repository/task.md` and `CLAUDE.md`.
- Read `git diff origin/ai-modernization...HEAD` (4 commits, working tree clean).
- Read these files in full: `LocationForegroundService.kt`, `AndroidLocationDataSource.kt`, `LocationDataSource.kt`, `LocationRepository.kt`, `LocationModule.kt`, the kept Android platforms, `di/CoroutinesModule.kt`, `di/SystemServicesModule.kt`, and all new or changed tests and fakes.
- Checked the deleted `LocationGnssMonitoringSessionTest` cases against the new tests.
- Grepped the remaining users of the old platforms (only `MainActivity`).
- CI: PASS on 587be7b as reported by the main session (run 36438066106). I did not re-inspect the CI logs.
- Not verified: anything at runtime or on a device, including real `LocationManager` behavior when registering from a non-main thread, Android delivering a cached location on a fresh request, and notification behavior. No comparison with `~/smart-flight` was needed: the task requires no behavior change relative to the current rewrite, and the request settings and mapping are byte-for-byte the same as before.

Relevant files:
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/location/data/LocationRepository.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/location/data/LocationDataSource.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/location/data/AndroidLocationDataSource.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/monitoring/LocationForegroundService.kt
- /home/ai-dev/smart-flight-2-modern/app/src/test/java/kniezrec/com/flightinfo/location/data/LocationRepositoryTest.kt
- /home/ai-dev/smart-flight-2-modern/app/src/test/java/kniezrec/com/flightinfo/location/data/AndroidLocationDataSourceTest.kt
- /home/ai-dev/smart-flight-2-modern/app/src/test/java/kniezrec/com/flightinfo/monitoring/LocationForegroundServiceTest.kt
