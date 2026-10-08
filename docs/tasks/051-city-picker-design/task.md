# TASK-051 — Redesign: city picker

## Goal
The city picker matches `docs/design/2026-10-redesign/choose_dest.png`: back arrow, bold title and Cancel in the top row; a pill search field with a search icon and a tonal Search button; the instruction with "Long-press" in bold; the map with 12dp corners, themed tiles and a recenter button; a full-width pill Confirm.

## Context
- Today: muted title, underlined text field, card-colored buttons, untinted map, no recenter.

## Dependencies
- TASK-047 (map tile tint, map buttons).

## Scope
- `RoutePicker` layout restyled; the field keeps its "City name" label (it stays accessible after typing; empty, it reads as the design's placeholder).
- New `smartFlightTonalButtonColors` (Search) and `smartFlightFilledButtonColors` (Confirm: accent with page-colored text, `accentContainer` when disabled).
- Picker map: `mapTileTint` filter (dark scheme), recenter button reusing the dashboard `MapButton`: centers on the selected city, otherwise returns to the world view.
- `route_map_instruction` carries `<b>` around "Long-press" (EN, PL), rendered with `AnnotatedString.fromHtml`.

## Behavior change
- New recenter button on the picker map (design).
- Back arrow in the top row (same action as Cancel and system back).

## Review feedback (PR #51)
1. The "− +" buttons on both maps (osmdroid's built-in zoom controller, never turned off) look bad: they are hidden and replaced by zoom in/out icon buttons in the map buttons' style, stacked at the middle of the end edge, on the dashboard map and the picker map (new strings "Zoom in"/"Zoom out", PL "Przybliż"/"Oddal").
2. "Selected: City (Country)" is too small: the selected city is a card with the pin badge, the city in the title style and the country below; it is still read as "Selected: City (Country)".

## Acceptance criteria
- [ ] Search, select, long-press nearest, confirm, cancel, errors unchanged — verified by: CI unit test (`RoutePickerTest`, `RoutePickerOverlayTest`, `RouteInteractionTest`)
- [ ] Matches the design in both themes — verified by: HUMAN on device
