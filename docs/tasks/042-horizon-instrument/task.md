# TASK-042 — Redesign: horizon sky/ground and tonal Calibrate button

## Goal
The artificial horizon gets the designs' blue sky and green ground (gradients, themed) with 12dp
rounded corners, and "Calibrate" becomes a full-width tonal pill button with a target icon.

## Context
- Designs: `docs/design/2026-10-redesign/` (Horizon card). Today: purple sky/ground, text button.
- Long press ("Reset to level") and its accessibility action must keep working; the button cannot be
  a Material `Button` because it has no long click, so the existing `combinedClickable` box is
  restyled.

## Dependencies
- TASK-039, TASK-040.

## Scope
- `HorizonInstrument`: vertical gradients `horizonSkyTop` → `horizonSky` and `horizonGround` →
  `horizonGroundBottom`, corner radius 12dp. Marks stay `horizonLine`.
- `HorizonAction`: `accentContainer` pill, `accent` content, optional leading icon
  (`ic_calibrate`, Material "gps fixed"); full width for Calibrate; the focus border follows the pill
  shape. The retry action uses the same style.

## Acceptance criteria
- [ ] Calibrate click, long press and the custom accessibility action still work — verified by: CI unit test (`HorizonCardTest`)
- [ ] White marks >= 3:1 on every sky and ground tone — verified by: CI unit test (`ContrastTest`)
- [ ] Horizon matches the designs in both themes — verified by: HUMAN on device
