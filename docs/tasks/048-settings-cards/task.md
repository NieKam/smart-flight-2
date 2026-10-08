# TASK-048 — Redesign: Settings as grouped cards

## Goal
Settings matches the design: each section (Display, Monitoring, Units) is a card with an icon header; rows show a title with a short explanation, switches in the Material 3 style (accent track, light thumb), and value rows with the value and a chevron.

## Context
- Design: `docs/design/2026-10-redesign/new_design_cards.png` (Settings, light and dark).
- Today: plain sections on the page, rows with dividers, switch rows show "On/Off"/"Portrait/Sensor" as subtitle.

## Dependencies
- TASK-040 (CardHeader).

## Scope
- Sections in `SmartFlightCard` with `CardHeader` (`titleStyle` parameter added, `titleMedium` here) and icons `ic_settings_display` (Material "palette"), `ic_settings_monitoring` ("notifications"), `ic_settings_units` ("straighten"); `ic_chevron_right` on navigation rows.
- Switch rows: visible subtitle is an explanation (new strings, EN + PL: keep screen on, portrait, larger zoom; the background notification uses its existing description). "Notifications are blocked" is shown instead, in the error color, while blocked. Semantics (content description, state description, toggleable state) unchanged.
- Theme row value in the accent; unit values muted; chevrons. Dividers (`cardOutline`) between rows, none after the last.
- `smartFlightSwitchColors`: checked = accent track with `onAccent` (new token, white) thumb; unchecked = `cardOutline` track, `labelText` thumb and border.

## Behavior change (visual)
- The On/Off (Portrait/Sensor) words are no longer shown under switch rows; the switch shows the state and TalkBack still reads it.

## Acceptance criteria
- [ ] Settings tests (rows, dialogs, highlight, hidden cards, notifications) green — verified by: CI unit test
- [ ] Polish translation complete — verified by: CI unit test
- [ ] Settings matches the design in both themes — verified by: HUMAN on device
