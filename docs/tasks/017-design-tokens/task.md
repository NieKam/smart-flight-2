# TASK-017 — Design tokens in ui/theme and shared unit label helpers (no visual change)

## Goal
Collect the app's colors, typography roles and unit-label mappings into `ui/theme` and one shared helper, so the following visual parity tasks change one place. Pixel output stays the same.

## Context
- Architecture review F17 (VIOLATION, LOW), verified:
  - App-wide colors `cardPurple` (#5B5999) and `actionCyan` (#6CF0FF) live in `ui/permission/PermissionColors.kt:6-7` and are imported by every card (`CourseCard.kt:49-50`, `HorizonCard.kt:44-45`, `MapCard.kt:47-48`, `RouteCard.kt:27-28`, `NearbyCityCard.kt:38`, `FlightParametersCard.kt:42`, `GnssStatusScreen.kt:64-65`).
  - ~31 `Color(0x…)` literals across 11 UI files; `0xFFD9D9ED` redeclared in at least 5 files; `smartFlightPageColor` (#484685) in `PermissionOnboardingScreen.kt:52`.
  - `ui/theme/Theme.kt:13-58` is the unmodified template with dynamic color; the app palette bypasses it.
  - Unit → string-resource mapping duplicated in `FlightParametersCard.kt:156-275`, `UnitSettingsScreen.kt:294-343`, `RouteCard.kt:168-204`, `NearbyCityCard.kt:97-120`.
- Paths above are pre-TASK-016; files may have moved to feature packages.

## Dependencies
- TASK-016.

## Scope
- `ui/theme/SmartFlightColors.kt`: named tokens for all colors currently used (page, card, accent, text, horizon sky/ground, error, map button background, picker background #211D46), exposed through a `CompositionLocal` or an extended color object accessed via `SmartFlightTheme.colors`. Keep current values; TASK-018 changes values.
- Replace every `Color(0x…)` literal in UI files with a token. `contrastRatio` moves to `ui/theme` with its tests.
- `ui/units/UnitLabels.kt` (or inside `displayunits/ui`): one set of `@StringRes` mappings (unit symbol, long name, accessibility label) used by all four files.

## Out of scope
- Any color value change, dynamic color removal, typography change (TASK-018).

## Requirements
Required:
- No visual change: all existing Compose tests pass unchanged.
- No `Color(0x` literal outside `ui/theme` (grep), except inside `@Preview` functions if any.

## Acceptance criteria
- [ ] Grep for `Color(0x` outside `ui/theme` returns nothing — verified by: code review (optionally a JVM test scanning sources)
- [ ] Single unit-label mapping used by the four call sites — verified by: code review + CI unit test for the mapping
- [ ] All tests pass — verified by: CI unit test

## Tests to add or update
- `UnitLabelsTest` (every enum value maps to an existing resource).
- Move `contrastRatio` tests.

## Risks and edge cases
- Accidentally changing a color while tokenizing; compare values in review.
