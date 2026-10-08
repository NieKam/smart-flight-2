# TASK-046 — Redesign: Route card flight arc

## Goal
With both cities chosen, the Route card shows the design's dotted flight arc with a plane between the departure (start) and the destination (end).

## Context
- Design: `docs/design/2026-10-redesign/new_design_cards.png` (Route card). The details rows and the trash icon already match; the empty-slot state is not in the design and stays.

## Dependencies
- TASK-039.

## Scope
- `RouteCard`: a decorative `RouteArc` (dotted quadratic arc in `accent` at 70%, plane icon in `accent` heading east) between the two endpoint slots when both are set.

## Acceptance criteria
- [ ] Route interactions (choose, long-press clear, clear all) unchanged — verified by: CI unit test (`RouteCardTest`, `RouteInteractionTest`)
- [ ] Arc matches the design in both themes; long city names still ellipsize — verified by: HUMAN on device
