# TASK-039 — Redesign: blue/navy palette, card surface and design tokens

## Goal
Move both themes to the palette of the new designs (`docs/design/2026-10-redesign/`): a cool
near-white page with white cards and a blue accent in the light theme, a navy page with slate cards
and a bright blue accent in the dark theme. All of it goes through the existing token layer
(`ui/theme/SmartFlightColors.kt` → Material `ColorScheme`), so every screen, dialog, Settings and the
picker follow without per-screen edits.

## Context
- **Human request (2026-10-08):** redesign the UI to match `light_new_design.png` and
  `new_design_dark.png`; `light_old.png` is the current light theme. This supersedes the planner
  decision "Palette: the app's colors match the original palette" and the TASK-038 lavender light
  scheme. The map overlays (route line, pins, plane marker, map buttons) and the launcher icon are not
  in the designs and keep their colors.
- Colors were sampled from the design PNGs and adjusted only where WCAG AA failed (see table).
- Today a card stands out from the page by tone only (TASK-038, ratio >= 1.15). In the new light
  design the card is white on #F4F6FB (ratio ~1.08) and separated by a hairline outline and a soft
  shadow, so the card gets an outline token.

## Dependencies
- TASK-038 (merged).

## Token table
| Token | Light | Dark | Notes |
|---|---|---|---|
| `page` (+ `window_*`) | #F4F6FB | #111722 | |
| `card` | #FFFFFF | #1A2330 | |
| `raised` (dialogs, menus) | #F9FAFD | #232D3C | |
| `topBar` | = page | = page | seamless top bar in both themes (design) |
| `topBarScrolled` | #E9EDF5 | #1E2735 | |
| `cardOutline` (new) | #E2E7F0 | #283245 | 1dp card outline, compass ring |
| `accent` | #1A66D9 | #4A9BFD | design #247EFB darkened to pass AA on the light page |
| `accentPressed` | accent @ 50% | accent @ 50% | |
| `accentContainer` (new) | #E7F0FD | #1C304A | icon badge, status pill, Calibrate button |
| `accentLight` (Settings highlight) | #CFE0FA | #23436B | |
| `labelText` | #5B6785 | #9DAED0 | |
| `valueText` | #172340 | #E8ECF5 | card titles now use it (design) |
| `toolbarTitle` | #172340 | #E8ECF5 | |
| `satelliteUsed` / `satelliteUnused` | #2E7D32 / #C62828 | #4CAF50 / #FF7A6E | unchanged |
| `error` | #B3261E | #FFB4AB | |
| `inverseSurface` / `inverseOnSurface` / `inversePrimary` | #172340 / #F1F4FA / #8DBBFF | #E8ECF5 / #172340 / #1558C0 | snackbar |
| `compassPlane` (new) | = valueText | = accent | the plane in the compass dial (design: dark plane in light, blue in dark) |
| `horizonSkyTop` (new) / `horizonSky` | #2A73C9 / #3B87DB | #0F3D74 / #1C5EA0 | sky gradient, lightest at the horizon; design #6CB1FA darkened so the white marks keep 3:1 |
| `horizonGround` / `horizonGroundBottom` (new) | #3E7F5B / #2B6249 | #295744 / #143839 | ground gradient |
| `horizonLine`, `overlay*`, `mapInk`, `mapHalo` | unchanged | unchanged | |

The horizon becomes themed (it was theme-independent purple).

## Scope
- `SmartFlightColors`: the values above, new tokens `cardOutline`, `accentContainer`, `compassPlane`,
  `horizonSkyTop`, `horizonGroundBottom`; KDoc.
- `smartFlightColorScheme`: `primaryContainer`/`secondaryContainer` = `accentContainer`,
  `outlineVariant` = `cardOutline`; the rest unchanged.
- `SmartFlightCard`: 1dp `cardOutline` border, large shape (16dp), 1dp shadow elevation.
- `values/colors.xml` `window_light`/`window_dark` = the new page colors.
- Tests: `SmartFlightColorsTest`, `PaletteGuardTest` (token table), `ContrastTest` (new pairs: accent
  on accentContainer, compassPlane on card, horizon marks on every horizon tone; the TASK-038
  card-vs-page check becomes a card-outline check), `DashboardColorsPixelTest` stays green.

## Out of scope
- Layout changes (TASK-040–043). Map overlays, map drawables, launcher icon.

## Acceptance criteria
- [ ] Tokens equal the table — verified by: CI unit test (`SmartFlightColorsTest`, `PaletteGuardTest`)
- [ ] Every text pair >= 4.5:1, every component pair >= 3:1 in both themes — verified by: CI unit test (`ContrastTest`)
- [ ] Window backgrounds equal the page tokens — verified by: CI unit test
- [ ] Both themes look like the designs' colors (page, cards, top bar, dialogs, Settings) — verified by: HUMAN on device
