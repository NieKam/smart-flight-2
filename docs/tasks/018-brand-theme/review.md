# TASK-018 — Review iteration 1

## Result
**PASS.** I found no blocking findings and nothing needs escalating. The notes below are non-blocking, plus a list of checks for a human on a device.

## Acceptance criteria
| Criterion | Status | Verified by |
|---|---|---|
| `SmartFlightTheme` ignores system dark mode and wallpaper | Met | `SmartFlightThemeTest`: `+night` and `+notnight` qualifiers. The test also checks that the qualifier really took effect (`isSystemInDarkTheme` and `uiMode`). It asserts primary, onPrimary, background, onBackground, surface, surfaceContainerHigh, surfaceTint, onSurface, onSurfaceVariant, outline, error, inverseSurface and inversePrimary. `Theme.kt` has no `darkTheme`/`dynamicColor` parameters, and every role is set explicitly (`darkColorScheme` with all roles). The wallpaper case can only be checked on a device. |
| Palette guard: every token and every `colors.xml` entry is in the table | Met | `PaletteGuardTest`. It reads the getters by reflection and cross-checks the count against the declared fields, so a missed token would fail. It also checks that every `<color>` in `colors.xml` is a literal. Its `PALETTE` set is exactly the task table plus the three documented extras. `colors.xml` now holds only `purple_dark`. |
| No `Color(0x` outside `ui/theme`; no `android.graphics.Color.<NAMED>` in UI code | Met | `ColorLiteralGuardTest` scans for literals, Android named constants/`parseColor`, and Compose named colors except Transparent/Unspecified. I also grepped by hand: only `Color.Transparent` remains, in `MainActivity` and `NearbyCityCard`. `Color.CYAN` is gone from `MapOverlays`. |
| Contrast test | Met | `ContrastTest`: valueText on card and page ≥ 4.5, accent on card ≥ 3 and on page ≥ 4.5. Labels are pinned at 2.50 on card and 3.35 on page and printed. I recomputed accent on card by hand: 4.10:1. |
| Pixel check (page #484685, card #5B5999, top bar #5B5999) | Met, with an accepted deviation | `DashboardColorsPixelTest` samples the three areas with ±2 tolerance under `@GraphicsMode(NATIVE)`. See non-blocking finding 1. |
| Label/value roles used | Met | `TextHierarchyTest` reads each text's laid-out color on the FlightParameters card (title, labels, values), NearbyCity labels/values and the About dialog title/label/value. Route, Course, GNSS, Horizon, Map messages and Settings were checked by code review only: they all use the `LabelText`/`ValueText` wrappers as the task maps them. |
| Dashboard, Settings, About, "Enable GPS" dialog and picker match `promo/screen-1..3`; picker text readable | HUMAN | On a device. The "Enable GPS" dialog does not exist yet (TASK-019); it only needs to use `SmartFlightAlertDialog`. |
| No white window at cold start | HUMAN | In code: `themes.xml` parent is `android:Theme.Material.NoActionBar` and `windowBackground` is `@color/purple_dark`. Needs a device check. |

## Blocking findings
None.

## Escalations
None.

## Non-blocking findings
1. **Pixel test deviation** (`app/src/test/java/kniezrec/com/flightinfo/ui/theme/DashboardColorsPixelTest.kt`). The test draws the Compose host view into a bitmap instead of calling `captureToImage()`, and composes AppScaffold + DashboardHeader + FlightParametersCard instead of the whole dashboard. **I judge this acceptable.** These are the production composables responsible for the three sampled colors. The test would fail if the header background, card container or page Surface were wrong. Composing the whole `DashboardScreen` would need Hilt ViewModels. Limitation: Robolectric reports zero system-bar insets, so the status-bar strip (the `Spacer` in `AppRoot.kt` `AppScaffold`) is not sampled. That goes to the human check.
2. **Accent text on cards.** Small accent labels on cards come out at 4.10:1:
   - `RouteCard.kt:79,95,130,135,145` (Clear all, Retry, "Departure: <city>", Edit, Clear)
   - Course, Horizon and Map "Try again", and the permission actions
   - the 18sp compass cardinal in `CourseCard.kt:155-160`

   They pass 3:1 as UI components (the reading the task allows), but not 4.5:1 as small text. README line 208 says the accent change "restricts small accent text on card surfaces". The original app used small cyan text on cards the same way (`permission_view.xml`; course cardinal at 18sp; horizon at 15sp), so this is parity. The endpoint rows showing city names in accent differ from `screen-2.png`, which shows light city names; that is the route card layout, TASK-033. Suggestion: mention it with open question 1.
3. **Error text contrast.** `error` #FFB4AB on card is 3.71:1 (`RouteCard.kt:83-91`). It is printed, not asserted. The task itself defines this color as an extra, and its contrast criterion covers values/body/accent, not error text. Suggestion: add it to the README open questions instead of treating it as a developer defect.
4. **Route line colour** (`MapCard.kt:98`, `MapOverlays.kt`). It is now `accent`. The task table maps the original route line to `page` (purple_dark; `screen-2.png` shows a dark purple geodesic). The line itself belongs to TASK-030, and replacing `Color.CYAN` was required by the named-colour guard. Using `page` now would already match the original, but either is fine as an interim.
5. **Settings section headers** (`UnitSettingsScreen.kt` Display/Monitoring/Units). They use `valueText`. In the original, the AppCompat `PreferenceCategory` title typically took `colorAccent`. The task does not specify this. Cosmetic only.
6. **Guard coverage.** The guard only scans Kotlin and `values/colors.xml`. Drawables with non-palette colours are left for TASK-030/034, as the developer stated. M3 "fixed" roles are not set, but no component in use reads them.
7. **Max-zoom caption** (`MapCard.kt:110-114`). Its colour changed from white to `valueText` over map tiles. It may be less readable over light tiles (covered in the device checks). The map buttons are `card`/`valueText`, as the task says.

## Behavior and visual changes for the PR description
- Dynamic colour removed: the colours are the same in light and dark system themes and with any wallpaper.
- Accent changed from #6CF0FF back to the original #25E5FE, everywhere.
- Text hierarchy:
  - Card titles and row labels are muted #A1A0C4; values and body text are light #D9D9ED. This covers FlightParameters, Nearby, Route, Course, GNSS, Horizon and Map messages.
  - The satellite row number is muted and its status is light.
- Top bar:
  - The dashboard and permission top bars are now card #5B5999, with a white title.
  - The Settings/About actions and the "more options" menu changed from accent to white.
  - Edge to edge with light status-bar and navigation-bar icons in every system theme. A card-coloured strip is drawn behind the status bar; the page shows behind the navigation bar.
- Window: dark theme parent with a #484685 window background (no white frame at launch). The template `colors.xml` entries were removed.
- Dialogs (About, unit choice): page container, muted title, light content, accent buttons. The About action buttons are card-coloured with light labels.
- Settings:
  - Page background; card top bar with white title and icons.
  - Primary text light, secondary/summary and the current unit value muted, section headers light.
  - Accent switches and radio buttons (checked track cyan 50%, unchecked track #33000000).
- City picker:
  - Background #484685 instead of #211D46.
  - All plain text light; results light instead of primary-coloured.
  - Text field with transparent container, accent indicator/cursor and muted unfocused outline.
  - Search/Confirm buttons card-coloured with light labels (muted when disabled).
- Snackbars now take the theme roles: #2C2163 container, light text, accent action.
- Map:
  - Buttons card #5B5999 with light glyphs (were #DD25133F with white glyphs).
  - Max-zoom caption light instead of white.
  - Route line accent instead of `Color.CYAN`, until TASK-030.
- Compass visual: the ring is muted (was light at 45% alpha) and the arrow is light.
- Permission screen body text is light (the original was muted), for WCAG AA. This is a documented deviation from the original.

## Human checks on device
1. Cold start (light and dark system theme): no white frame before the dashboard; the splash and first frame are #484685.
2. Dashboard vs `promo/screen-1.png` / `screen-2.png`:
   - Status bar area and top bar are one continuous #5B5999 band, with light status icons.
   - The page is #484685 behind the navigation bar, with light nav icons, in both gesture and 3-button navigation.
   - Muted labels, light values, cyan compass cardinal.
3. Change the system theme between light and dark and switch between two different wallpapers: the colours must not change on the dashboard, Settings, the About and unit dialogs, the picker, the overflow dropdown and snackbars.
4. Settings: switch thumb and track in both states, radio buttons in the unit dialogs, divider visibility, top bar behind the status bar.
5. City picker vs `promo/screen-3.png`: the input text and label are readable, the cyan underline shows when focused, the Search/Confirm buttons are card-coloured, the Confirm label is muted when disabled, the result list is light, and error messages are readable.
6. Map: max-zoom caption readable over light tiles; map buttons visible.
7. Landscape and a display cutout: the card strip covers only the status-bar/cutout area, and nothing overlaps the top bar.
8. Permission onboarding (denied / permanently denied): card top bar, readable body, accent actions.

## Verification performed
- **Source review:** the full diff `origin/ai-modernization...HEAD` (3 commits, 31 files), including every changed main and test file. Also read `DashboardScreen` hosting, `RouteCard` and `CourseCard` in context, the task, and the README palette decision and open question 1.
- **Compared with the original:** `values/colors.xml`, `styles.xml`, `layout/permission_view.xml`, cyan usage and text sizes across the original layouts, and `xml/app_preferences_layout.xml`. I opened `promo/screen-1.png`, `screen-2.png` and `screen-3.png`.
- **Contrast recomputed by hand:** accent on card 4.10:1, error on card 3.71:1.
- **Grep:** no leftover references to the removed tokens, `R.color`, or the `dynamicColor`/`darkTheme` parameters.
- **CI:** reported by the orchestrator as PASS on e278dc5 (run 36696984271); I did not re-check it.
- **Not verified:** any runtime rendering on a device (system bars, splash, wallpaper independence, picker readability), `enableEdgeToEdge` navigation-bar contrast behaviour on real OEM builds, and drawables (out of scope).
- **Not done:** the `docs/tasks/README.md` status update (the orchestrator's job).
