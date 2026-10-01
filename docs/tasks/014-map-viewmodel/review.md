# TASK-014 — Review iteration 1

## Result
**PASS.** I found no blocking findings and nothing needs escalating. One non-blocking finding (N1, map panning) needs attention before the task is closed: reading the code, the scrolling page probably takes over vertical map drags. The task only requires a fix if the check on a device shows it is broken.

## Acceptance criteria
| criterion | status | verified by |
|---|---|---|
| `MapViewModelTest`: loading → ready, failure → unavailable → retry, first fix centered once, marker course from bearing (0 when absent) | Met | Source review of `MapViewModelTest` (13 tests, including open failure → Unavailable → retry, and restart after the 5 s timeout). CI reported green. |
| `MapArchiveRepository` suspend tests (existing archive reused, corrupt archive re-extracted, failed copy leaves no temp file) | Met | `MapArchiveRepositoryTest`: reuse with 0 opens; a corrupt and an empty archive are both copied again; a failed copy leaves no `.partial` file and no archive. |
| Compose glyph test under `@Config(qualifiers="pl")` | Met as written; weak (see N2) | `MapCardTest.buttonsShowTheirGlyphsUnderANonEnglishLocale` |
| `invalidOfflineArchiveReportsOpenFailureWithoutUsingNetworkFallback` runs without `@Ignore` and passes | Met | `@Ignore` removed in `GnssStatusScreenTest`. CI reported green. |
| `MapStateTest` and `MapZoomPolicyTest` pass | Met | Rewritten for `MapTracking`/`MapRules` with the same assertions. CI reported green. |
| Overlay sync test: no position → no marker; A → one marker at A; B → same marker moved; course applied | Met | `MapOverlaysTest` builds a real osmdroid `MapView` under Robolectric. It also covers route overlays being added and removed without touching the plane marker. |
| `MapCard` recomposes with a new position (`stateDescription` switch) | Met | `MapCardTest.readyMapRecomposesWithEveryNewPositionAndMovesOnePlaneMarker` |
| Plane marker appears within one fix and follows movement | HUMAN | On device |
| Map pans inside the scrolling dashboard | HUMAN (likely to fail, see N1) | On device |

## Blocking findings
None.

## Escalations
None.

## Non-blocking findings
**N1 — map panning inside the scrolling page (item H).** File: `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/MapCard.kt:253-298`. `GnssStatusScreen.kt:112` puts the card inside `verticalScroll`.
- The original app handles this explicitly. `~/smart-flight/.../base/BaseMapView.kt:80-90` calls `parent.requestDisallowInterceptTouchEvent(true)` on ACTION_DOWN and `(false)` on ACTION_UP.
- The rewrite has no equivalent. My grep found no `requestDisallowInterceptTouchEvent`, `pointerInteropFilter` or `nestedScroll` anywhere in `app/src/main`.
- Why it probably breaks: as far as I recall the source (not checked for this project's version), Compose's `AndroidView` passes MOVE events to the view only in the Final pass unless the view has asked the parent not to intercept. So `verticalScroll` sees the drag first in the Main pass. Past touch slop it consumes the drag and the map gets a cancel. I believe osmdroid's `MapView` does not make that request itself, which is why the original subclassed it.
- Expected result: vertical (and probably pinch) gestures on the map scroll the page instead of panning the map. Horizontal pans probably still work.
- The task makes the fix depend on the device check ("if broken, fix in this task"), so this is not blocking. Recommendation: fix it now with the original's approach, since it costs little. Otherwise the device check will most likely fail and reopen TASK-014.
- Proposed fix: a small `MapView` subclass, or an `OnTouchListener` set in `factory`, that calls `parent.requestDisallowInterceptTouchEvent(true)` on DOWN and `(false)` on UP/CANCEL.

**N2 — the glyph test cannot tell old code from new yet (item F).**
- The rewrite has no `values-pl` folder yet (only `values`). The original has one; README maps its return to TASK-036.
- So under the `pl` qualifier the descriptions are still English, and the test would also pass on the old `startsWith("Expand")` code.
- The fix itself is structural: `MapButtonKind` carries both glyph and description, and nothing parses the text any more. That meets the requirement "glyphs correct regardless of locale", and the test meets the criterion as written.
- Recommendation: keep the test and note in TASK-036 that it becomes meaningful once `values-pl` exists. No stronger test is needed now.

**N3 — an old map state is briefly shown when the map restarts after more than 5 s (item I.4).**
- `stateIn` keeps the last `Ready` value. After returning from the background, `collectAsStateWithLifecycle` first renders the old `Ready` (old position), then `Loading`, then the new `Ready`. `MapViewModelTest.kt:203` (`states.drop(1)`) documents this.
- The effect is an extra `MapView` created and disposed straight away. It is harmless for correctness: `centered` and `openFailed` are reset only after `prepare()` succeeds, so a call from the old map cannot leak into the new observation.
- The other card ViewModels behave the same way. Optional improvement: map the value back to `Loading` when a new observation starts, or accept it.

**N4 — `HiltSingletonScopeTest` lost the activity-vs-service `LocationRepository` identity check (item G).**
- `serviceInstancesShareOneLocationRepository` still exists, and `MainActivityCharacterizationTest.activityAndServiceShareOneLocationRegistration` covers the behavior end to end.
- `LocationRepository` is `@Singleton`. The coverage loss is acceptable.
- Optional: restore the check through an `@EntryPoint` if an explicit ViewModel-vs-service identity check is wanted.

**N5 — the GNSS card's "Try again" still restarts only the map (item G).**
- `MainActivity.kt` wires `onRetry = mapViewModel::retry`. This matches the old behavior (`startObservation()` only restarted the map load), so it is equivalent.
- It does not retry anything related to GNSS. This existed before and belongs to a later task.

**N6 — `refreshPermissionState` equivalence (item G): confirmed.**
- The old code stopped background monitoring when `!granted` (foreground or not) and never in the `foreground && granted` case. So `if (!granted) stopBackgroundMonitoring()` is exactly equivalent for the service.
- The map part (restart on resume, stop when paused) is now owned by the ViewModel and the lifecycle-aware collection.

**N7 — README status.** `docs/tasks/README.md` still lists 014 as TODO. The PR must update it, as TASK-012 and TASK-013 did.

**N8 — `MapUiState.Inactive` is no longer produced by the app.** It is only the default parameter of `GnssStatusScreen` in tests and previews. The task lists it in the state, so keeping it is fine.

## Items A–I verified by reading the code
- **A (MapViewModel):**
  - A single `stateIn(WhileSubscribed(5_000), Loading)`.
  - Each observation runs `flow { Loading; prepare(); Ready… }` behind `retries.onStart{emit}.flatMapLatest`, so `retry()` and re-subscription both restart it, and cancelling the scope cancels `prepare()`.
  - `CancellationException` is rethrown.
  - Fixes are gated by `confirmedLocationEnabled.flatMapLatest`, and `LocationRegistrationException` is swallowed by `catch`. This matches the pattern in `NearbyCityViewModel`.
  - `centerRequest` is derived from `MapTracking.firstFix` and the `centered` StateFlow, and cleared by `onCentered()`. It is reset for each observation after `prepare()`.
  - `onMapOpenFailed()` produces `Unavailable`, and `transformWhile` ends the observation (the test checks that fix registrations drop to 0).
  - `retry()` before anyone collects is dropped (SharedFlow without subscribers), which the test checks.
- **B (MapArchiveRepository):**
  - `mutex.withLock { withContext(io) { … } }`. Because `withContext` waits for its blocking block before the lock is released, a cancelled copy that is still writing blocks the next preparation. The comment explaining this is correct.
  - It reuses `MapArchiveCopier.validate`, which also checks for an empty file.
  - A failure deletes the temporary file and rethrows. The executor and callback API are gone.
- **C (Compose update pitfall):**
  - `update` reads only captured immutable values (`state`, `largerMapZoom`, `routeOverlay`) and the non-snapshot fields of `MapInstance`.
  - It writes `showMaximumZoomWarning` through a callback, but only `MapCard` reads that value, not `update`. On recomposition `OfflineMap` is skipped because its arguments and the remembered lambda are unchanged, so there is no self-loop. This is the same as before.
  - `onCentered()` writes a ViewModel `MutableStateFlow`, not Compose state. It triggers a single extra emission with `centerRequest = null`.
  - `MapInstance` and `MapOverlays` hold no Compose state.
  - The marker bug fix holds: every fix gives a new `Ready` instance, so `OfflineMap` recomposes, the `update` lambda changes, and `update` runs again.
  - `MapCardTest` fails if `update` stops re-running. It cannot be run against the old API (that code no longer compiles with it), but it exercises exactly the path that used to be skipped.
  - Drawables, anchors, the CYAN polyline and the add order match the old code line for line. `dispose()` behaves as before.
- **D (F1 fix):**
  - When `provider.archives.isEmpty()`, the code calls `provider.detach()`, throws, and reports `onOpenFailure()`.
  - The fallback `MapView(context)` has `setUseDataConnection(false)`, the same as before, so no network fallback is used.
- **E (osmdroid configuration):**
  - Loaded once in `SmartFlightApplication.onCreate` with the "osmdroid" preferences file, before any Activity and therefore before any `MapView`.
  - Robolectric uses this Application from the manifest; no `robolectric.properties` or `HiltTestApplication` override was found.
  - No other code sets the user agent or tile cache, so the defaults are unchanged.
  - The disk I/O moves from `factory` (main thread) to the Application `onCreate` (main thread). The task's risk note explicitly allows this.
- **I (reported behavior changes):**
  - 1 and 2 are required by the task.
  - 3 is required ("glyphs correct regardless of locale").
  - 4 follows directly from the ViewModel, `WhileSubscribed` and survive-config-change conventions that the task and CLAUDE.md require. It matches TASK-009 to TASK-013. Acceptable, as long as the PR lists it.
  - 5 is a sensible replacement: the old code re-centered on the next first fix because rotation reset the rules. The recreated map now starts on the known position at zoom 3. Acceptable; it must be listed in the PR.

## Human checks on device
1. With GPS locked on the dashboard, the plane marker appears within about one fix and moves and rotates while walking or driving. This includes the case where the map was already Ready before the first fix.
2. Drag the map vertically, horizontally and with a pinch while it sits inside the scrolling dashboard. Report whether the page takes over the gesture (N1 predicts it will for vertical drags).
3. Rotate the device after the first fix: the map starts on the current position at zoom 3 and is not re-centered unexpectedly.
4. Background the app for less than 5 s, then more than 5 s: the map and viewport are kept in the first case; the second shows Loading and then re-centers on the next first fix. Also watch for a brief flash of the old map state (N3).
5. Toggle the "larger map zoom" setting with the map open: maximum zoom 6 or 9 and the maximum-zoom caption behave as before.
6. Replace `cacheDir/osmdroid.zip` with garbage and launch: the archive is re-extracted and the map works. If osmdroid still cannot open it, "Map unavailable" and "Try again" appear, and no blank map is shown.
7. First launch: check startup time and StrictMode, if enabled, now that osmdroid's configuration loads in `Application.onCreate`.

## Verification performed
- Source review of the full diff `origin/ai-modernization...HEAD` (22 files) and the full current contents of `MapCard.kt`, `MapViewModel.kt`, `MapArchiveRepository.kt`, `MapOverlays.kt`, `MapUiState.kt`, `MapState.kt`, `MainActivity.kt` (the relevant parts) and the new or updated tests.
- Compared with the original `~/smart-flight/.../base/BaseMapView.kt` for touch handling and osmdroid configuration; the original never calls `Configuration.load`.
- Checked `NearbyCityViewModel` for consistency of the ViewModel pattern, and the README for task status.
- CI: reported by the main session as PASS on `4cacd639d591c7817a226f1ded64a07889c8cdc3` (run 36626374577). I did not check it myself.
- Not verified:
  - Anything at runtime: marker movement, gesture handling, startup cost, and how osmdroid behaves on a real device.
  - The osmdroid and Compose internals behind N1 and item E come from memory of the library sources, not from reading the versions this project uses.
  - I did not open the promo screenshots, because this task makes no visual changes: drawables, colors and overlay order are the same line for line.

Relevant files:
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/ui/gnss/MapCard.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/map/ui/MapViewModel.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/map/data/MapArchiveRepository.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/map/ui/MapOverlays.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/SmartFlightApplication.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt
- /home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/base/BaseMapView.kt
