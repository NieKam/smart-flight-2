# TASK-006 — Review iteration 1

## Result
**PASS.** I found no blocking findings and nothing to escalate. The four non-blocking notes below can be handled as follow-ups.

## Acceptance criteria
| Criterion | Status | Verified by |
|---|---|---|
| Repositories expose `Flow`/`StateFlow` and `suspend` setters; no `read()`/`write()` polling left in `MainActivity` | Met | Source review. The three settings repositories expose `StateFlow` and have `suspend` setters. The `onCreate` reads, the `onResume` re-reads and the local `mutableStateOf` copies are all gone. Compose uses `collectAsStateWithLifecycle()`. The only `.read()` left in `MainActivity` is `AndroidAppVersionProvider`, which is unrelated. |
| Unit tests cover defaults, malformed values, round trip and change emission for each repository | Met | CI unit test, as reported. Display, Unit and BackgroundNotification each have tests for defaults, malformed values, setter plus persistence plus a new-instance read-back, and emission of changes written elsewhere. Permission history has no Flow (the task only requires the existing synchronous contract), so it tests default, persistence and "value written elsewhere is read on next access" instead. |
| Characterization test "settings overlay changes unit" (TASK-004 scenario 6) still passes | Met | `MainActivityCharacterizationTest` is untouched by the diff; CI green on e4a57bf, as reported. |
| Orientation and keep-screen-on applied on launch and after toggling | Met (CI part) + HUMAN | `MainActivityDisplaySettingsTest` covers defaults on launch, stored values on launch, and toggling in both directions (window flag, `requestedOrientation` and persisted keys). The pure `orientationToRequest` is covered by `DisplayWindowEffectsTest`. The rotation-flash part needs a device check. |
| ktlint | Not re-run by me | CI result as reported. |

Requirements:
- **Files, keys and defaults:** identical. The files are `display_behavior`, `display_units`, `monitoring_behavior` and `location_permission`, and every key string matches the deleted stores and the old anonymous object.
- **Malformed-value handling:**
  - The `all[key] is Boolean` guard for display and notification is copied verbatim.
  - The unit `enumOrDefault` behaves the same; only the reified cast became a `T : UnitKey` bound.
  - Permission history still uses a plain `getBoolean(..., false)`, as before.
- **Writes:** all use `apply()`. The only `commit()` left in main code is `RouteController.kt:180`, which is out of scope (TASK-012).
- **Permission history:** `PermissionRequestHistoryRepository` implements `LocationPermissionRequestHistory` as a `@Singleton` bound through `@Binds`. It has no Flow, which matches the task's scope.
- **Service:** `LocationForegroundService.showBackgroundNotification()` reads `settings.value` and keeps the `protected open` seam.
- **Bridge:** `BackgroundMonitoringBridge.setNotificationEnabled` is still called from the settings callback, which the task allows.
- **Out of scope:** nothing out of scope was touched (no route persistence, no settings ViewModel).

## Blocking findings
None.

## Escalations
None.

## Non-blocking findings
1. **NON_BLOCKING — setter writing the StateFlow directly** (`data/SharedPreferencesFlows.kt:242-249` together with each setter)
   - The `@ApplicationScope` scope runs on `Dispatchers.Default` (`di/CoroutinesModule.kt:49-51`). The re-read (`state.value = read()`) therefore runs on a Default thread, while the setters write `state.value` on the main thread.
   - A stale emission is possible with two setter calls in quick succession, A then B. The collector reads A for A's key event, is preempted, the setter publishes B, and the collector then writes A.
   - This is eventually consistent. B's `apply()` changes at least one key compared with A, so its listener events queue another re-read that restores B. Because every re-read reads the current in-memory map, there is no lost update and no lasting wrong state.
   - The worst case is a transient A then B emission, for example a brief orientation request flip if orientation is toggled twice within the same few milliseconds. That is practically unreachable through the UI.
   - The deviation itself is justified: it gives a value that is available synchronously after the setter returns, which the dashboard and the service rely on.
   - Optional hardening: run the re-read collector on the main dispatcher (listener callbacks already arrive on main), or drop the direct write and accept a one-dispatch delay.
2. **NON_BLOCKING — possible dropped change event** (`SharedPreferencesFlows.kt:228-233`). `callbackFlow` uses a buffer of 64 with `trySend`, so an event is dropped if the buffer is full. Adding `.conflate()` would make "the latest change always triggers a re-read" hold structurally. This is theoretical with the current write volume.
3. **NON_BLOCKING — listener registered asynchronously.** The listener is registered when the Default-dispatched `launchIn` starts, not in the constructor. An external write between the initial synchronous read and registration would be missed until the next change. No such writer exists today, since only the repositories write these files and they publish directly.
4. **NON_BLOCKING — interface made public** (`permission/LocationPermissionStateController.kt:836`). `LocationPermissionRequestHistory` went from `internal` to `public` only so the public Hilt `@Binds` module compiles. Making `PermissionBindingsModule` internal would have avoided widening the API. It is harmless.

Other points I checked and found sound:
- **Listener lifetime:** the listener lambda is captured by the `awaitClose` block of a coroutine that is a child of the `SupervisorJob` held by the Hilt `@Singleton` `@ApplicationScope`. It stays strongly reachable for the life of the process, and the collectors never end, which fits process-wide singletons.
- **`MainActivity` collection:** the display collector runs in `lifecycleScope` under `repeatOnLifecycle(STARTED)` on `Main.immediate`, so window calls happen on the main thread. It re-applies on every start, which replaces the old `onResume` re-apply. The "request orientation only if different" rule prevents redundant requests.
- **Setter calls in `launch`:** the setters are wrapped in `lifecycleScope.launch`, but they never suspend and the scope uses `Main.immediate`. The body therefore runs synchronously, including the bridge call and the `startBackgroundMonitoring` check, the same as before.

## Behavior changes
- **Keep-screen-on / orientation after a Settings toggle:** now applied by the `STARTED`-scoped collector instead of synchronously inside the callback. There is no user-visible difference, since the overlay is only interactive while resumed.
- **`onResume`:** no longer re-reads the settings from disk. Only this app writes these files, so there is no functional change.
- **Test coverage of the notification setting:** `LocationForegroundServiceTest` now drives the setting through the fake repository instead of the SharedPreferences file. This is test-only.

## Deleted-test coverage
- **`DisplayPreferencesTest`:**
  - Defaults, malformed values and round trip are ported to `DisplaySettingsRepositoryTest`.
  - The "re-read after recreation" case is covered by the new-instance read-back.
  - The applier's "only when different" rule is now in `DisplayWindowEffectsTest`.
  - The keep-screen on/off calls are now in `MainActivityDisplaySettingsTest`.
- **`UnitPreferencesTest`:** both cases are ported, plus a separate all-defaults test.
- **`BackgroundMonitoringTest`:** missing and malformed values defaulting to true, and the round trip, are ported.

All coverage is ported. The in-memory fakes are in `testutil/FakeSettingsRepositories.kt`.

## Human checks on device
- Cold launch with portrait forced (the default), holding the device in landscape: check there is no visible rotation flash before the first frame.
- Settings: toggle keep-screen-on and orientation. The effect should apply immediately, and the dashboard should show unit and larger-zoom changes without leaving the Activity.
- Rotate with sensor orientation enabled: after recreation, the settings are kept and applied.
- Turn off the background notification, then press Home: the foreground service stops and no notification remains. Turn it back on while in the foreground: monitoring restarts.

## Verification performed
- **Source review:** `git diff origin/ai-modernization...HEAD` (28 files), the full `MainActivity.onCreate` and callbacks, `di/CoroutinesModule.kt`, every new repository, module, helper and test, the deleted stores and tests, and a grep for remaining `commit()`, `getSharedPreferences` and key usages.
- **CI result as reported:** PASS on e4a57bf (run 36430492756).
- **Not verified:** I did not run any build, test or ktlint myself. I did not check runtime behavior on a device, including the rotation flash. I did not consult the original app's source or screenshots, because this task restores no original behavior.

Relevant files:
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/data/SharedPreferencesFlows.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/di/CoroutinesModule.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/display/data/DisplaySettingsRepository.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/display/ui/DisplayWindowEffects.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/displayunits/data/UnitSettingsRepository.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/monitoring/data/BackgroundNotificationSettingsRepository.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/permission/data/PermissionRequestHistoryRepository.kt
