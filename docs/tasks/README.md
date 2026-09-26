# Smart Flight migration plan

## Overview
The rewrite (`app/`) is migrated to the target architecture from `CLAUDE.md` (Compose UI + Hilt ViewModels exposing `StateFlow`, repositories exposing `Flow`/`suspend` over `callbackFlow` data sources, Hilt with KSP, package by feature with layers inside) and then brought back to feature/behavior parity with the original app (`~/smart-flight`), presented with a modern UI in the original app's color palette.

The order follows four phases. Every task leaves the app building, tests green and features working.
1. **Foundation and safety net (001–004):** version catalog (coroutines, lifecycle-compose), Compose UI tests moved to Robolectric so CI runs them, dead-code removal, characterization tests pinning `MainActivity` orchestration.
2. **DI (005):** KSP + Hilt as a dedicated step, starting with a CI compatibility check on AGP 9.
3. **Architecture migration (006–017):** settings repositories, one location/GNSS repository, removal of the global bridge, one ViewModel per card, city/route/map data layers, slim `MainActivity`, design tokens. These tasks preserve behavior; every behavior they must keep "as is" until a later parity task is named in the task. Exception: TASK-014 fixes the plane marker not following fixes (a by-product of the immutable map state).
4. **Parity restoration (018–036):** built on the target architecture, so no code is migrated twice.

### Planner decisions (apply to all tasks)
- **CI verification (human decision):** no emulator and no instrumented tests in GitHub Actions. Compose UI tests run on the JVM with Robolectric inside `testDebugUnitTest` where they pay off (TASK-002 moves the existing ones and retires `androidTest`). Pixel-level color checks use Robolectric native graphics (`captureToImage`). Everything else in the UI — real sensors, GPS, notifications, visual comparison with the screenshots — is marked "HUMAN on device".
- **Release identity (human decision):** no Play Store release. `versionCode = 1`, `versionName = "1.0.0"` (set in TASK-001). No migration of the original app's settings or route.
- **Kotlin/KSP/Hilt setup (human decision):** TASK-005 first verifies KSP + Hilt with AGP 9 built-in Kotlin in CI; if that fails, adding `org.jetbrains.kotlin.android` is allowed. kapt is never used.
- **Palette (human decision):** the app's colors match the original palette (`~/smart-flight/app/src/main/res/values/colors.xml`, `styles.xml`, `promo/*.png`) on every screen, card, dialog and on the map. TASK-018 defines the token table and a palette-guard test; every later visual task uses only those tokens.
- **Settings storage:** keep SharedPreferences (same files and keys), wrapped in Flow repositories (TASK-006). DataStore is not adopted: it adds a dependency and a storage migration while synchronous window-flag application at startup favours SharedPreferences.
- **ViewModel granularity:** one `@HiltViewModel` per card/overlay (GNSS, flight parameters, course, horizon, nearby, route, route picker, map, settings, permission); the dashboard composes card containers that call `hiltViewModel()`.
- **Navigation:** no navigation library; Settings, About and the city picker stay overlays with saveable state (one screen does not justify a nav graph).
- **Use cases / domain layer:** none planned. The pure functions (architecture K2) stay feature-local; each has a single consumer.
- **Modularization:** not proposed. Architecture review K9 found no problem that modules would solve at this size (~50 source files, one screen); revisit if a second screen family or build-time issues appear.
- **Package layout:** `kniezrec.com.flightinfo.<feature>.{data,ui}` (`domain` only if ever needed); `ui/theme` is the shared design package; DI modules next to the feature they bind.
- **Third-party libraries:** no chart library (Canvas bar chart, TASK-022). `lottie-compose` is added to play the original `loading.json` (human decision, TASK-023).
- **Card order (delegated to the planner):** Satellites, Course, Horizon, Flight parameters, Nearby city, Route, Map; permission card first when not granted. Reasoning in TASK-034.
- **Map plane marker (human decision):** rotated by the GPS track when moving, by the compass when stationary (TASK-030). It must be clearly visible: TASK-014 fixes the update trigger, TASK-030 fixes drawable, color, outline, z-order and tap behavior.
- **Hidden cards (human decision):** a "Show hidden cards" setting restores cards hidden because of a missing sensor (TASK-029).
- **Launcher icon (human decision):** restore the original (TASK-034).

## Tasks

| NNN | Title | Depends on | Status |
|---|---|---|---|
| 001 | [Build foundation: coroutines, lifecycle-compose, coroutine tests, versionName 1.0.0](001-build-foundation/task.md) | none | TODO |
| 002 | [Run Compose UI tests on the JVM (Robolectric), retire androidTest](002-compose-tests-on-jvm/task.md) | 001 | TODO |
| 003 | [Remove dead and test-only production code](003-remove-dead-code/task.md) | 002 | TODO |
| 004 | [Characterization tests pinning dashboard orchestration](004-characterization-tests/task.md) | 002, 003 | TODO |
| 005 | [Introduce KSP + Hilt as a dedicated DI step (CI compatibility check first)](005-hilt-di/task.md) | 001, 004 | TODO |
| 006 | [Observable settings repositories](006-settings-repositories/task.md) | 005 | TODO |
| 007 | [Location and GNSS repository (callbackFlow); service collects it](007-location-repository/task.md) | 005 | TODO |
| 008 | [Remove BackgroundMonitoringBridge and dead foreground paths](008-remove-monitoring-bridge/task.md) | 007 | TODO |
| 009 | [GNSS status and flight parameters ViewModels](009-gnss-flight-viewmodels/task.md) | 008 | TODO |
| 010 | [One orientation Flow; Course and Horizon ViewModels](010-orientation-course-horizon/task.md) | 008 | TODO |
| 011 | [City repository and Nearby city ViewModel](011-city-repository-nearby/task.md) | 005, 008 | TODO |
| 012 | [Route repository and Route card ViewModel](012-route-repository-viewmodel/task.md) | 006, 011 | TODO |
| 013 | [Route picker state holder, typed errors, nearest-city draft fix](013-route-picker-state/task.md) | 012 | TODO |
| 014 | [Map ViewModel, suspend archive, osmdroid config at startup, marker follows fixes](014-map-viewmodel/task.md) | 006, 008, 011 | TODO |
| 015 | [Settings, About and permission state out of the Activity](015-settings-about-permission-state/task.md) | 006 | TODO |
| 016 | [Dashboard composition, slim MainActivity, state survives rotation](016-dashboard-screen-slim-activity/task.md) | 009, 010, 011, 013, 014, 015 | TODO |
| 017 | [Design tokens in ui/theme, shared unit labels](017-design-tokens/task.md) | 016 | TODO |
| 018 | [Original palette on every screen (theme, text hierarchy, dialogs, palette guard)](018-brand-theme/task.md) | 017 | TODO |
| 019 | [GPS-disabled prompt and live GNSS states](019-gps-disabled-prompt/task.md) | 016 | TODO |
| 020 | [Flight parameters: pressure without GPS, smoothed vertical speed](020-flight-parameters-parity/task.md) | 016 | TODO |
| 021 | [Route: remaining distance and ETA with only a destination](021-route-destination-only/task.md) | 016 | TODO |
| 022 | [Satellite signal-strength bar chart](022-satellite-signal-chart/task.md) | 018 | TODO |
| 023 | [Original Lottie searching animation and window tip](023-satellite-search-animation/task.md) | 022 | TODO |
| 024 | [Compass, horizon, Settings, About without location permission](024-dashboard-without-location-permission/task.md) | 016 | TODO |
| 025 | [Background notification on Android 13+ and plane notification icon](025-background-notification-permission/task.md) | 016, 024 | TODO |
| 026 | [Compass sensor fallback, smoothing, rounding](026-compass-sensor-fallback/task.md) | 016 | TODO |
| 027 | [Compass rose with N/E/S/W and animated plane](027-compass-rose/task.md) | 018, 026 | TODO |
| 028 | [Horizon calibration persistence, long-press reset, filtering](028-horizon-calibration/task.md) | 016 | TODO |
| 029 | [Hide unsupported Course/Horizon cards; "Show hidden cards" setting](029-hide-unsupported-cards/task.md) | 006, 015, 026, 028 | TODO |
| 030 | [Map: great-circle route line and visible purple plane marker](030-map-route-line-and-plane-marker/task.md) | 014, 016, 018, 026 | TODO |
| 031 | [Map controls, real expand, max-zoom tip leading to Settings](031-map-controls-and-zoom-tip/task.md) | 015, 016, 018, 030 | TODO |
| 032 | [City picker: large centering map, single result auto-selected](032-city-picker-map/task.md) | 013, 018 | TODO |
| 033 | [Route card visuals](033-route-card-visual/task.md) | 018, 021 | TODO |
| 034 | [Toolbar with overflow menu, original launcher icon, card order](034-header-launcher-card-order/task.md) | 018, 024 | TODO |
| 035 | [Displayed distances on the WGS84 ellipsoid](035-ellipsoidal-distances/task.md) | 011, 021 | TODO |
| 036 | [Polish localization and translation-completeness check](036-polish-localization/task.md) | 019, 020, 021, 022, 023, 024, 025, 027, 028, 029, 030, 031, 032, 033, 034 | TODO |

## Coverage

### Parity review (`docs/review/parity.md`)

| Finding | Task |
|---|---|
| Screenshots / palette section | 018 (palette tokens, theme, text hierarchy, dialogs, palette guard), 022, 027, 030, 031, 032, 033, 034 (per-screen visuals using the tokens) |
| 1. Satellite signal bar chart gone | 022 |
| 2. GPS/location off never told; stale satellites mid-session | 019 (data prerequisites in 007, 008) |
| 3. Route: no remaining distance/ETA with destination only | 021 |
| 4. Pressure hidden until first GPS fix | 020 (pinned in 004, preserved in 009) |
| 5. Compass needs rotation vector; smoothing dropped | 026 |
| 6. Compass rose replaced by small arrow | 027 |
| 7. Compass/horizon/Settings/About gone without location permission | 024 |
| 8. Background notification gone on Android 13+ | 025 |
| 9. Straight cyan route line instead of great-circle purple | 030 |
| 10. Picker map not centered, too small | 032 |
| 11. Stock template theme with dynamic colors | 018 |
| 12. Polish localization missing (+ English-dependent map glyph) | 036 (glyph fixed in 014) |
| 13. Nearest-city lookup reads whole DB per fix | 011 |
| 14. Upgrading users lose settings/route; versionCode 1 | deferred (human decision: no Play Store release; version set to 1 / "1.0.0" in 001) |
| 15. Heading truncated instead of rounded | 026 |
| 16. Vertical speed not smoothed; first value blank | 020 |
| 17. Horizon calibration resets on resume; no long-press reset; no filter | 028 (behavior preserved in 010) |
| 18. No option to hide unsupported compass/horizon card | 029 (plus "Show hidden cards") |
| 19. No searching animation / window tip | 023 |
| 20. Max-zoom tip no longer leads to Settings | 031 |
| 21. Map marker is a cyan star following GPS track | 030 (update trigger fixed in 014) |
| 22. Map buttons are text glyphs; expand barely enlarges | 031 |
| 23. Route card became text buttons | 033 |
| 24. Header, launcher icon, notification icon changed | 034 (header, launcher), 025 (notification icon) |
| 25. Rotation resets dashboard state | 016 (enabled by 009–015) |
| 26. Spherical distances instead of WGS84 | 035 |
| 27. Card order changed | 034 (planner-chosen order, reasoning there) |
| MODERNIZATION: ft/min fixed | deferred (accepted; kept by 009/020) |
| MODERNIZATION: city local time / DST fixed | deferred (accepted; kept by 011) |
| MODERNIZATION: nearest city by true distance | deferred (accepted; kept by 011, 035) |
| MODERNIZATION: horizon redesigned | deferred (accepted; kept by 028) |
| MODERNIZATION: arrival shown as full date-time | deferred (accepted; kept by 012, 021, 033) |
| MODERNIZATION: background monitoring stops at first fix | deferred (accepted; kept by 007, 008, 025) |
| MODERNIZATION: location request settings changed | deferred (accepted; kept by 007) |
| MODERNIZATION: inline picker feedback; single result not auto-selected | inline feedback: deferred (accepted); auto-select: 032 (restored, see disagreements) |
| MODERNIZATION: settings in-app | deferred (accepted; kept by 015) |
| MODERNIZATION: minSdk 31 | deferred (declared project decision) |
| MODERNIZATION: accessibility improvements | deferred (accepted; every UI task must keep them) |
| Inventory: "Connected to N satellites" | deferred (accepted; kept by 022) |
| Inventory: speed/VS/altitude units correct | deferred (accepted; kept by 009, 020) |
| Inventory: route distance between cities; clear one/all | deferred (accepted; kept by 012, 021, 033) |
| Inventory: city picker search/list/long-press/confirm present | 013, 032 |
| Inventory: offline map tiles zoom 1–6 / 1–9 | deferred (accepted; kept by 014) |
| Inventory: map "my location", expand/shrink | 031 |
| Inventory: About dialog | deferred (accepted; kept by 015) |
| Inventory: keep screen on / force portrait | deferred (accepted; kept by 006) |
| Inventory: permission card | 024 |
| Not verified: city picker contrast | 018 (HUMAN) |
| Not verified: map panning inside scrolling page | 014 (HUMAN; fix there if broken), re-checked in 031, 032 |
| Not verified: horizon roll direction / pitch sign | 010 (rotation-matrix tests), fix in 028 if wrong |
| Not verified: size of lookup cost | 011 (measured in PR) |
| Not verified: rotation-vector availability | 026 (fallback planned regardless; position kept by human decision) |
| Not verified: foreground-service start failing silently | 008 (dashboard no longer depends on the service) |
| Not verified: old screenshots predate features | deferred (informational) |

### Architecture review (`docs/review/architecture.md`)

| Finding | Task |
|---|---|
| F1. UI state in the Activity, not ViewModels | 009, 010, 011, 012, 013, 014, 015, 016 |
| F2. MainActivity is composition root + router + logic host | 008, 009–015 (incremental), 016 (final) |
| F3. Global BackgroundMonitoringBridge; no single location source | 007, 008 |
| F3 correctness note: unsynchronized bridge vars | 008 |
| F4. Callback/Executor async model, no coroutines | 007 (location/GNSS), 009 (pressure), 010 (orientation), 011 (city), 012/013 (route), 014 (map archive) |
| F5. No DI, no Application class | 005 |
| F6. Settings without observable source of truth; commit() on main | 006 (route prefs in 012) |
| F7. Dead dual-path foreground registration | 004 (pinned), 008 |
| F8. Android type in RouteController; persistence in state holder | 012 |
| F9. City data layer without caching; duplicated asset handling; map archive executor | 005 (single instance), 011, 014 |
| F10. Logic/presentation leaks across UI boundary; map state not observable | 011 (local time), 012 (arrival/duration), 013 (picker errors/validation), 014 (map state, glyph, marker update) |
| F11. 38-parameter god composable (nearest draft never passed) | 013 (bug), 016 (composition) |
| F12. Untestable orchestration; test infra mismatch | 001 (coroutines-test), 002 (Compose on JVM, androidTest retired), 003 (template tests), 004, 006 (prefs fakes) |
| F13. Missing build dependencies and tooling | 001 (coroutines, lifecycle-compose), 005 (KSP, Hilt); Java 11 target bullet: deferred |
| F14. Three interfaces and two adapters for one sensor | 010 |
| F15. Dead or test-only production code | 003 (`LocationGnssMonitoringSession.isActive` in 007) |
| F16. Thin indirections (coordinator, display applier, retry(isForeground), version provider) | 006 (display applier), 010 (coordinator, retry), 015 (version provider) |
| F17. Design tokens scattered; unit mapping duplicated | 017 (values changed to the original palette in 018) |
| F18. osmdroid config in composables; service depends on MainActivity | 014 (osmdroid); service → MainActivity: deferred |
| K1. Platform-interface seam | KEEP — preserved in 007, 009, 010, 015 |
| K2. Pure computation functions | KEEP — preserved in 009, 010, 011, 012, 014, 015, 035 |
| K3. Sealed/data `*State` types | KEEP — preserved in 009–014 |
| K4. Stateless cards | KEEP — preserved in 009–016 and all visual tasks |
| K5. Package by feature | KEEP — layers added inside features in 006–016 |
| K6. Shared sensor ownership; service-owned background lifetime | KEEP — preserved in 007, 008, 010, 025 |
| K7. Atomic asset extraction | KEEP — becomes shared helper in 011, used in 014 |
| K8. Injected clocks and test seams | KEEP — 005 (Hilt clock), preserved in 011, 012; service seams kept in 005, 007, 008 |
| K9. Build basics; no modularization | KEEP — preserved in 001; modularization deferred |
| Section 3: DI inventory | 005 (singletons), remaining sites in 006–016 |
| Not verified 1: Hilt/KSP with AGP 9 built-in Kotlin | 005 (CI check first; `org.jetbrains.kotlin.android` fallback approved) |
| Not verified 2: rotation behavior / display rotation | 010, 016 |
| Not verified 3: city lookup performance | 011 |
| Not verified 4: dead foreground paths | 004 (scenario 9), 008 |
| Not verified 5: routeNearestDraft never reaching picker | 013 |
| Not verified 6: thread-safety of bridge and route prefs | 008, 012 |
| Not verified 7: parity not compared | deferred (covered by the parity review) |

### Planner findings (not in the reports; verified in code)

| Finding | Task |
|---|---|
| Plane marker not updated on new fixes: `MapCard`/`OfflineMap` receive the same mutable `MapSessionRules` and are skipped (strong skipping), so `AndroidView.update` (`ui/gnss/MapCard.kt:300-339`) does not run per fix | 014 |
| Plane marker drawn under the route line and pins (added first, `MapCard.kt:331` vs `:351-366`); cyan star with poor contrast on light tiles; empty info window on tap | 030 |
| Non-palette colors outside the theme: map buttons #DD25133F (`MapCard.kt:182`), route pins #4DD0E1/#FFB74D, template `res/values/colors.xml` | 018 (tokens, guard), 030 (pins), 031 (buttons) |
| Original launcher adaptive-icon XMLs are unused template files; the real icon is the PNG mipmaps | 034 |

## Deferred / rejected

| Finding | Reason |
|---|---|
| Parity 14: upgrading users lose settings/route; `versionCode` 1 (former TASK-036 "Migrate original app settings/route; versionCode for update") | Removed by human decision: the rewrite is not released on the Play Store, so there is no upgrade path from the original app. `versionCode` stays 1 and `versionName` is "1.0.0" (TASK-001). The new settings/route/tip/hidden-card keys start empty. Revisit only if a Play Store update release is planned (it would need an import of `LocalPrefs` keys, a versionCode > 49 and the original signing key). |
| F13 bullet: `compileOptions` Java 11 while CI uses JDK 17 | Not blocking (the review says so); no feature needs Java 17 bytecode. Revisit if a library requires it. |
| F18 bullet: service builds a `PendingIntent` to `MainActivity` | Standard Android practice for a single-Activity app; an injectable launch intent adds an abstraction without benefit (the review marks it optional). |
| Modularization (CLAUDE.md "propose after review") | Not justified now (architecture K9): one screen, ~50 files. Revisit when a second feature area or build-time pain appears. |
| Running UI/instrumented tests on an emulator in CI (F12 planner decision) | Rejected by human decision. Compose tests run on the JVM with Robolectric (TASK-002); the `androidTest` source set is retired; the rest is HUMAN on device. |
| DataStore instead of SharedPreferences (F6 planner decision) | Rejected: see planner decisions above. |
| Parity MODERNIZATION items (ft/min fix, DST fix, true-distance nearest city, horizon redesign, arrival date-time, first-fix stop, location request settings, inline picker feedback, in-app settings, accessibility) | Accepted as-is; listed tasks must preserve them and mention retained behavior changes in PR descriptions. |
| Parity MODERNIZATION: minSdk 31 | Declared project decision. |
| Parity "Not verified": old screenshots | Informational only. |
| Architecture "Not verified" 7 | Covered by the parity review. |

## Disagreements with the reviews (verified against code)
- Architecture F15 says `normalizeCourse` (`map/MapState.kt:28`) duplicates `normalizeCourseDegrees` (`course/CourseState.kt:16`). Not exactly: the map version returns a fractional `Float`, the course version floors to `Int`. Blindly folding them would change marker rotation; TASK-003 keeps both behaviors.
- Parity MODERNIZATION lists "single search result no longer auto-selected" as acceptable. The original is the reference and auto-selection drives the picker's map-centering cue, so TASK-032 restores it.
- Parity finding 27 (card order) is correct for the original code, but `promo/screen-1.png` shows a different, older order (Satellites, Course, Flight parameters). The human delegated the choice; TASK-034 uses Satellites, Course, Horizon, Flight parameters, Nearby city, Route, Map (reasoning in the task).
- Parity inventory implies the original horizon only needed an accelerometer (`HorizonCardViewPresenter.kt:26`). That check only drove the overlay; the horizon data actually came from `TYPE_ROTATION_VECTOR` (`services/SensorService.kt:64,154`). TASK-026 accounts for this.
- Parity finding 16: the original showed "+0.0" as the first vertical speed. TASK-020 keeps "—" (no measurement yet) while restoring the smoothing; this is a presentation detail.
- Architecture DI inventory marks `AndroidOrientationSource` as Activity-bound. TASK-010 takes the review's alternative: a singleton source plus a display-rotation provider based on `DisplayManager` (default display).
- Parity finding 24 cites the original launcher as `drawable-v24/ic_launcher_foreground.xml` + `drawable/ic_launcher_background.xml`. Those are unused Android Studio template files (teal grid background); the original has no `mipmap-anydpi-v26`, so the shipped icon is the PNG `mipmap-*/ic_launcher.png` / `ic_launcher_round.png`. TASK-034 rebuilds the adaptive icon from the PNG artwork.
- Parity palette section / finding 11 justify the accent change to #6CF0FF "for contrast". Per the human's palette decision TASK-018 restores #25E5FE and instead restricts small accent text on card surfaces.

## Open questions for the human
1. **Muted label contrast.** The original label color #A1A0C4 has ≈2.5:1 contrast on cards (#5B5999) and ≈3.3:1 on the page (#484685), below WCAG AA. TASK-018 follows your palette decision and keeps it as a documented exception. Do you want a slightly lighter label shade instead (e.g. ≈#B9B8D9, still clearly "muted")? The plan proceeds with the original color unless you say otherwise.
