# TASK-041 — Redesign: compass dial on the Course card

## Goal
The compass rose becomes the dial of the designs: a round face with an outline ring, minor ticks
every 10°, long ticks at N/E/S/W, the letters inside the ring and the plane in the center.

## Context
- Designs: `docs/design/2026-10-redesign/` (Course card). Today: four letters around a plane, no dial.
- The rotation logic (`shortestRotationTarget`, 200ms linear) and the test tags
  `course-direction-visual` / `course-plane` stay.

## Dependencies
- TASK-039, TASK-040.

## Scope
- `CompassRose` in `course/ui/CourseCard.kt`: Canvas dial (face `card`, ring `cardOutline`, ticks
  `labelText`), letters 20sp `labelText`, plane tinted `compassPlane`. Size stays 156dp.
- Heading value: the design's bolder weight (`displayMedium`, SemiBold); the cardinal above it stays
  in the accent.

## Acceptance criteria
- [ ] Existing course tests stay green (rotation, tags, semantics) — verified by: CI unit test
- [ ] Dial matches the designs in both themes; the plane still turns the short way — verified by: HUMAN on device
