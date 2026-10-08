# TASK-044 — Redesign: icons on the flight parameter tiles

## Goal
Each flight parameter tile shows a small icon above its value, as in `docs/design/2026-10-redesign/new_design_cards.png`.

## Context
- TASK-043 introduced the tiles (value, label, unit). The new design adds a gauge (speed, pressure), a mountain (altitude) and up/down arrows (vertical speed).

## Dependencies
- TASK-043.

## Scope
- New tinted vector icons `ic_tile_gauge` (Material "speed"), `ic_tile_altitude` (Material "terrain"), `ic_tile_vertical_speed` (Material "swap vert").
- `ParameterTile` draws the icon (24dp, `labelText`, decorative) above the value.

## Acceptance criteria
- [ ] Tiles keep their texts, order and spoken descriptions — verified by: CI unit test (`FlightParametersCardTest`)
- [ ] Icons match the design in both themes — verified by: HUMAN on device
