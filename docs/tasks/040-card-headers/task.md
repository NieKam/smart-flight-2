# TASK-040 — Redesign: card headers with icon badge and status pill

## Goal
Every card shown in the designs starts with the same header: an icon in a round tinted badge, the
title in the value color, and (while waiting) a status pill on the right.

## Context
- Designs: `docs/design/2026-10-redesign/light_new_design.png`, `new_design_dark.png`.
- Today the titles are muted (`labelText`), centered in waiting states and have no icon; each card
  builds its own title.

## Dependencies
- TASK-039.

## Scope
- New shared composables in `ui/theme`: `CardHeader(icon, title, titleModifier, trailing)` and
  `StatusPill(text)` (accent dot + accent text on `accentContainer`). At a font scale above 1.3 the
  pill goes below the title so neither is clipped.
- New vector icons (Material-style, tinted in code): `ic_card_satellite`, `ic_card_course`,
  `ic_card_horizon`, `ic_card_flight`, `ic_card_place`.
- Apply the header to GNSS status, Course, Horizon, Flight parameters and Nearby city, in every
  state (waiting, error, available). Static states keep their body text and actions centered below.
- GNSS waiting state: "Searching…" pill (new string, Polish "Wyszukiwanie…"); the searching animation
  is tinted with the accent (Lottie dynamic color) instead of the original cyan.
- Flight parameters waiting: the "Waiting for GPS position…" text moves into the pill (TASK-043 adds
  the value tiles below it).
- Route, Map and the permission card are not in the designs: only the palette applies to them.

## Requirements
- Live regions and content descriptions stay as they are (the title nodes keep their modifiers).
- No color literal outside `ui/theme`.

## Acceptance criteria
- [ ] The five cards show the header (icon, title in `valueText`) — verified by: CI unit test
- [ ] GNSS waiting shows "Searching…", flight parameters waiting shows its waiting text in the pill — verified by: CI unit test
- [ ] Polish translation complete — verified by: CI unit test (`TranslationCompletenessTest`)
- [ ] Headers match the designs in both themes — verified by: HUMAN on device
