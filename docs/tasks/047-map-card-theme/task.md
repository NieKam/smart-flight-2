# TASK-047 — Redesign: themed map tiles, route line and map buttons

## Goal
The map card follows the design: in the dark theme the offline tiles are dimmed to navy and the route line is light; the recenter button shows a target icon and the expand button sits in a rounded square.

## Context
- Design: `docs/design/2026-10-redesign/new_design_cards.png` (Map card, light and dark).
- Today the tiles, route line (`mapInk`) and buttons are theme-independent (TASK-030/031).

## Dependencies
- TASK-039.

## Scope
- New tokens `mapRoute` (route line: light #484685 = original purple_dark, dark #E8ECF5) and `mapTileTint` (multiplied over the tiles: light #FFFFFF = unchanged, dark #4A5878).
- `MapCard`: applies `mapTileTint` as a multiply color filter on the tiles overlay; passes `mapRoute` to `MapOverlays`. Recenter uses `ic_calibrate`; the expand/collapse button container is a rounded square (the theme's small shape). Button colors stay `mapInk` on `mapHalo`.
- Remove the now unused `drawing_pin_icon`.
- Tests: tokens, palette, contrast of the route line on a tinted white and black tile.

## Behavior change (visual)
- Dark theme: dimmed tiles, light route line.

## Acceptance criteria
- [ ] Route line >= 3:1 on tinted white and black tiles in both themes — verified by: CI unit test (`ContrastTest`)
- [ ] Map buttons keep tags, descriptions and actions — verified by: CI unit test (`MapCardTest`)
- [ ] Dark and light maps match the design; pins and plane stay visible — verified by: HUMAN on device
