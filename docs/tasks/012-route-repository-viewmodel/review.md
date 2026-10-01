# TASK-012 — Review iteration 1

## Result
**PASS.** I found no blocking findings, no spec conflicts and no unclear requirements. There are seven non-blocking notes below.

## Acceptance criteria
| criterion | status | verified by |
|---|---|---|
| `RouteRepository` round trip, change emission, clear one/all (Robolectric SharedPreferences) | Met | `RouteRepositoryTest`: MIN_VALUE/missing means unset, set persists under the same keys, a new instance reads the route back, set/clear touch only their own key, clearAll, emission of repository writes and of outside writes. CI green on ff5f70c (as reported). |
| `RouteViewModel` ports every `RouteControllerTest` case | Met | Case mapping: restore → `saved endpoints are restored…` and `a chosen route is restored by a new view model`. Invalid-id drop → `unknown saved id…` and `bad coordinates or time zone…` (both also check the saved id is removed). Restore error → `a failed restore…` (checks the second read is a reload), plus `a failed retry keeps the error`. Choose/reject → `choose saves a valid city…`. Clear one and clear all → the two clearing tests. Fix monotonicity → `older and invalid fixes…`. Search/nearest → `search and nearest use the city data…`. Stop session → replaced by the stop-timeout tests (see D1). Details only with both endpoints → `details and overlay only with both endpoints`. Also added: `the latest endpoint change wins…` (gated IO dispatcher). |
| `RouteModelsTest` updated for raw values; `RouteCard` formatting tested with a fixed locale | Met | `RouteModelsTest`: exact `Duration`/`Instant`/zone asserts. `RouteCardTest`: en-GB and en-US qualifiers, Berlin zone, 26:05 beyond 24 h, waiting text when there is no arrival. `GnssStatusScreenTest` is pinned to en-US. |
| TASK-004 scenario 7 (route restored after launch) | Met | `MainActivityCharacterizationTest.persistedRouteIsRestoredIntoRouteCardAfterLaunch` is unchanged in this diff and CI is reported green. |

## Specific checks A–G
**A. RouteRepository** (`route/data/RouteRepository.kt`)
- Uses the same `@RoutePreferences` file, the keys `route_departure_id` / `route_destination_id`, and treats `Long.MIN_VALUE` or a missing key as unset.
- Builds its state with the TASK-006 `observedState(scope)` listener pattern.
- `set`/`clear` edit only their own key; `clearAll` removes both. Writes use `apply()`, and `state.value` is updated right away inside `synchronized`.
- "Not verified 6" is resolved: the ViewModel never reads or writes prefs, the old worker read against main-thread `commit()` is gone, and all writes go through one guarded path.

**B. RouteViewModel**
- `merge(ids, retries).mapLatest(resolve)`, so the latest write wins.
- `CancellationException` is rethrown; any other exception gives `RouteError.RESTORE` and nothing is removed.
- `keepValid` removes an id only if it is still the stored one.
- `retryRestore` calls `cityRepository.reload()`.
- Fixes are gated by `confirmedLocationEnabled` inside `flatMapLatest`. `LocationRegistrationException` is caught and anything else is rethrown.
- `latestValidFixes` drops invalid coordinates, and an invalid fix does not raise the timestamp bar.
- Details are built only when both endpoints are set, with `clock.instant()` (K8 kept).
- `stateIn(WhileSubscribed(5_000))` matches the TASK-009..011 ViewModels.
- The pure `routeDetails`/`routeOverlay`/`validCity` functions stay pure (K2). The only signature change is that `now` no longer has a default.

**C. Formatting**
- The previous text was `"$a (${details.duration})"`: a SHORT localized date-time in the destination zone, and `"%02d:%02d"` with `Locale.ROOT` from `toHours()`/`toMinutesPart()`.
- `arrivalText()` in `RouteCard.kt` produces the same string, with the same null → "waiting for speed" fallback. The only difference is where the locale comes from (D5).
- The accepted MODERNIZATION "arrival as full date-time" is preserved.
- F8 (no `SharedPreferences` in logic or state holder) and F10 (raw `Instant`/`ZoneId`/`Duration` in state, formatted in the composable) are both addressed.

**D. Behavior changes reported by the developer.** All acceptable; each should be listed in the PR description.
1. **Position kept across pause, re-resolved after a longer stop.** This follows from the required `WhileSubscribed(5_000)` pattern, as in 009–011. It is also closer to the original app: `~/smart-flight/.../cards/route/RouteCardViewPresenter.kt` never cleared the remaining-distance text on pause.
2. **Saved route re-resolved on observation restart, not on every resume.** Harmless: `CityRepository` caches the cities, so this costs nothing.
3. **Picker results no longer dropped while paused.** Acceptable as a temporary path; TASK-013 owns the picker.
4. **Choosing a city after a restore error no longer erases the other endpoint.** Old `persist()` rewrote both keys from memory; per-endpoint `set` is required by the task's own API. This is in scope as a consequence of the required repository design, not scope creep. It is user-visible, so it must be listed in the PR.
5. **Arrival locale from `LocalConfiguration`.** Normally equal to `Locale.getDefault()`, and it matches TASK-011's approach for the nearby card.

**E. MainActivity**
- `lookUpRouteCities` cancels the previous lookup and runs in `lifecycleScope`. `result {}` rethrows cancellation, and `if (isActive)` in `finally` checks the launched coroutine's own scope, so a replaced lookup does not reset the loading flag.
- The RESUMED `collectLocation` now feeds only `mapRules`; the map fan-out is unchanged.
- Deleted: `RouteController`, `AndroidNearbyCityRepository` with its legacy interface and `@Binds`, `RouteControllerTest`, `normalizeCityQuery` and its tests. No uses remain.
- Search parity: the old code lowercased with `ROOT` and matched with `contains(ignoreCase)`; the new code trims and matches with `contains(ignoreCase)`. Same results.

**F. HiltSingletonScopeTest.** That test only compares fields injected into `MainActivity`; `nearbyCityRepository` and `clock` are no longer injected there, so removing them is correct. `CityRepository` and `RouteRepository` were never covered by it, so no coverage was lost. Adding them is an optional improvement (see N1).

**G. Orphaned `filesDir/nearby-city/cities_info.db`.** Correctly left out: the task does not ask for cleanup. It should just be noted in the PR.

## Blocking findings
None.

## Escalations
None.

## Non-blocking findings
- **N1 — no singleton test for `RouteRepository` / `CityRepository`.** `RouteRepository` must be `@Singleton`: a second instance would hold its own `state`, and `keepValid`'s "still the stored id" check depends on a single instance. A test through a Hilt `EntryPoint` would pin this.
- **N2 — `app/src/main/java/kniezrec/com/flightinfo/di/ClockModule.kt:10`: stale KDoc.** It still says "for controllers that take `() -> Instant`"; the only remaining user in this path is a ViewModel taking `Clock`.
- **N3 — first prefs read on the main thread.** The first `RouteRepository` construction reads prefs (`readIds()` in `observedState`) on the main thread during `hiltViewModel()`. That is a read, not a write, so the requirement "writes never block the main thread" is met. It is the same as the other TASK-006 repositories.
- **N4 — choosing a city after a restore error when the data is still unreadable.**
  - Old: the chosen city was shown and the error cleared.
  - New: the change triggers a new resolve, which fails again, so the error stays and the choice is not shown.
  - In practice this is hard to reach, because a successful picker search loads and caches the data. It is a small nuance of D4.
- **N5 — `onRetry = { startObservation() }` no longer re-restores the route.** Old `startObservation()` also called `routeController.start()`. The Route card has its own restore retry, so this is acceptable. Mention it in the PR list alongside D2.
- **N6 — `docs/tasks/README.md` still shows 012 as TODO.** Per CLAUDE.md the PR must update it. TASK-011 did this in a final "docs: review and status" commit, so this is expected to follow.
- **N7 — stale picker lookup results.** A lookup still running when the picker is closed can write `routeResults`/`routeSearchError` afterwards. The old code had the same exposure while active; TASK-013 owns this.

## Human checks on device
1. Set a departure and destination, kill the process, relaunch. Both cities are restored and the arrival shows as a localized short date-time in the destination zone plus "HH:MM".
2. Change the device language or region. The arrival format follows the new locale.
3. Rotate while the route has a position. Remaining distance and arrival are kept.
4. Background the app for more than 5 s, then return. The route stays; remaining distance and arrival wait for a new fix.
5. Clear one endpoint, then clear all. The saved state is correct after relaunch.
6. Search in the picker, then search again quickly. Only the latest results show and the loading indicator ends.
7. Nearest-city via long press on the picker map still proposes a city.

## Verification performed
- **Source review:** full diff `origin/ai-modernization...HEAD` (2 commits) and full content of `RouteRepository.kt`, `RouteViewModel.kt`, `RouteModels.kt`, `RouteCard.kt`, the relevant `MainActivity.kt` sections, `CityRepository.kt`, `SharedPreferencesFlows.observedState`, `HiltSingletonScopeTest.kt`, all new and changed tests, the TASK-004 scenario 7 test, and the original app's `RouteCardViewPresenter.kt`.
- **CI:** PASS on ff5f70c6d5ad881346b14695cc7cd1da72f5cd53 (run 36576791341), as reported by the main session; I did not re-run it.
- **Not verified:** runtime behavior on a device (nothing here claims it works at runtime), exact on-device date-time strings for other locales, and the promo screenshots (no visual change was intended; card visuals are TASK-033).

Relevant files:
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/route/data/RouteRepository.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/route/ui/RouteViewModel.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/ui/route/RouteCard.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/di/ClockModule.kt
