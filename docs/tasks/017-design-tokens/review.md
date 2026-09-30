# TASK-017 — Review iteration 1

## Result
PASS

## Acceptance criteria
| criterion | status | verified by |
|---|---|---|
| Grep for `Color(0x` outside `ui/theme` returns nothing | MET | Code review. My grep of `app/src/main/java`, excluding `ui/theme`, found no `Color(0x`, no raw `0xFF…`/`0xDD…` literals and no named colors such as `Color.White`. The one remaining `Color.Transparent` in `NearbyCityCard.kt` is not a hex literal. There is also a JVM guard, `ColorLiteralGuardTest`. |
| Single unit-label mapping used by the four call sites | MET | Code review. `displayunits/ui/UnitLabels.kt` (`UnitKey.labels`) is used by `FlightParametersCard`, `UnitSettingsScreen` (symbol and long name), `RouteCard` and `NearbyCityCard`. All old inline `when`/`if` mappings are removed. `UnitLabelsTest` pins every unit (all 12 enum values) to its symbol, long-name and accessibility resources. |
| All tests pass | MET (as reported by the caller) | CI run 36692787088 on 9c3e8e7 was reported as PASS. I did not open the run myself. |

## Blocking findings
None.

## Escalations
None.

## Non-blocking findings
1. `permission/ui/PermissionOnboardingScreen.kt`: the comment "The legacy muted lavender does not meet contrast at this size; use the accessible light token." is now orphaned. `BodyLavender` was removed, so the comment sits above `PERMISSION_STATE_CARD_TEST_TAG`. Delete it or move it to the `text` token.
2. `route/ui/RouteCard.kt` (clear `TextButton` in `EndpointRow`): the reformat split `Modifier.heightIn(min = 48.dp)` over several lines. This works, but it reads worse than before. It looks like a ktlint artifact.
3. The task Goal mentions "typography roles", but Scope and Out of scope (TASK-018) only cover colors and unit labels. Not tokenizing typography matches the scope, so no action is needed.
4. `ColorLiteralGuardTest` assumes the working directory is the module directory. It fails loudly (asserts the directory exists) rather than passing silently, which is acceptable. It only checks `src/main/java`, not `src/main/kotlin`, which is fine because the project has no Kotlin source dir.
5. `docs/tasks/README.md` status is not updated yet. The caller says the orchestrator will do this after review. It must happen before the PR is ready.

## Verified details
- **Color values compared one by one** against the removed literals. All are unchanged:
  - page #484685
  - card #5B5999
  - accent #6CF0FF
  - text #D9D9ED (replaces every `textColor`, `horizonText`, `nearbyTextColor`, `LightLavender` and `BodyLavender`)
  - error #FFB4AB
  - horizonSky #7775B5
  - horizonGround #3F3D70
  - mapButtonBackground #DD25133F
  - mapOverlayContent `Color.White` (replaces the two `Color.White` uses in `MapCard`)
  - pickerBackground #211D46

  `SmartFlightColorsTest` pins all ten as ARGB values.
- **`contrastRatio`** moved to `ui/theme/Contrast.kt`. Its test moved from `LocationPermissionsTest` into `SmartFlightColorsTest`, now using the tokens, plus 1:1 and 21:1 bound checks. The old symbols `cardPurple`, `actionCyan`, `smartFlightPageColor` and `DashboardColors` are no longer referenced anywhere in `app/src`, including androidTest.
- **Theme access:** `LocalSmartFlightColors` is a `staticCompositionLocalOf` that defaults to the default colors, so composables rendered without the theme (e.g. Compose tests) get identical colors. `SmartFlightTheme` provides the same object. No dynamic-color change and no typography change, as required by Out of scope.
- **`UnitKey` → sealed interface:** this makes the `when` over the unit types exhaustive. `UnitChoiceDialog` no longer needs the unchecked `as T` casts. The checked casts remain in `UnitPreferences.with`, as before. Setting behavior (selected option, preference update, dismiss) is unchanged in the source.
- **Scope:** no files outside scope, no androidTest changes, no behavior changes found in the source. The deliberate leftovers are in line with the task: `MapOverlays` `android.graphics.Color.CYAN` has no `Color(0x` literal and belongs to TASK-030; the template `ui/theme/Color.kt` is inside `ui/theme` and belongs to TASK-018.
- **Architecture:** no new abstractions beyond the ones the task asks for (the color-token type, the theme accessor and the unit-label mapping). This matches CLAUDE.md.

## Human checks on device
1. Dashboard, cards, onboarding and route picker look the same as before, side by side with the previous build. Check page, card and text colors, cyan actions and focus borders, the horizon sky and ground, the map zoom buttons and max-zoom warning, the route error text, and the picker background.
2. Settings → Units: each row shows the correct symbol. Each choice dialog lists the full names, marks the current selection and changes the unit.
3. TalkBack: speed, vertical speed, altitude and pressure (flight card), plus nearby-city distance and route distance, still use the spoken unit names.

## Verification performed
- **Source review:** read the task, the full diff (21 files) and the full `UnitSettingsScreen.kt`. Grepped `src/main` for color literals and `app/src` for removed symbols.
- **CI result as reported:** PASS on 9c3e8e7, run https://github.com/NieKam/smart-flight-2/actions/runs/36692787088. I did not inspect it myself.
- **Not verified:** runtime rendering and pixel parity, and whether androidTest Compose tests ran or passed in CI (there are no androidTest changes). The original app was not compared because the task is a no-behavior-change refactor and colors were compared against the pre-change code.

Relevant files:
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/ui/theme/SmartFlightColors.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/ui/theme/Theme.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/ui/theme/Contrast.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/displayunits/ui/UnitLabels.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/permission/ui/PermissionOnboardingScreen.kt
- /home/ai-dev/smart-flight-2-modern/app/src/test/java/kniezrec/com/flightinfo/ui/theme/ColorLiteralGuardTest.kt
- /home/ai-dev/smart-flight-2-modern/app/src/test/java/kniezrec/com/flightinfo/ui/theme/SmartFlightColorsTest.kt
- /home/ai-dev/smart-flight-2-modern/app/src/test/java/kniezrec/com/flightinfo/displayunits/ui/UnitLabelsTest.kt
