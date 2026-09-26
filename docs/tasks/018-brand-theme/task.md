# TASK-018 — Brand theme on every screen (original palette, no dynamic color, no white launch flash)

## Goal
Apply the original Smart Flight palette through `MaterialTheme` so Settings, About, the city picker and every dialog use it, restore the muted-label / light-value text hierarchy, and remove the light window theme that can flash white at launch.

## Context
- Parity finding 11 (VISUAL_MISMATCH, MEDIUM), verified:
  - `ui/theme/Theme.kt:36-52` is the Android Studio template with `dynamicColor = true` and Purple80/Pink80 (`ui/theme/Color.kt`).
  - `res/values/themes.xml:4` uses `android:Theme.Material.Light.NoActionBar`.
  - `UnitSettingsScreen` (Scaffold/TopAppBar), `AboutDialog` (AlertDialog/Button) and `RoutePicker` (Button/OutlinedTextField) take colors from the wallpaper-based dynamic scheme.
  - The picker sits on `Color(0xFF211D46)` (not in the original palette) and its plain `Text` calls set no color.
  - All labels use #D9D9ED; the original used muted labels (#A1A0C4) and light values (#D9D9ED).
  - Accent changed from #25E5FE to #6CF0FF "for contrast; that part is justifiable".
- Parity "Not verified": picker text contrast on #211D46 depends on `LocalContentColor`; check on device in light and dark system themes and several wallpapers.
- Original palette (`~/smart-flight/app/src/main/res/values/colors.xml`): page `purple_dark #484685`, cards/toolbar `purple_main #5b5999`, accent `cyan_main #25e5fe`, labels `text_color_dark #a1a0c4`, values `text_color_light #d9d9ed`, satellites `#4caf50`/`#f44336`, dialogs background `purple_dark` (`styles.xml:10-14`: title `text_color_dark`, content `text_color_light`, buttons `cyan_main`), settings text light/secondary dark (`styles.xml:40-43`).
- Screenshots: `~/smart-flight/promo/screen-1.png`, `screen-2.png`, `screen-3.png` (labels muted lavender, values light; picker on page purple with cyan underline).
- TASK-017 put all colors into `ui/theme` tokens.

## Dependencies
- TASK-017.

## Original app reference
- `~/smart-flight/app/src/main/res/values/colors.xml`, `styles.xml`, `layout/activity_find_city.xml`, `layout/about_app_dialog_layout.xml`, `xml/app_preferences_layout.xml`; `promo/screen-*.png`.

## Scope
- `SmartFlightTheme`: fixed dark `ColorScheme` built from the palette (background = page, surface/surfaceContainer* = card, primary = accent, onPrimary dark, onSurface = light text, onSurfaceVariant = muted label, error from token). Remove dynamic color and the unused template colors.
- Tokens: `labelText` = #A1A0C4, `valueText` = #D9D9ED. Apply label/value roles in Flight parameters, Nearby city, Route, Course cards.
- Accent decision: use the original #25E5FE where its contrast ratio against the background it sits on is ≥ 4.5:1 for text (≥ 3:1 for large text/icons); otherwise keep #6CF0FF for that use. Encode the check in a unit test using `contrastRatio`.
- Picker background: page color #484685 instead of #211D46; text colors from the scheme.
- `res/values/themes.xml`: dark parent (e.g. `android:Theme.Material.NoActionBar`) with `android:windowBackground` = page color, status/navigation bars consistent with edge-to-edge.
- Settings, About and picker: rely on scheme colors (no ad-hoc colors).

## Out of scope
- Header/toolbar layout (TASK-034), card visuals (TASK-022/027/033), launcher icon (TASK-034).

## Requirements
Required:
- No dynamic color; same colors in light and dark system themes.
- Muted label / light value hierarchy restored.
- Every text/background pair used by the app meets WCAG AA (4.5:1 normal text, 3:1 large text) — or the deviation is listed in the PR.
- No white flash at launch.

Recommendations:
- Keep the accessibility improvements (semantics, touch targets) untouched.

## Acceptance criteria
- [ ] `SmartFlightTheme` ignores system dark mode and wallpaper — verified by: CI unit test (Robolectric Compose: `MaterialTheme.colorScheme.primary` equals token under `night` and `notnight` qualifiers)
- [ ] Contrast test over all token pairs in use — verified by: CI unit test
- [ ] Label/value colors applied (Compose tests reading text color via semantics or a test tag on a styled `Text` wrapper) — verified by: CI unit test / code review
- [ ] Settings, About and picker look on-brand; picker text readable — verified by: HUMAN on device (light + dark system theme, two wallpapers), compare with `promo/screen-3.png`
- [ ] No white window at cold start — verified by: HUMAN on device

## Tests to add or update
- `SmartFlightThemeTest`, `ContrastTest`; update Compose tests that assert colors, if any.

## Risks and edge cases
- Material3 components pick secondary/tertiary roles for some parts (e.g. switches, radio buttons); set all roles explicitly.
