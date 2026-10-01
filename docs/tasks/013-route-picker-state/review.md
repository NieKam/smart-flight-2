# TASK-013 — Review iteration 1

## Result
**PASS.** I found no blocking findings and nothing to escalate. The non-blocking suggestions are listed below.

## Acceptance criteria
| criterion | status | verified by |
|---|---|---|
| ViewModel tests: search success/failure/retry, nearest found/none/invalid, select/confirm, stale result ignored after close | MET | Source review of `RoutePickerViewModelTest`. It covers all listed cases, plus reopen for the other endpoint, a newer lookup replacing a running one, an explicit choice winning over the async current-city read, a closed picker ignoring actions, and restore from `SavedStateHandle`. CI reported green on e483e55. |
| Nearest draft becomes the selection | MET | VM test `the nearest city becomes the only result and the confirmable selection`. Compose test `RoutePickerTest.nearestCityFromTheViewModelIsSelectedAndConfirmable` wires the real VM, checks "Selected: Paris (France)" and that Confirm is enabled, then confirms and checks the saved preference. `GnssStatusScreenTest.routePickerNearestCityIsImmediatelyConfirmableDraft` is also updated. |
| State survives `ActivityScenario.recreate()` | MET | `MainActivityCharacterizationTest.recreationKeepsTheOpenCityPickerWithQueryAndSelection`: after recreate the picker title, the "Warsaw" query and the selected text are shown and Confirm is enabled. Process death is covered at VM level by the shared-`SavedStateHandle` tests. |
| No `getString` for picker errors in the Activity | MET | Code review. All `getString(R.string.route_*)` calls are gone from `MainActivity`. The only remaining `getString` in `RoutePicker.kt:153` is the map content description, not an error. |

Scope: all Activity picker fields and lambdas are removed. `RoutePicker` is stateless (no `remember` of UI state, no `validCity`). Errors are mapped to `route_error`, `route_no_city_at_location` and `route_invalid_city` in the composable. `GnssStatusScreenTest` is updated. Nothing from the out-of-scope list (map centering or size, auto-select of a single result, visual changes) was done. The `docs/tasks/README.md` status is still TODO. That matches the convention from TASK-012, where the status is updated in the final "review and status" commit.

## Blocking findings
None.

## Escalations
None.

## Specific verifications requested

**OOM diagnosis and fix (e483e55): confirmed.**
- **Why it looped:** the base code held the draft marker in `mutableStateOf` and the `AndroidView` `update` block both read it (`draftMarker?.let { remove }`) and wrote it (`draftMarker = Marker(...)`). `update` runs under a snapshot observer, so a write to a state it read schedules `update` again. Each run creates a new, unequal `Marker`, so the loop never settles whenever `selectedCoordinate != null`.
  - With a null coordinate, null is written over null and nothing is invalidated, which is why only "map archive + selected city" triggers it.
  - This was a pre-existing production bug: on a device the picker rebuilt its marker every frame once a city was selected. Under Robolectric the test never goes idle.
- **The fix:** `DraftMarkerHolder` is a plain `remember`ed object, so `update` has no snapshot reads.
  - `update` now runs only when AndroidView gets a new `update` lambda. Under strong skipping (the Kotlin 2.2 default) that lambda is memoized on its captures (`selectedCoordinate`, `context`, holder), so the marker is replaced only when `selectedCoordinate` changes.
  - Every run removes the previous marker from `map.overlays` before adding the new one. Even an extra recomposition-driven run cannot pile up markers, so there is no overlay leak.
  - If the LazyColumn item is disposed and recreated, both the holder and the MapView are recreated.
- **The regression test:** `RoutePickerTest.selectingCitiesOnTheOfflineMapSettles` composes the picker with a real archive in a tall window (the map item is composed), then selects Paris and Berlin. With the old code, the first selection would give `update` a non-null coordinate and the compose rule would never go idle, so it would time out or OOM. By source reasoning it would have caught the loop. I could not observe it failing against the old code, because the test was added in the same commit as the fix.

**A. RoutePickerViewModel: OK.**
- A separate VM is allowed by the task ("pick the smaller design and justify"), and the justification is in the KDoc: the picker has its own lifetime, saved input and lookup job, and shares only `RouteRepository` with the card.
- There is a single `StateFlow<RoutePickerState>`.
- One `lookupJob` at a time. It is cancelled by a new lookup, `open` or `close` (via `cancelJobs`, which also clears `lastLookup`), so TASK-012 N7 is addressed.
- `CancellationException` is rethrown in both catch sites.
- `SavedStateHandle` stores the endpoint name, the query and the selected id, not the results. Restore in `init` runs only when an endpoint is saved.

**B. Typed errors: OK.** `RoutePickerError` is a sealed interface with the three objects, mapped in `RoutePicker.kt` with a `when`. Retry appears only for `SearchFailed`.

**C. Nearest-city draft fix: OK.** `onFound(Nearest)` sets `results = listOf(city)` and `setSelected(city)`. An invalid city gives `InvalidCity`, a null city gives `NoCityAtLocation`, and both leave the selection unchanged. This matches the original app: `FindCityPresenter.onCityFound` displays the city and enables Confirm.

**D. `collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)`: correct and effective here.**
- It works because `updateQuery` sets the `MutableStateFlow` synchronously on the main thread with no operators in between. The collector resumes immediately, and the Compose state is written before `onValueChange` returns.
- It is a recognised workaround, but not the most idiomatic option. The approaches Google documents are a `TextFieldState` (state-based `TextField`) or query text held as Compose `MutableState` in the VM. See NON_BLOCKING N2.

**E. RouteViewModel: OK.** `choose`, `search` and `nearest` are removed and `grep` finds no other callers. Invalid-city rejection is kept in `RoutePickerViewModel.confirm()` (`validCity` check) and in the `canConfirm` UI guard, and it is tested (`an invalid selection cannot be confirmed`). `RouteViewModelTest` now writes through `RouteRepository.set`, and the old `choose` rejection test moved to the picker VM tests.

**F. Developer-reported behavior changes:**
1. **Long-press selects the nearest city, Confirm enabled:** required by the task and matches the original app. Accept.
2. **Open state, query and selection survive rotation and process death; results survive rotation only:** required, and results are excluded by design per the task's risk note. Accept.
3. **Typed error display:** required by the task. The old display showed "could not be loaded" plus the raw message plus Retry even for "no city", which was misleading. Accept.
4. **Invalid long-press shows "No city found" in the error area and clears results:** a small side effect. Found-null already cleared results in the base code, so treating an invalid coordinate the same way is consistent. Accept (see N3).
5. **Retry repeats the last lookup:** the task defines `retry()` without restricting it to text search. The base behavior after a failed long-press was to re-run the text search, possibly with an empty query, which was wrong. The original app had no retry. Accept.
6. **Current city read asynchronously on open:** acceptable. `RouteRepository` holds only ids, and `CityRepository` caches after the first read. Confirm is briefly disabled. Accept (see N4).
7. **Marker no longer rebuilt every frame:** a genuine fix of a pre-existing production defect. Accept.

## Non-blocking findings
- **N1 `RoutePickerViewModel.kt` `confirm()`:** it does not check `loading`, while `canConfirm` does. The UI disables the button, so this is safe today. Using `if (!current.canConfirm) return false` would keep the VM and UI rule in one place.
- **N2 `MainActivity.kt` (picker state collection):** `Dispatchers.Main.immediate` works but is subtle. If the query ever passes through `map` or `stateIn`, the lag returns. A future cleanup (for example TASK-016 or 032) could move the query to `TextFieldState` or VM-held Compose state. The explanatory comment is present.
- **N3 `RoutePickerViewModel.nearest(null)`:** it clears results and cancels a running search. The base code left both untouched and only showed a message under the map. Keeping `results` for an invalid-point long-press would be a slightly closer match to "all other behavior unchanged". This is minor.
- **N4 `open()`:** the current city could be taken synchronously from `RouteViewModel`'s resolved state to avoid the brief empty selection. That would couple the two VMs, so the current choice is defensible.
- **N5:** `RoutePickerState` and `RoutePickerError` live in `route/` next to `RouteState`, which follows the existing convention. They are UI-state types, so they may move into `route/ui` during a later package cleanup.

## Human checks on device
1. Type quickly in the city search field, including IME composition, autocorrect, and mid-text cursor edits. Check there are no dropped characters and no cursor jumps.
2. Open the picker, type a query, search, select a city, then rotate. The picker stays open with the query, results, selection and Confirm enabled.
3. Same as step 2 with "Don't keep activities" or `adb shell am kill` while in the background. The query and selection come back; the results do not.
4. With the offline map ready, long-press on the picker map. The nearest city is listed and shown as "Selected", Confirm is enabled, the marker appears, and Confirm saves it to the route card.
5. Select several cities in turn. The marker moves each time, only one marker is visible, and CPU and memory stay flat (the loop is gone).
6. Open the picker for an endpoint that already has a city. It appears as selected after a moment.
7. Long-press outside valid coordinates, or where no city exists. "No city found at this location." is shown with no Retry.
8. Back and Cancel close the picker and leave the route unchanged.

## Verification performed
- **Source review:**
  - The full diff against `origin/ai-modernization` (four commits, 1400a37..e483e55).
  - Full `RoutePicker.kt`, `RoutePickerViewModel.kt`, `RoutePickerState.kt` and `RouteRepository.kt`, and the relevant parts of `RouteViewModel.kt`.
  - All new and changed tests.
  - The original app's `FindCityPresenter.kt` and `FindCityActivity.kt` in ~/smart-flight, for long-press selection and error behavior.
  - A `grep` for remaining `choose`, `getString(R.string.route_*)` and `routeNearestDraft` usages.
- **CI result as reported:** round 1 on 7feadf0 (run 36619440567) failed with OOM in 11 tests. The main session reported CI green on e483e55 (run 36622005496). I did not check CI myself.
- **Not verified:**
  - Anything at runtime.
  - That the new regression test fails without the fix (it was added together with the fix).
  - Real TextField typing behavior with `Main.immediate` on a device.
  - Process-death restore end to end (only VM-level tests exist).
  - Map long-press gesture handling on a device.

Relevant files:
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/route/ui/RoutePickerViewModel.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/route/RoutePickerState.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/ui/route/RoutePicker.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt
- /home/ai-dev/smart-flight-2-modern/app/src/test/java/kniezrec/com/flightinfo/route/ui/RoutePickerViewModelTest.kt
- /home/ai-dev/smart-flight-2-modern/app/src/test/java/kniezrec/com/flightinfo/ui/route/RoutePickerTest.kt
- /home/ai-dev/smart-flight-2-modern/app/src/test/java/kniezrec/com/flightinfo/MainActivityCharacterizationTest.kt
