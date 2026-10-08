# TASK-043 — Redesign: flight parameters as value tiles

## Goal
Flight parameters show the designs' value tiles: a large value, the label and the unit below it,
side by side with dividers, in the waiting state too ("—").

## Context
- Designs: `docs/design/2026-10-redesign/` (Flight parameters card): Speed, Altitude, Vertical speed.
- Today: label/value rows (speed, vertical speed, altitude, pressure), and a centered waiting text.
- Pressure is not in the designs (the device in the screenshot has no barometer); it is kept as the
  fourth tile so no information is lost.

## Dependencies
- TASK-039, TASK-040.

## Scope
- `FlightParametersCard`: tiles in the design order Speed, Altitude, Vertical speed, Pressure.
  Value = the number only (`headlineMedium`, tabular figures, "—" while unknown), then the label
  (`labelLarge`, `labelText`) and the unit symbol (`bodyMedium`, `labelText`).
- Columns: 4 when the card is at least 480dp wide, otherwise 2×2; one column above font scale 1.3.
  Vertical `cardOutline` dividers between tiles of a row.
- The waiting state shows the tiles with "—" under the header pill (TASK-040).
- Each tile keeps the row's spoken description ("Speed, 36.0 kilometres per hour").

## Behavior changes (visual)
- The unit moves from after the number to its own line; the order of altitude and vertical speed
  follows the design.

## Acceptance criteria
- [ ] Tiles show the converted value, label and unit for every unit setting — verified by: CI unit test (`FlightParametersCardTest`, `DashboardCardsUnitsTest`, `TextHierarchyTest`)
- [ ] Not clipped at font scale 2 on a 320dp screen — verified by: CI unit test
- [ ] Matches the designs on phone and tablet widths, both themes — verified by: HUMAN on device
