# TASK-045 — Redesign: Nearby city waiting illustration

## Goal
While the Nearby city card waits (for a position or for the lookup), it shows the design's skyline illustration with a location pin below the waiting text.

## Context
- Design: `docs/design/2026-10-redesign/new_design_cards.png` (Nearby city card). Today the waiting states show only the text.

## Dependencies
- TASK-040.

## Scope
- `NearbyCityCard`: waiting and looking-up states show the header, the centered waiting text and a decorative Canvas skyline (hills in `horizonGround` at low alpha, buildings in `accentLight`) with the `ic_card_place` pin in the accent. 120dp high, 12dp corners, hidden from accessibility.
- The error state (retry) and the available state (rows) are unchanged.

## Acceptance criteria
- [ ] Waiting texts and retry unchanged — verified by: CI unit test (`NearbyCityCardTest`)
- [ ] Illustration matches the design in both themes — verified by: HUMAN on device
