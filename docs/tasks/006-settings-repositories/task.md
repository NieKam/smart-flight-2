# TASK-006 — Observable settings repositories (Flow + suspend setters)

## Goal
Replace the synchronous `read()`/`write()` preference stores with repositories that expose settings as `Flow` and write with `suspend` setters, so the Activity, the service and (later) ViewModels observe one source of truth. Remove the `onResume` re-reads.

## Context
- Architecture review F6 (VIOLATION, MEDIUM), verified:
  - Stores expose only sync `read()`/`write()`: `display/DisplayPreferences.kt:32-62`, `displayunits/UnitPreferences.kt:50-89`, `monitoring/BackgroundNotificationPreferences.kt:9-30`.
  - The Activity reads all three in `onCreate` (`MainActivity.kt:157-159`) and again in every `onResume` (`:369-371`) to pick up changes.
  - The service reads its own store on each reconcile (`LocationForegroundService.kt:140-143`). The setting reaches the service only because the Activity also calls `BackgroundMonitoringBridge.setNotificationEnabled` (`MainActivity.kt:198`).
  - `commit()` on the main thread in `DisplayPreferences.kt:48`, `UnitPreferences.kt:70`, `BackgroundNotificationPreferences.kt:23` (and `RouteController.kt:180`, handled in TASK-012).
  - Permission-request history written through an anonymous object in the Activity (`MainActivity.kt:463-470,486-488`).
- Architecture review F16: `DisplayPreferencesApplier` + `DisplayEffectSink` (`display/DisplayPreferences.kt:11-30`) is a 3-line applier with an anonymous sink in the Activity (`MainActivity.kt:111-130`). Direction: a small Activity-level effect driven by settings state.
- Architecture review F12: four hand-written `SharedPreferences` fakes (`RouteControllerTest.kt:194`, `DisplayPreferencesTest.kt:79`, `UnitPreferencesTest.kt:35`, `BackgroundMonitoringTest.kt:43`). Direction: in-memory repository fakes.
- Planner decision (backing store): keep SharedPreferences, not DataStore. Reasons: no new dependency; the same files and keys keep working for existing installs; TASK-036 migrates the original app's `LocalPrefs` into these keys and needs synchronous-friendly access; window flags (keep screen on, orientation) must be applied before first frame, which a synchronous initial value makes simple.

## Dependencies
- TASK-005.

## Target conventions (from CLAUDE.md)
Package by feature with layer sub-packages (`<feature>/data`, `<feature>/ui`); repositories expose `Flow`/`suspend`; Android callbacks wrapped with `callbackFlow` + `awaitClose`; dispatchers injected (qualifiers from TASK-005); keep Android types out of logic classes.

## Scope
- New repositories (interfaces + SharedPreferences implementations, `@Singleton`, bound in Hilt):
  - `UnitSettingsRepository`: `val units: StateFlow<UnitPreferences>` (or `Flow` + `current()`), `suspend fun setUnits(UnitPreferences)`.
  - `DisplaySettingsRepository`: `val display: StateFlow<DisplayPreferences>`, `suspend fun set(...)`.
  - `BackgroundNotificationSettingsRepository`: `val settings: StateFlow<BackgroundNotificationPreferences>`, `suspend fun setShowBackgroundNotification(Boolean)`.
  - `PermissionRequestHistoryRepository` (implements the existing `LocationPermissionRequestHistory` contract) backed by file `location_permission`, key `has_requested_location_permission`.
- Change-observation: `callbackFlow` over `OnSharedPreferenceChangeListener` (keep a strong reference to the listener), mapped with the existing parsing (defaults and malformed values handled exactly as in the current stores). `stateIn(@ApplicationScope, SharingStarted.Eagerly, initialSyncRead)` is acceptable so consumers get a synchronous current value.
- Writes use `apply()` (or `commit()` on `@IoDispatcher`), never `commit()` on the main thread.
- `MainActivity`: collect display settings in `lifecycleScope` (`repeatOnLifecycle(STARTED)`) and apply keep-screen-on / requested orientation there; delete `DisplayPreferencesApplier`/`DisplayEffectSink` (fold the "only request orientation if different" rule into the Activity effect or a pure function with a test). Remove the `onResume` re-reads (`:369-371`) and the `onCreate` reads; the Compose UI reads the `StateFlow`s with `collectAsStateWithLifecycle()`.
- `LocationForegroundService.showBackgroundNotification()` reads the injected repository's current value; keep the `protected open` seam.
- `BackgroundMonitoringBridge.setNotificationEnabled` call stays for now (the bridge is removed in TASK-008), but may be triggered from observing the flow instead of from the settings callback.
- Tests: replace SharedPreferences fakes in `DisplayPreferencesTest`, `UnitPreferencesTest`, `BackgroundMonitoringTest` with Robolectric real SharedPreferences for the repository implementation tests, and add a small in-memory fake of each repository interface in `app/src/test/.../testutil/` for consumers.

## Out of scope
- Route persistence (TASK-012), legacy `LocalPrefs` migration (TASK-036).
- Settings screen ViewModel (TASK-015). The Activity may still pass values/callbacks into `UnitSettingsScreen` in this task.

## Requirements
Required:
- Same file names and keys: `display_behavior` (`display_behavior_keep_screen_always_on`, `display_behavior_portrait_orientation`, `display_behavior_larger_map_zoom`), `display_units` (`display_units_speed|altitude|distance|vertical_speed|pressure`), `monitoring_behavior` (`monitoring_show_background_notification`), `location_permission` (`has_requested_location_permission`). Same defaults (keep-screen false, portrait true, larger zoom false, notification true, units km/h, m, km, m/s, mbar).
- Changing a setting in the Settings overlay is reflected on the dashboard immediately, without leaving/returning to the Activity.
- Window effects (keep screen on, orientation) are applied at startup before content is shown (no visible rotation flash with portrait forced).
- No `commit()` on the main thread.

Recommendations:
- Put each repository in its feature package (`displayunits/data`, `display/data`, `monitoring/data`, `permission/data`).

## Acceptance criteria
- [ ] Repositories expose `Flow`/`StateFlow` and `suspend` setters; no `read()`/`write()` polling remains in `MainActivity` — verified by: code review
- [ ] Unit tests: defaults, malformed values, round trip, and change emission for each repository — verified by: CI unit test
- [ ] Characterization test "settings overlay changes unit" (TASK-004 scenario 6) still passes — verified by: CI unit test
- [ ] Orientation/keep-screen-on applied on launch and after toggling — verified by: CI unit test (Robolectric: window flags and `requestedOrientation`) + HUMAN on device (no rotation flash)
- [ ] ktlint — verified by: ktlint

## Tests to add or update
- `DisplaySettingsRepositoryTest`, `UnitSettingsRepositoryTest`, `BackgroundNotificationSettingsRepositoryTest`, `PermissionRequestHistoryRepositoryTest` (Robolectric SharedPreferences).
- Replace/remove `DisplayPreferencesTest`, `UnitPreferencesTest`, and the preference part of `BackgroundMonitoringTest` (keep equivalent cases).

## Risks and edge cases
- `OnSharedPreferenceChangeListener` is held weakly by Android; keep a strong reference inside the `callbackFlow`.
- `apply()` is async to disk but in-memory immediately; the service reading the same `SharedPreferences` instance sees the new value.
- Existing installs: nothing to migrate (same keys).
