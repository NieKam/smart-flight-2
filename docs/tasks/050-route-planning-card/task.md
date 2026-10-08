# TASK-050 — Redesign: Route card before a destination is chosen

## Goal
Until a destination is chosen (first start, or only a departure), the Route card shows the design `docs/design/2026-10-redesign/pick_city_card.png`: a "Select flight route" header with a plane badge, a one-line explanation, and an illustration with two pins, a dashed arc and a plane between two skylines, above "Pick departure" and "Pick destination".

## Context
- Today this state shows the hint "Tap to select flight route" and two large take-off/landing icons.
- The filled layout (destination chosen, TASK-046) is unchanged.

## Dependencies
- TASK-046, TASK-045 (skyline).

## Scope
- `RouteCard`, destination not chosen: `CardHeader` (plane icon, `route_hint` now "Select flight route"; PL "Wybierz trasę lotu"), the new `route_planning_body`, the decorative `RoutePlanningIllustration` behind the two endpoint slots. Each half of the illustration plus its label is the endpoint's button (tap picks, long press/accessibility action clears a chosen departure). A chosen departure shows its city and country instead of "Pick departure". The trash icon moves to the header while any endpoint is set.
- Shared skyline drawing (`ui/theme/CitySkyline.kt`), also used by the Nearby city illustration.

## Acceptance criteria
- [ ] Pick/clear/clear-all interactions and descriptions unchanged — verified by: CI unit test (`RouteCardTest`, `RouteInteractionTest`)
- [ ] Polish translation complete — verified by: CI unit test
- [ ] Matches the design in both themes — verified by: HUMAN on device
