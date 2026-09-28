# TASK-002 — Review iteration 1

## Result
**ESCALATED**

The migration itself is clean. There are no BLOCKING findings. One deviation from the task text needs a human decision: the `@Ignore`'d test (SPEC_CONFLICT E1). My recommendation is to accept it as it is and open follow-ups F1–F3. If the human accepts that, this review becomes PASS and no code change is needed.

## Acceptance criteria
| criterion | status | verified by |
|---|---|---|
| The six Compose test classes run in `testDebugUnitTest` and pass | MET per the reported CI run (PASS on 09a9690, run 36404786317). I did **not** check the test-report listing or the total/failed/skipped counts: my role only allows `git` read commands, so I could not run `gh run download`. The orchestrator must still confirm the six classes appear and that 1 skipped test is expected (`invalidOfflineArchive…`). | CI as reported; source review: all six files moved under `app/src/test/...` with `@RunWith(AndroidJUnit4::class)` |
| `app/src/androidTest` no longer exists; the workflow has no emulator or instrumented step | MET | `git show HEAD:app/src/androidTest` gives "does not exist". `build.yml` is unchanged (`ktlintCheck testDebugUnitTest assembleDebug`). `testInstrumentationRunner`, the `androidTestImplementation` deps and espresso are removed from `build.gradle.kts` and `libs.versions.toml`. |
| Any deleted test method is listed in the PR with its scenario as a HUMAN on-device check | NOT APPLICABLE / PARTIAL. No method was deleted. One method was `@Ignore`'d instead (see E1). I could not see the PR description. | code review |
| No file under `app/src/main` changed | MET | `git diff --stat origin/ai-modernization...HEAD -- app/src/main` is empty |
| ktlint passes | MET per CI (ktlintCheck is part of the green run) | CI as reported |

Requirements:
- `robolectric.properties` exists with `sdk=35` in one place: met.
- `@GraphicsMode` was not added anywhere, which is correct because nothing uses `captureToImage`.
- The workflow is unchanged: met.

## Blocking findings
None.

## Escalations
**E1 — SPEC_CONFLICT: `app/src/test/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreenTest.kt:304-327`, `@Ignore` on `invalidOfflineArchiveReportsOpenFailureWithoutUsingNetworkFallback`**

The developer says a production bug makes this test fail. The claim fits the code:
- `MapCard.kt:261-298` only calls `onOpenFailure()` when the `OfflineTileProvider`/`MapView` constructor throws.
- osmdroid's `ArchiveFileFactory.getArchiveFile` catches the `IOException`/`ZipException`, logs it and returns null. `OfflineTileProvider` then skips the null archive, so nothing is thrown.
- As a result, a corrupt `osmdroid.zip` gives a blank map and never the "Map unavailable" card.

Checked against: `MapCard.kt` and my knowledge of the osmdroid 6.1.x source. The osmdroid jar was not inspected, and the CI failure that led to the `@Ignore` was not seen by me.

Why this is a conflict:
- The task's goal is "no test code that nothing ever runs", and deletion is only foreseen for tests that *cannot run under Robolectric*. This one can run; it fails on production behavior.
- The task also says "No production code changes" and "keep assertions identical".
- So all three possible actions break some rule: `@Ignore` leaves unrun test code, deleting drops a correct pin, and inverting the assertion pins a bug.
- No existing task tracks this defect. TASK-014 only covers re-extracting a corrupt archive at repository level, not MapCard reporting an open failure.

Recommendation: accept `@Ignore`. It keeps the pin, and the reason string names the bug. In addition:
- (a) Add a follow-up, probably folded into TASK-014: MapCard/MapRepository must detect an unreadable archive, for example by opening it with `ZipFile` before building the provider, and then remove the `@Ignore`.
- (b) List it in the PR description as a known defect and as a HUMAN check.

The alternative is to delete the test plus add a HUMAN check, which is weaker.

## Non-blocking findings
**N1. Assertions changed to match current production output (the caller's point 2).**

I checked every change against production. The original androidTest file could never have compiled or passed, because it was out of date with production:
- `RoutePicker.onConfirm` now returns `Boolean`.
- `permissionStateCardTestTag` was renamed to `PERMISSION_STATE_CARD_TEST_TAG`.
- `GnssStatusScreen` was called with positional arguments that no longer match its parameters.
- `setContent` was called twice in one test, which the rule forbids.

So the premise "they pin current behavior" was false, and aligning to production was unavoidable. None of the changes weakens a matcher: all remain exact text or content-description matches. Each one:
- "Pressure, unavailable" → "Pressure unavailable", and "Pressure, 1013.3 millibars" → "Pressure 1,013.2 millibars".
  - Production is `flight_value_accessibility = "%1$s %2$s"`, used for both label+value and value+unit. `git log -S` shows the comma form never existed in strings.xml. The comma came only from the TASK-009 design doc, so this is not a regression from an earlier commit.
  - Still worth a follow-up: TalkBack reads "Pressure 1,013.2 millibars" with no pause.
- "1013.3 mbar" → "1,013.2 mbar". This matches `formatUnitNumber` (`NumberFormat` with grouping and HALF_EVEN).
  - The **original app** uses `"%.1f mbar".format(pressure)` (`~/smart-flight/.../avionic/calculators/Pressure.kt:21`), which gives "1013.3 mbar": no grouping, HALF_UP.
  - This is a real parity deviation that no parity finding or task tracks. **Follow-up F2 is needed**, probably under TASK-020.
- "196.9 ft/min" → "+196.9 ft/min". This matches production's signed format (`formatUnitNumber(signed=true)`).
- About stateDescription → "Opens an email addressed to Smart Flight feedback". This matches `about_feedback_action_description`.
- Larger-zoom switch description now includes the warning suffix. This matches `display_setting_warning_description`.
- `everySelectorShowsItsExactOptions…`: the old code clicked `options.last()` but expected `chosen` (for Speed that is Knots vs "mph"), so the test contradicted itself. The fix clicks the option that matches `chosen`. This is correct and not weaker.
- `zoomLevel` → `zoomLevelDouble` is a type fix.

These must be listed in the PR description as unavoidable changes, and F1–F3 should be recorded in the plan. I could not verify the PR body.

**N2. Map viewport tolerance (point 3).**

The justification holds:
- The tolerance is 1 px at zoom 8 plus 1 px at zoom 6 of longitude: 360/65536 + 360/16384 ≈ 0.0275°.
- It covers osmdroid's whole-pixel snapping of Mercator coordinates.
- Latitude pixels at 48.8° are smaller, so the tolerance is conservative there.

The test is still meaningful:
- A reset or recentre would jump to `DEFAULT_CENTER` (32, -32) or to a fix, which is tens of degrees away.
- The `assertSame(initialMap, …)` identity checks are unchanged.

Doing zoom-then-centre in the setup is a setup change, not a weaker assertion.

**N3. Setup-only changes (point 4) are fine and do not weaken anything:**
- A single `setContent` per test, with state switched through `mutableStateOf` (Flight, Horizon, `setCourse` helper).
- `nearbyCityState = LookingUp` to remove a duplicate "Waiting for GPS position…" match.
- `performScrollTo()` before clicking rows below the fold.
- `@Config(qualifiers = "w411dp-h2000dp")` on one test, as the task itself suggests.
- A local `hasRole` matcher equivalent to the removed import.

**N4.** `qualifiers=w411dp-h891dp` in `robolectric.properties` applies to every Robolectric test, including non-Compose ones such as `LocationForegroundServiceTest`. It is harmless (CI is green) and matches the task's example.

**N5.** The PR description should state the CI duration change, as the Risks section asks.

**Follow-ups to record (not in this task):**
- F1: MapCard does not report an unreadable offline archive (E1).
- F2: pressure and number formatting differs from the original: grouping separator, HALF_EVEN rounding ("1,013.2" vs "1013.3").
- F3: flight row content descriptions have no separator between label and value (optional a11y polish).

## Behavior changes
None in production (`app/src/main` is untouched). Build and test-infra changes only:
- androidTest source set removed.
- espresso removed from the catalog.
- Compose ui-test-junit4 and androidx-junit moved to `testImplementation`.
- `robolectric.properties` added.

## Human checks on device
1. Put a corrupt or non-zip `osmdroid.zip` in the extracted map location, then open the dashboard. Expected per the test: the "Map unavailable" card with "Try again". Likely current result: a blank or grey map. This covers the `@Ignore`'d test.
2. With TalkBack, focus the Flight parameters rows and confirm they are read clearly, for example "Pressure 1,013.2 millibars" / "Pressure unavailable". This is for F3.
3. Confirm the pressure value format on device, which depends on locale (grouping and rounding), and compare it with the original app. This is for F2.

## Verification performed
- Source review:
  - the full diff `origin/ai-modernization...HEAD` (4 commits);
  - the full content of `UnitSettingsScreenTest.kt`, `GnssStatusScreenTest.kt` (lines 150–340) and `HorizonCardTest.kt` (the setContent usages);
  - production `FlightParametersCard.kt`, `UnitPresentation.kt`, `MapCard.kt`, `AboutDialog.kt`, `MapState.kt` (default centre and zoom policy), and the relevant `strings.xml` entries;
  - the original app's `Pressure.kt` formatting;
  - `docs/tasks/README.md` and the task docs, to see whether F1 and F2 are tracked (they are not).
- CI result as reported by the main session: PASS on 09a9690, run 36404786317.
- NOT verified:
  - the test-report contents (class list, total/failed/skipped counts), because gh commands are outside my allowed tools;
  - the PR description;
  - the osmdroid `ArchiveFileFactory` behavior against the actual jar;
  - any runtime behavior.
- The `docs/tasks/README.md` status is still TODO; the orchestrator will update it on PASS, as noted.

Relevant files:
- /home/ai-dev/smart-flight-2-modern/app/src/test/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreenTest.kt
- /home/ai-dev/smart-flight-2-modern/app/src/test/java/kniezrec/com/flightinfo/ui/settings/UnitSettingsScreenTest.kt
- /home/ai-dev/smart-flight-2-modern/app/src/test/resources/robolectric.properties
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/ui/gnss/MapCard.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/displayunits/UnitPresentation.kt
- /home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/avionic/calculators/Pressure.kt

## Human decision (2026-09-28)
E1 resolved with option A (the reviewer's recommendation): keep the `@Ignore` on `invalidOfflineArchiveReportsOpenFailureWithoutUsingNetworkFallback`. The review result becomes **PASS**; no code change needed. Follow-ups recorded in the plan: F1 → TASK-014 (fix `MapCard` for unreadable archives, remove the `@Ignore`), F2 and F3 → TASK-020.
