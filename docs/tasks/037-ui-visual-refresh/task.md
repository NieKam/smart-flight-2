# TASK-037 — UI visual refresh: accessible text contrast, Material 3 surfaces, prominent flight values, light and dark themes

## Goal
With parity restored (TASK-018 to 036), the app looks almost exactly like the original. Modernize the look to current Android standards (Material 3, WCAG AA) while keeping the Smart Flight identity (purple and cyan palette, card dashboard, layout). Four improvements, in this order:
1. **Accessible text contrast.** Labels and values meet WCAG AA on every surface.
2. **Material 3 surfaces and elevation.** One shared card component, M3 shape scale, tonal elevation, and a top bar that reacts to scrolling.
3. **Prominent flight values.** A real type scale: large, tabular key values (speed, altitude, vertical speed, heading), smaller units and labels.
4. **Light and dark themes.** A light scheme derived from the same hues, the purple palette as the dark scheme, and a Theme setting (System / Light / Dark).

## Context
- **Human decision (2026-10-01):** the human approved these four improvements after reviewing the app on a device; it "looks almost exactly like the old app". This supersedes the TASK-018 rule that every color must equal the original palette, and the open question 1 decision to keep the muted label #A1A0C4. The palette stays the brand basis. New tokens are allowed when they are derived from it and documented in the token table.
- **Contrast, computed by the coordinator:**

  | Foreground | on `card` #5B5999 | on `page` #484685 |
  |---|---|---|
  | `labelText` #A1A0C4 | 2.50 | 3.35 |
  | `valueText` #D9D9ED | 4.53 | 6.06 |
  | #FFFFFF | 6.30 | 8.43 |
  | `accent` #25E5FE | 4.11 | 5.51 |

  - Labels fail AA everywhere, and values only just pass on cards.
  - A label color that passes 4.5:1 on `card` is as light as today's value color. So the label/value hierarchy cannot rely on color alone; it must come from typography (size, weight) plus a smaller color step.
  - The alternative is a darker card surface, which gives more headroom. Pick one approach and justify it in the PR, with the contrast table.
- **Current theme code:**
  - `ui/theme/SmartFlightColors.kt` holds the tokens; `ui/theme/Theme.kt` builds one fixed `darkColorScheme`, ignores the system theme, and gives every surface role the same `card` value.
  - `ui/theme/Type.kt` is the Android Studio template: only `bodyLarge` is set.
  - `ui/theme/SmartFlightText.kt` has `LabelText` / `ValueText`; `ui/theme/SmartFlightComponents.kt` has the dialog, button, text-field, switch and radio colors.
- **Card chrome is repeated** in `FlightParametersCard`, `CourseCard`, `HorizonCard`, `NearbyCityCard`, `RouteCard`, `GnssStatusCard`, `MapCard` and `LocationPermissionCard`: 10dp shape, 4dp elevation, min height 160dp (TASK-033 review finding).
- **The top bar** (`DashboardHeader`, TASK-034) is a `CenterAlignedTopAppBar` in `card`. It has no shadow and no scroll behavior.
- **Guard tests to update, not delete:** `PaletteGuardTest`, `ColorLiteralGuardTest`, `ContrastTest`, `DashboardColorsPixelTest`, `SmartFlightThemeTest`, `SmartFlightColorsTest`, `TextHierarchyTest`.

## Dependencies
- TASK-018 to 036 (all merged).

## Original app reference
- The original palette and screenshots in `~/smart-flight/app/src/main/res/values/colors.xml` and `~/smart-flight/promo/` remain the brand reference for the dark theme. The original had no light theme.
- Android references: Material 3 color roles, surface tones and type scale (m3.material.io); "Guide to app architecture" for keeping theme state in a ViewModel and repository; WCAG 2.1 1.4.3 (text contrast) and 1.4.11 (non-text contrast).

## Scope

### 1. Contrast and text hierarchy
- **Contrast targets:**
  - Labels and values ≥ 4.5:1 on every surface they sit on (card, page, dialog, top bar, snackbar), in both themes.
  - Large text (≥ 18sp regular or ≥ 14sp bold) and UI components (icons, switch thumbs, borders, map buttons) ≥ 3:1.
  - `accent` used as small text on `card` must reach 4.5:1, or be restricted to large text and components (as today).
- **Hierarchy:** `LabelText` and `ValueText` keep their roles. Labels use a smaller, medium-weight style (M3 `labelLarge` / `bodyMedium`) in the muted color; values use the value styles below. The visible difference must remain clear.
- **Contrast test:** `ContrastTest` computes and asserts these ratios for every foreground/background pair actually used, for both schemes, and prints the table.

### 2. Material 3 surfaces and elevation
- **Shared card:** one `SmartFlightCard` composable in `ui/theme` replaces the repeated chrome.
  - It uses an M3 `ElevatedCard` or `Card` with a token shape (M3 `shapes.medium` = 12dp, or a documented choice), a consistent 16dp content padding, and a min height only where a card needs it.
  - All eight cards use it.
- **Surfaces:** use tonal elevation instead of hard shadows, with proper surface roles:
  - `surface`/`background` for the page, `surfaceContainer` for cards, `surfaceContainerHigh` for dialogs and menus, `inverseSurface` for snackbars.
  - Set the surface roles to distinct tones derived from the palette instead of all equal `card`, for example a slightly lighter dialog than card.
- **Top bar:** `TopAppBarDefaults.pinnedScrollBehavior` (or `enterAlwaysScrollBehavior`) on the dashboard list, so the bar changes to its scrolled container color when content scrolls under it. The status bar keeps matching the bar.
- **Snackbars:** they get a container that contrasts with the page. This fixes the TASK-031 finding (max-zoom tip `page` on `page`); for example `inverseSurface`, with the action in `inversePrimary`.

### 3. Type scale and prominent values
- **`Type.kt`:** replace the template with a full app `Typography`:
  - **Values:** a display or headline style for key values (speed, altitude, vertical speed, compass heading) and a title style for secondary values (nearby distance, route values).
  - **Labels:** label and body styles.
  - **Titles:** a title style for card titles.
- **Tabular figures:** numeric values use `fontFeatureSettings = "tnum"`, so digits do not jump while values change.
- **Units:** shown smaller than the number in the same row (`AnnotatedString` or two `Text`s sharing a baseline), for example "**12 500** ft".
- **Accessibility:** values stay readable at font scale 2.0 without clipping. Use `maxLines` plus ellipsis, or a layout that wraps. The existing large-font tests must stay green.
- **Unchanged:** the content descriptions and live regions stay the same. This change is visual only.

### 4. Light and dark themes
- **Theme setting:** a new Settings row "Theme" in the Display section with the options System default (default), Light and Dark.
  - It is stored in the existing display settings repository (SharedPreferences, new key `theme_mode`, default `system`) and exposed as a `Flow`.
  - It is applied in `MainActivity`/`AppRoot` before the first frame.
- **Dark scheme:** the purple palette, refined by points 1 and 2. It must still read as Smart Flight next to `promo/screen-1.png`.
- **Light scheme:**
  - Light surfaces tinted with the brand purple (page and cards near-white lavender), purple top bar or surface, and dark text.
  - The accent is a darker cyan or teal, so it meets the contrast targets on light surfaces.
  - The satellite chart, horizon, map buttons, route line, plane marker and pins must work in both themes. Tokens they use either stay theme-independent (the map tiles are the same in both) or get light variants.
- **Theme-aware tokens:** `SmartFlightColors` becomes two instances, `DarkSmartFlightColors` and `LightSmartFlightColors`, and `SmartFlightTheme(darkTheme: Boolean)` picks one. There is still **no dynamic color**: the brand stays fixed.
- **Startup background:** the window background follows the theme (`values/themes.xml` and `values-night/themes.xml`) so there is no flash of the wrong color at launch. The forced in-app choice overrides night mode through `AppCompatDelegate` or `uiMode`; pick the simplest approach that works with the current Activity and document it.
- **System bars:** status and navigation bar icons switch between light and dark appearance with the theme.

## Out of scope
- Layout and card order, the city picker layout, the map tiles, the launcher icon and the strings, except new Settings strings, which need English and Polish entries (the translation completeness test enforces this).
- Dynamic color (Material You). The brand stays fixed.
- Moving theme state to a different storage (keep SharedPreferences).

## Requirements
Required:
- WCAG AA as above, in both themes, enforced by `ContrastTest`.
- No color literal outside `ui/theme` (`ColorLiteralGuardTest` stays green). Every token is in the documented token table, and `PaletteGuardTest` is updated to the new table.
- **Theme behavior:**
  - The theme follows the system by default.
  - The in-app choice persists across launches and survives rotation.
  - There is no wrong-color flash at launch.
- All existing accessibility behavior (semantics, touch targets, live regions) is unchanged.
- **Behavior changes:** every one is listed in the PR, for example the default theme for users on a light system theme, which becomes light.

Recommendations:
- Keep the token count small, deriving tones from the palette instead of adding unrelated colors.
- Add `@Preview`s of one card in both themes.

## Acceptance criteria
- [ ] `ContrastTest` asserts ≥ 4.5:1 for every label and value on every surface, and ≥ 3:1 for components, in both schemes — verified by: CI unit test
- [ ] `SmartFlightThemeTest`: System follows the `night` / `notnight` qualifier, while Light and Dark override it; there is still no dynamic color — verified by: CI unit test
- [ ] Theme setting: the Settings row shows the three options, the choice persists in the repository and is applied to the dashboard — verified by: CI unit test (ViewModel, repository, Compose)
- [ ] Every dashboard card uses `SmartFlightCard`, with no per-card shape or elevation literals — verified by: CI unit test (source scan) or code review
- [ ] Key values use tabular figures and the value style, and units are rendered smaller than numbers — verified by: CI unit test (`TextHierarchyTest` or a Compose test checking text style and size)
- [ ] `DashboardColorsPixelTest` samples page, card and top bar colors in both themes — verified by: CI unit test
- [ ] Translation completeness stays green with the new strings — verified by: CI unit test
- [ ] The dark theme still reads as Smart Flight next to `promo/screen-1..3.png`; the light theme is readable in daylight; there is no launch flash; the top bar reacts to scrolling — verified by: HUMAN on device

## Tests to add or update
- `ContrastTest`, `PaletteGuardTest`, `SmartFlightThemeTest`, `SmartFlightColorsTest`, `DashboardColorsPixelTest`, `TextHierarchyTest`.
- A new theme-mode repository test, Settings ViewModel and screen tests, and a `SmartFlightCard` test.
- Card tests that assert font sizes or colors.

## Risks and edge cases
- **Map overlays** (`ic_plane_marker`, pins, route line) sit on the map tiles, not on theme surfaces. Keep them theme-independent and check their contrast against the tiles, not the theme.
- **The horizon's sky and ground** and the satellite green/red must stay recognizable in the light theme.
- **Theme switching:** Robolectric `night` qualifiers and `AppCompatDelegate.setDefaultNightMode` can recreate the Activity. Keep the ViewModel state intact and make sure the tests do not depend on recreation timing.
- **Large fonts:** the bigger value styles increase the clipping risk at large font scale and on narrow screens (320dp). The existing RTL and large-font tests must cover it.
