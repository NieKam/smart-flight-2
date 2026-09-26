# TASK-030 — Map: great-circle route line and a purple plane marker

## Goal
Draw the route as a geodesic (great-circle) line in the original dark purple, 7px wide, and show the position as the original plane icon tinted purple, rotated by the direction of travel.

## Context
- Parity finding 9 (REGRESSION, MEDIUM): original `~/smart-flight/app/src/main/java/kniezrec/com/flightinfo/cards/map/MapCardView.kt:88-92` sets `isGeodesic = true`, color `purple_dark` (#484685), width 7; `promo/screen-2.png` shows the curved Frankfurt–San Francisco arc. Rewrite: `Polyline(map)` with `Color.CYAN`, default width, not geodesic (`MapCard.kt:349-354` before moves). The rewrite's added departure/destination markers (`:355-383`) are an improvement — keep them.
- Parity finding 21 (VISUAL_MISMATCH, LOW, plus behavior change): original marker `small_plane_icon` tinted `purple_dark` (`common/SmartFlighAppExtensions.kt:20-27`, `MapCardView.kt:94-97`), rotated by the compass azimuth (`MapCardViewPresenter.kt:194-199`). Rewrite: `res/drawable/ic_plane_map.xml` is a four-point #00D4FF star rotated by GPS bearing, 0° (north) when there is no bearing (`map/MapState.kt:50`).
- Planner decision (marker rotation): use GPS bearing when available (moving; better in flight, where the magnetic compass is unreliable), otherwise the compass heading (restores the original behavior when stationary and avoids pointing north). README open question offers "compass only" as in the original.

## Dependencies
- TASK-016, TASK-017, TASK-026 (heading available from the orientation Flow).

## Original app reference
- `cards/map/MapCardView.kt`, `MapCardViewPresenter.kt`, `common/SmartFlighAppExtensions.kt`, `res/drawable/small_plane_icon.png`, `res/drawable-v21/small_plane_icon.xml`, `promo/screen-2.png`.

## Scope
- Route line: osmdroid `Polyline` with `isGeodesic = true` (verify the osmdroid 6.1.20 API; otherwise densify points along the great circle with a pure function), color token = page purple #484685, width 7px (scale to density if it looks too thin: document).
- Marker: vector from `small_plane_icon.xml` tinted #484685; anchor center; rotation = GPS bearing if the latest fix has one, else compass heading, else 0.
- `MapViewModel` combines `LocationRepository.fixes` with the orientation heading (only while the map is collected).
- Pure function for marker rotation selection, unit tested.

## Out of scope
- Map controls and zoom tip (TASK-031).

## Requirements
Required:
- Geodesic, purple, 7px route line; purple plane marker; rotation rule as decided.
- Behavior change (rotation source when stationary) listed in PR.

## Acceptance criteria
- [ ] Pure tests: rotation choice (bearing present / absent with heading / neither); great-circle densification if implemented — verified by: CI unit test
- [ ] Code review: `isGeodesic` (or densified points), color and width — verified by: code review
- [ ] Visual match with `promo/screen-2.png` — verified by: HUMAN on device

## Tests to add or update
- `MapMarkerRotationTest`, `MapViewModelTest`.

## Risks and edge cases
- Geodesic lines crossing the antimeridian: check osmdroid handles it; otherwise split the polyline.
- Collecting orientation for the map keeps the sensor on while the map is visible; acceptable (shared registration with the Course card).
