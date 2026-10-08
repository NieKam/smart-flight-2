# TASK-038 — Light theme: tinted "lavender mist" surfaces instead of near-white

## Goal
Make the light theme look like a current Material 3 (Expressive) app instead of a flat sheet of white. Page, top bar, cards and dialogs get distinct, brand-tinted tones, so cards visibly stand out from the page without shadows. The dark theme does not change.

## Context
- **Human feedback (2026-10-08), after TASK-037 on device:** "that white color for light day theme looks bad". The human asked for a better color that modern apps use.
- **Why it looks white today** (`ui/theme/SmartFlightColors.kt`, `LightSmartFlightColors`):
  - page #EFEEF8, card #FBFAFE, raised (dialogs) #FFFFFF, top bar #E4E3F3: every surface is within a few tones of pure white.
  - `SmartFlightCard` is a flat, tonal card (no shadow). Card vs page differ by a luminance ratio of only 1.11, so cards almost disappear and the screen reads as one white area.
- **What current apps do:** Material 3 / M3 Expressive light schemes (Android 15/16 system apps, Now in Android) never put content on pure white. Surfaces come from a neutral tonal palette tinted with the brand hue: the page sits in a mid-light container tone (~90–92), content containers in a lighter tone (~96–97), dialogs in the lightest (~99). The top bar uses the page tone and turns one step darker when content scrolls under it. Separation comes from tone, not from shadows or white.

## Dependencies
- TASK-037 (merged).

## Proposal (assumed approved, per the human's standing preference; the human checks on device)
Light surfaces from the brand purple hue (#5B5999, ~243°), "lavender mist":

| Token | Old | New | Role |
|---|---|---|---|
| `page` (and `window_light`) | #EFEEF8 | **#E6E4F4** | page, Settings, picker |
| `card` | #FBFAFE | **#F7F6FC** | dashboard cards, picker buttons |
| `raised` | #FFFFFF | **#FDFCFF** | dialogs, menus |
| `topBar` | #E4E3F3 | **#E6E4F4** (= page, seamless M3 top bar) | top bar at rest, status bar |
| `topBarScrolled` | #D8D6EC | **#DAD7EF** | top bar while scrolled |

Unchanged: text (`labelText` #55537D, `valueText` #1E1C3A, `toolbarTitle` #2C2163), accent #00687A, error, satellite, snackbar, horizon and map tokens.

Contrast (WCAG, computed by the coordinator):

| Foreground | page/topBar #E6E4F4 | card #F7F6FC | raised #FDFCFF | topBarScrolled #DAD7EF |
|---|---|---|---|---|
| labelText #55537D | 5.74 | 6.69 | 7.04 | 5.12 |
| valueText #1E1C3A | 13.08 | 15.24 | 16.02 | 11.66 |
| accent #00687A | 5.14 | 5.99 | 6.30 | 4.58 |
| error #B3261E | 5.22 | 6.08 | 6.39 | 4.65 |
| toolbarTitle #2C2163 | 11.14 | 12.99 | 13.65 | 9.93 |
| satelliteUsed #2E7D32 (bar on card) | — | 4.77 | — | — |
| satelliteUnused #C62828 (bar on card) | — | 5.23 | — | — |

Card vs page luminance ratio: 1.17 (was 1.11); raised vs card: 1.05.

Alternative considered and rejected: a brand-purple top bar (#484685) with a white title in the light theme. It adds identity but needs light status-bar icons in the light theme, differs from M3 practice and widens the scope.

## Scope
- `LightSmartFlightColors`: the five values above. `raised` gets its own literal; `White` stays for the horizon line and the dark toolbar title.
- `values/colors.xml` `window_light` = #E6E4F4, so the launch window matches the page (no flash).
- Update the KDoc of `LightSmartFlightColors` and the token table.
- Tests: `PaletteGuardTest` (token table), `SmartFlightColorsTest`, `DashboardColorsPixelTest` if it samples the light colors, `ContrastTest` stays green. Add a check that the light `card` is distinguishable from `page` (luminance ratio ≥ 1.15) so the cards cannot fade into the page again.

## Out of scope
- The dark scheme, typography, layout, card shape, text and accent colors, map and horizon tokens.

## Requirements
- No color literal outside `ui/theme` and `colors.xml` (`ColorLiteralGuardTest`).
- WCAG AA as in TASK-037 (`ContrastTest`).
- Behavior change (visual only): the light theme surfaces change; list it in the PR.

## Acceptance criteria
- [ ] Light tokens equal the table above — verified by: CI unit test (`SmartFlightColorsTest`, `PaletteGuardTest`)
- [ ] `ContrastTest` green; light card vs page luminance ratio ≥ 1.15 — verified by: CI unit test
- [ ] `window_light` equals the light page — verified by: CI unit test or code review
- [ ] The light theme no longer reads as white, cards stand out from the page, no launch flash — verified by: HUMAN on device
