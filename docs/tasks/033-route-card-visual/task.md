# TASK-033 — Route card visuals: large city names, take-off/landing icons, trash icon

## Goal
Restore the original route card look (two large city slots side by side with take-off/landing icons, detail rows with muted labels and light values, trash icon to clear the route) while keeping the rewrite's functional improvements (clear a single endpoint, accessibility).

## Context
- Parity finding 23 (VISUAL_MISMATCH, LOW): original `~/smart-flight/app/src/main/res/layout/route_card_layout.xml` — 100dp take-off/landing icons, city names at 22sp side by side, delete icon; `promo/screen-2.png` ("Frankfurt am … San Francisco", "Distance 9156.4 km", "Distance to destination 9156.9 km", "Estimated arrival in 11h 42min", trash icon). Tapping a slot picks a city; long-press clears one endpoint (`RouteCardViewPresenter.kt:183-207`). Rewrite: "Departure: X [Edit] [Clear]" text rows (`ui/route/RouteCard.kt:100-140` before moves).
- Parity MODERNIZATION (accepted, keep): clear one endpoint / clear all; arrival as localized date-time in destination zone + "HH:MM" duration; distance between cities.
- TASK-021 made details available with destination only.

## Dependencies
- TASK-021, TASK-018 (palette tokens).

## Original app reference
- `layout/route_card_layout.xml`, `res/drawable/take_off_icon.xml`, `landing_icon.xml`, `delete_icon.xml`, `cards/route/RouteCardView.kt`, `promo/screen-2.png`.

## Scope
- Two tappable slots (departure with take-off icon, destination with landing icon); empty slot shows the icon with "Pick departure"/"Pick destination"; filled slot shows the city name (22sp, ellipsized) and country.
- Clearing one endpoint: long-press on a slot (as original) PLUS an accessibility custom action "Clear departure/destination" (keep the rewrite's discoverability for TalkBack; a visible small clear icon on a filled slot is acceptable).
- Detail rows as label/value (`labelText` / `valueText` tokens from TASK-018); city names `valueText`; take-off/landing/trash icons `valueText` (the original drawables are filled #D9D9ED); trash icon button clears the whole route (with content description). Remove the ad-hoc #D9D9ED/#FFB4AB literals if any survived (errors use the `error` token).
- Keep restore-error state with retry.

## Out of scope
- Route logic.

## Requirements
Required:
- Visual structure per screenshot; all current actions still reachable (tap, long-press, accessibility actions, trash).

## Acceptance criteria
- [ ] Compose tests: tap slot opens picker for that endpoint; long-press clears it; trash clears all; accessibility actions exist — verified by: CI unit test
- [ ] Matches `promo/screen-2.png` route card — verified by: HUMAN on device

## Tests to add or update
- `RouteCardTest`.

## Risks and edge cases
- Long city names ("Frankfurt am Main") ellipsize as in the screenshot.
