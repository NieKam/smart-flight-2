# TASK-021 — Route: remaining distance and ETA with only a destination

## Goal
Show "distance to destination" and the arrival estimate as soon as a destination is set and a fix exists, whether or not a departure city is set. The departure only adds the "Distance" between the two cities.

## Context
- Parity finding 3 (REGRESSION, HIGH), verified:
  - Original: `~/smart-flight/app/src/main/java/kniezrec/com/flightinfo/cards/route/RouteCardViewPresenter.kt:134-144` — setting city B calls `setRouteDetailsData()` → `updateRemainingRouteInfo` (distance to B and ETA) and shows the detail labels; city A is optional and only adds "Distance" (`:235-242`). Long-press clearing A only resets the between-cities distance (`:183-194`).
  - Rewrite: `route/RouteController.kt:186-199` (now `RouteViewModel` after TASK-012) publishes `details = null` unless both endpoints are set; `RouteCard` renders details only when present.
- Parity MODERNIZATION (accepted, keep): arrival shown as localized date-time in destination zone + "HH:MM" duration; clear one endpoint / clear all.
- Map route line needs both endpoints (original `MapCardViewPresenter.loadSavedPointsAsync` draws the path only when A and B exist) — keep that rule.

## Dependencies
- TASK-016.

## Original app reference
- `cards/route/RouteCardViewPresenter.kt`, `layout/route_card_layout.xml`, `promo/screen-2.png` (route card).

## Scope
- `RouteDetails.fixedDistanceKm` becomes nullable (present only with both endpoints); remaining distance and ETA computed from destination + latest fix.
- `routeDetails(...)` pure function: signature `(departure: City?, destination: City, fix: RouteFix?, now)`.
- `RouteViewModel`: details whenever destination != null.
- `RouteCard`: show "Distance" row only with departure; show remaining distance/ETA rows with destination (placeholder "—" until a fix/speed exists, as the original showed labels with dashes).
- Map overlay unchanged (both endpoints).

## Out of scope
- Route card visual redesign (TASK-033); ellipsoidal distances (TASK-035).

## Requirements
Required:
- Destination-only route shows distance to destination and ETA when a fix with positive speed exists.
- Departure-only route shows the departure name and no details (as original).
- Behavior change vs current rewrite: list in PR.

## Acceptance criteria
- [ ] Pure function tests: destination only (with/without fix, zero speed), both endpoints, departure only — verified by: CI unit test
- [ ] ViewModel test: clearing departure keeps remaining distance/ETA — verified by: CI unit test
- [ ] Compose test: destination-only card shows remaining distance and arrival rows — verified by: CI unit test

## Tests to add or update
- `RouteModelsTest`, `RouteViewModelTest`, `RouteCardTest`.

## Risks and edge cases
- Speed 0 or missing: arrival shows "—" (original showed "-:- (∞)"); keep the rewrite's placeholder, consistent with accepted modernization.
