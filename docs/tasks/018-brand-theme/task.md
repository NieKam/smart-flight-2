# TASK-018 — Original Smart Flight palette on every screen (no dynamic color, text hierarchy, dialogs, no white launch flash)

## Goal
Make the app's colors match the original app's palette everywhere: page, top bar and status bar, cards, text hierarchy (muted labels vs light values), Settings, About, every dialog, the city picker, snackbars, and the palette tokens later used by the map line/marker and the other visual parity tasks. Remove dynamic color and the light window theme that can flash white at launch. Add a CI guard so no color outside the palette creeps back in.

## Context
- Human decision: the app's colors must match the original palette (`~/smart-flight/app/src/main/res/values/colors.xml`, `styles.xml`, screenshots in `~/smart-flight/promo/`) across all screens, cards, text hierarchy, dialogs and the map line/marker.
- Parity finding 11 (VISUAL_MISMATCH, MEDIUM), verified in code:
  - `ui/theme/Theme.kt:36-52` is the Android Studio template with `dynamicColor = true` and Purple80/Pink80 (`ui/theme/Color.kt:5-11`). `res/values/colors.xml` still holds template colors (`purple_200`, `teal_200`, …).
  - `res/values/themes.xml:4` uses `android:Theme.Material.Light.NoActionBar`.
  - `UnitSettingsScreen` (Scaffold/TopAppBar), `AboutDialog` (AlertDialog/Button) and `RoutePicker` (Button/OutlinedTextField) take colors from the wallpaper-based dynamic scheme.
  - The picker sits on `Color(0xFF211D46)` (`GnssStatusScreen.kt:171` before moves; not in the original palette) and its plain `Text` calls set no color.
  - All labels use #D9D9ED (`FlightParametersCard.kt:44`, `CourseCard.kt:53`, `NearbyCityCard.kt:42`, `RouteCard.kt:46,157,163`, `GnssStatusScreen.kt:70`); the original used muted labels (#A1A0C4) and light values (#D9D9ED).
  - Accent changed from #25E5FE to #6CF0FF (`ui/permission/PermissionColors.kt:7`).
  - Other non-palette colors observed: map buttons `Color(0xDD25133F)` with white glyphs (`MapCard.kt:182,195`), max-zoom caption `Color.White` (`MapCard.kt:117`), route line `android.graphics.Color.CYAN` (`MapCard.kt:352`), plane marker #00D4FF (`res/drawable/ic_plane_map.xml`), route pins #4DD0E1 / #FFB74D (`res/drawable/ic_route_departure.xml`, `ic_route_destination.xml`), error #FFB4AB (`RouteCard.kt:88`), launcher background #211D46. The drawables and map colors are changed in TASK-030/031/034; this task defines the tokens they use.
- TASK-017 put all Compose colors into `ui/theme` tokens (`SmartFlightColors`), so this task changes values in one place.
- Parity "Not verified": picker text contrast depends on `LocalContentColor`; check on device in light and dark system themes and with several wallpapers.

### Original palette (from `colors.xml`) and where the original used it (from `styles.xml`, layouts and `promo/screen-1..3.png`)
| Token (new name) | Value | Original usage |
|---|---|---|
| `page` | `purple_dark` #484685 | window background (`AppTheme android:windowBackground`), main screen, dialog background (`md_background_color`), picker screen, map route line, plane marker tint, picker pin, map button icons (`ic_expand`/`drawing_pin_icon` fill) |
| `card` | `purple_main` #5B5999 | cards (`card_background`), toolbar and status bar (`colorPrimary`, `colorPrimaryDark`), picker Search/Confirm buttons (`promo/screen-3.png`) |
| `accent` | `cyan_main` #25E5FE | `colorAccent`: text-field underline, switches/checkboxes, dialog buttons (`md_color_button_text`), compass cardinal abbreviation ("W" in `promo/screen-1.png`), snackbar action |
| `accentPressed` | `cyan_main_50` #8025E5FE | ripple / `colorControlHighlight` |
| `accentLight` | `cyan_light` #99E5FC | secondary accent (use only where the original did; optional) |
| `labelText` | `text_color_dark` #A1A0C4 | labels (`TextLabel`), compass letters N/E/S/W (`CompassLetter`), dialog titles (`md_color_title`), preference secondary text, card titles such as "Connected to N satellites" |
| `valueText` | `text_color_light` #D9D9ED | values (`TextValue`), edit text, dialog content (`md_color_content`), preference primary text, plane in the compass, take-off/landing/trash icons |
| `satelliteUsed` / `satelliteUnused` | #4CAF50 / #F44336 | satellite bars |
| `overlay50` / `overlay20` | #80000000 / #33000000 | dim overlays (e.g. "hide card" overlay) |
| `toastBackground` | #2C2163 | toast/snackbar background (the zoom-tip snackbar used `purple_dark`) |
| `toolbarTitle` | #FFFFFF | toolbar title and overflow icon (AppCompat dark action bar default; white in `promo/screen-1.png`) |

Documented extras (not in the original, allowed): `error` #FFB4AB (inline error text), horizon `sky` #7775B5 / `ground` #3F3D70 (the redesigned horizon is an accepted modernization). Anything else needs a line in this table and a reason.

### Contrast (computed by the planner; re-check in the test)
- `valueText` on `card` ≈ 4.5:1 (passes AA normal text, just), on `page` ≈ 6.1:1.
- `accent` on `card` ≈ 4.1:1 (passes 3:1 for large text/UI components, fails 4.5:1 for small text), on `page` ≈ 5.5:1.
- `labelText` on `card` ≈ 2.5:1, on `page` ≈ 3.3:1 — fails WCAG AA for normal text. This is the original design's muted-label hierarchy. Planner decision: follow the human's instruction to match the original palette and keep #A1A0C4 for labels; the deviation is documented here and raised as an open question in the README (a slightly lighter label would improve accessibility). Values, body text and actions must pass.

## Dependencies
- TASK-017.

## Original app reference
- `~/smart-flight/app/src/main/res/values/colors.xml`, `styles.xml`, `dimens.xml`, `layout/activity_main.xml`, `layout/activity_find_city.xml`, `layout/about_app_dialog_layout.xml`, `xml/app_preferences_layout.xml`, `common/DialogUtils.kt`.
- Screenshots (open the images): `promo/screen-1.png` (purple_main toolbar and status bar, purple_dark page, purple_main cards, muted "Connected to 8 satellites" title, muted N/E/S/W, light plane and "271°", cyan "W", muted "Speed/Vertical Speed/Altitude" labels), `promo/screen-2.png` (muted labels "Closest city/Country/Distance…" with light values, light city names in the route card, light trash icon, purple_dark geodesic line and plane on the map), `promo/screen-3.png` (picker on purple_dark, light input text with cyan underline, purple_main "SEARCH"/"CONFIRM" buttons, light result text, purple_dark pin).

## Scope
- `SmartFlightTheme`: one fixed dark `ColorScheme` built from the tokens — `background` = `page`, `surface`/`surfaceContainer*` = `card`, `primary` = `accent`, `onPrimary` = `page`, `onBackground`/`onSurface` = `valueText`, `onSurfaceVariant` = `labelText`, `outline` = `labelText`, `error` = `error`, `secondary`/`tertiary` roles set explicitly (no Material defaults leaking in). `darkTheme`/`dynamicColor` parameters removed. Delete `Purple80`…`Pink40` and the template entries in `res/values/colors.xml` (keep only what XML resources use).
- `SmartFlightColors` values replaced by the table above; delete tokens that are no longer used (#6CF0FF `actionCyan`, #211D46 picker background, map-button #DD25133F — the map button look is TASK-031; until then map buttons use `card`/`valueText`).
- Text hierarchy, applied through two shared text styles or small wrappers (`LabelText`, `ValueText`) rather than per-call colors:
  - Flight parameters, Nearby city, Route, Course, GNSS cards: labels `labelText`, values `valueText`; card titles `labelText`.
  - Map messages (loading/unavailable/inactive): title `labelText`, body `valueText`.
  - Permission card/onboarding: title `valueText`, body `labelText`/`valueText` as in `layout/permission_view.xml`.
- Chrome: status bar area uses `card` (edge-to-edge: the top bar container draws behind the status bar; light status-bar icons); navigation bar area uses `page`.
- Dialogs (About, "Enable GPS" from TASK-019, radio dialogs in Settings, any `AlertDialog`): container `page`, title `labelText`, text `valueText`, buttons `accent`.
- Settings: background `page`, top bar `card` with `toolbarTitle`, primary text `valueText`, secondary text `labelText`; switches: checked thumb `accent`, checked track `accentPressed`, unchecked thumb `valueText`, unchecked track `overlay20`; radio buttons `accent`.
- City picker: background `page` (not #211D46); text field transparent container, text `valueText`, placeholder `labelText`, focused/unfocused indicator `accent`/`labelText`, cursor `accent`; buttons `card` container with `valueText` content (disabled content `labelText`).
- Snackbars (if any exist before TASK-031): container `page` or `toastBackground`, text `valueText`, action `accent`.
- `res/values/themes.xml`: dark parent (e.g. `android:Theme.Material.NoActionBar`) with `android:windowBackground` = #484685 (resource color `purple_dark`), so there is no white frame before Compose draws.
- Palette guard test: a JVM test that iterates over every public token in `SmartFlightColors` (and every `<color>` in `res/values/colors.xml`) and asserts each value is in the table above (original palette + documented extras).

## Out of scope
- Header/toolbar layout and card order (TASK-034), satellite chart (TASK-022), compass rose (TASK-027), route card layout (TASK-033), map line/marker/pins (TASK-030), map buttons (TASK-031), picker map (TASK-032), launcher icon (TASK-034). These tasks must use only the tokens defined here.

## Requirements
Required:
- No dynamic color; identical colors in light and dark system themes and with any wallpaper.
- Every color on screen comes from the palette table (original colors + documented extras).
- Muted label / light value hierarchy restored on every card, dialog and Settings.
- Values, body text and interactive elements meet WCAG AA (values/body ≥ 4.5:1; accent text only as large text or UI component on `card`, ≥ 3:1). Muted labels are the documented exception (original design).
- No white flash at launch.

Recommendations:
- Keep the accessibility improvements (semantics, touch targets) untouched.

## Acceptance criteria
- [ ] `SmartFlightTheme` ignores system dark mode and wallpaper — verified by: CI unit test (Robolectric Compose: `MaterialTheme.colorScheme.primary/background/surface/onSurface/onSurfaceVariant` equal the tokens under `night` and `notnight` qualifiers)
- [ ] Palette guard: every `SmartFlightColors` token and every `res/values/colors.xml` entry is in the palette table — verified by: CI unit test
- [ ] No `Color(0x` literal outside `ui/theme` and no `android.graphics.Color.<NAMED>` constant in UI code — verified by: CI unit test (source scan) or code review
- [ ] Contrast test: values/body on `card` and `page` ≥ 4.5:1; `accent` on `card` ≥ 3:1 and on `page` ≥ 4.5:1; muted-label ratios printed and asserted equal to the documented exception — verified by: CI unit test
- [ ] Pixel check: Robolectric `@GraphicsMode(NATIVE)` `captureToImage()` of the dashboard samples page background = #484685, a card container = #5B5999, the top bar = #5B5999 — verified by: CI unit test
- [ ] Label/value roles used (e.g. `FlightParametersCard` label nodes rendered with `labelText`, value nodes with `valueText`, checked via the `LabelText`/`ValueText` wrappers' test tags or a pixel sample of a glyph) — verified by: CI unit test / code review
- [ ] Dashboard, Settings, About, "Enable GPS" dialog and picker match the palette of `promo/screen-1..3.png`; picker text readable — verified by: HUMAN on device (light + dark system theme, two wallpapers)
- [ ] No white window at cold start — verified by: HUMAN on device

## Tests to add or update
- `SmartFlightThemeTest`, `PaletteGuardTest`, `ContrastTest`, `DashboardColorsPixelTest`; update Compose tests that assert colors, if any.

## Risks and edge cases
- Material3 components pick secondary/tertiary/container roles for some parts (switches, radio buttons, text fields, `AlertDialog` container = `surfaceContainerHigh`); set all roles explicitly and check each component used in the app.
- Edge-to-edge is enforced at target SDK 35+; status-bar color must come from the top bar drawing behind it, not from a deprecated window attribute.
- Robolectric native graphics can differ by a few units per channel from device rendering; compare pixels with a small tolerance (±2 per channel).
