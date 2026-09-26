# TASK-030 — Map: great-circle route line and a clearly visible purple plane marker

## Goal
Draw the route as a geodesic (great-circle) line in the original dark purple, 7px wide, and show the current position as the original plane icon tinted purple that is always visible on top of the map, rotated by the GPS track when moving and by the compass when stationary.

## Context
- Parity finding 9 (REGRESSION, MEDIUM): original `~/smart-flight/app/src/main/java/kniezrec/com/flightinfo/cards/map/MapCardView.kt:88-92` sets `isGeodesic = true`, color `purple_dark` (#484685), width 7 (`PATH_WIDTH`); `promo/screen-2.png` shows the curved Frankfurt–San Francisco arc. Rewrite: `Polyline(map)` with `android.graphics.Color.CYAN`, default width, not geodesic (`ui/gnss/MapCard.kt:349-354` before moves). The rewrite's added departure/destination markers (`:355-383`) are an improvement — keep them, recolored to the palette (below).
- Parity finding 21 (VISUAL_MISMATCH, LOW, plus behavior change): original marker `small_plane_icon` (`res/drawable-v21/small_plane_icon.xml`: 32dp vector, nose up, fill #D9D9ED) tinted `purple_dark` with `PorterDuff.Mode.SRC_ATOP` (`common/SmartFlighAppExtensions.kt:20-27`, `MapCardView.kt:94-97`), anchored center, rotated by the compass azimuth (`MapCardViewPresenter.kt:194-199`), placed only once a location arrives (`MapCardViewPresenter.kt:51-52`, `MapCardView.kt:168-172`). Rewrite: `res/drawable/ic_plane_map.xml` is a four-point #00D4FF star (32dp) rotated by GPS bearing, 0° (north) when there is no bearing (`map/MapState.kt:50`).
- Plane marker visibility (human requirement: the marker must actually be visible; planner code review, not in the reports):
  - Update trigger: the marker was only created/moved when `AndroidView.update` happened to re-run (`MapCard.kt:300-339`); TASK-014 fixed this with immutable map state and an extracted `MapOverlays.sync(...)`.
  - Z-order: osmdroid draws overlays in list order. The plane marker is added first (`MapCard.kt:331`), the route line and the departure/destination pins are added later (`:351-366`), so they are drawn on top of the plane.
  - Contrast: a cyan (#00D4FF) star on the light MapQuest tiles (light blue ocean, beige land) is hard to see; route pins are #4DD0E1 / #FFB74D (`res/drawable/ic_route_departure.xml`, `ic_route_destination.xml`), not palette colors, and the departure pin is similar to the plane's cyan.
  - Tap: default osmdroid `Marker` opens an (empty) info window on tap.
  - Before the first fix there is no marker, in both apps (keep).
- Human decision (marker rotation): GPS track when moving, compass when stationary.
- Palette tokens come from TASK-018 (`page` = #484685, `valueText` = #D9D9ED).

## Dependencies
- TASK-014 (map ViewModel and overlay sync), TASK-016, TASK-018 (palette tokens), TASK-026 (heading available from the orientation Flow).

## Original app reference
- `cards/map/MapCardView.kt`, `MapCardViewPresenter.kt`, `common/SmartFlighAppExtensions.kt`, `res/drawable/small_plane_icon.png`, `res/drawable-v21/small_plane_icon.xml`, `res/drawable/ic_city_found_marker.xml`, `take_off_icon.xml`, `landing_icon.xml`, `promo/screen-2.png`.

## Scope
- Route line: osmdroid `Polyline` with `isGeodesic = true` (verify the osmdroid 6.1.20 API: `Polyline.setGeodesic`; otherwise densify points along the great circle with a pure function), color `page` #484685, width 7px as the original (`outlinePaint.strokeWidth`; if it looks too thin on high-density screens, scale by density and document), round caps.
- Plane marker (`MapOverlays.sync` from TASK-014):
  - Drawable: import `small_plane_icon.xml` as `res/drawable/ic_plane_marker.xml` (32dp, nose up) and delete `ic_plane_map.xml`. Fill `page` #484685 plus a 1.5dp `valueText` (#D9D9ED) outline/halo path so the plane stays visible on the same-colored route line and on dark terrain. (The outline is a small, deliberate addition to the original look for visibility; list it in the PR.)
  - If tinting at runtime, `mutate()` the drawable before tinting so other users of the resource are unaffected.
  - Anchor `ANCHOR_CENTER, ANCHOR_CENTER`; `infoWindow = null` and a click listener that consumes the tap (no empty bubble). The map is always north-up, so the marker rotation is the absolute heading.
  - Z-order: the plane marker is always the last overlay (drawn on top). Whenever route overlays are added or removed, re-append the plane marker at the end.
  - Visible from the first fix on; no marker before the first fix; after location stops, the marker stays at the last known position (as the original).
- Route pins: keep departure/destination pins, recolored to the palette: `page` pin shape (as the picker pin `ic_city_found_marker`) with a `valueText` take-off / landing glyph (from the original `take_off_icon.xml` / `landing_icon.xml`), so they are distinguishable from the plane by shape; drawn below the plane.
- Marker rotation (pure function, unit tested): `markerRotation(fix, compassHeading, previous)`:
  - use the GPS bearing when the fix has a bearing and speed ≥ 2.0 m/s (planner threshold ≈ 7 km/h; GPS track is unreliable below walking/jogging speed); recommendation: hysteresis (switch back to compass below 1.5 m/s);
  - otherwise the compass heading (display-relative heading from the orientation Flow, same value as the Course card) if available;
  - otherwise keep the previous rotation (initially 0).
- `MapViewModel` combines `LocationRepository.fixes` with the orientation heading (only while the map is collected).
- Convert heading to osmdroid's `Marker.rotation`: check the direction convention of `Marker.setRotation` in osmdroid 6.1.20 source (clockwise vs counter-clockwise) and encode it in the pure function with a test; do not guess.

## Out of scope
- Map controls and zoom tip (TASK-031). Picker map (TASK-032).

## Requirements
Required:
- Geodesic, #484685, 7px route line; #484685 plane marker with light outline; route pins in palette colors.
- Plane marker on top of all other overlays, visible from the first fix, not tappable into an empty bubble.
- Rotation rule: GPS track when moving, compass when stationary, previous value otherwise.
- Behavior changes listed in PR (rotation source when stationary, marker look, pin colors, z-order).

## Acceptance criteria
- [ ] Pure tests: rotation choice (bearing + speed ≥ 2 m/s → bearing; bearing + slow → compass; no bearing + compass → compass; neither → previous; osmdroid direction conversion); great-circle densification if implemented — verified by: CI unit test
- [ ] Overlay sync test (extends TASK-014's): with a route set, the plane marker is the last overlay after route add, route change and route clear; exactly one plane marker; marker icon is `ic_plane_marker`; `infoWindow == null` — verified by: CI unit test (Robolectric `MapView`, or the overlay-list interface from TASK-014)
- [ ] Route line uses `isGeodesic` (or densified points), color #484685 and width 7 — verified by: CI unit test (overlay sync test reads the `Polyline` paint) + code review
- [ ] Palette guard (TASK-018) still passes; drawables use only palette colors — verified by: CI unit test + code review
- [ ] Plane clearly visible over ocean, land and the route line at zoom 3 and 6; nose points along the direction of travel when moving and follows the phone's heading when standing still — verified by: HUMAN on device
- [ ] Visual match with `promo/screen-2.png` (line color/curvature, plane) — verified by: HUMAN on device

## Tests to add or update
- `MapMarkerRotationTest`, `MapOverlaysSyncTest`, `MapViewModelTest`.

## Risks and edge cases
- Geodesic lines crossing the antimeridian: check osmdroid handles it; otherwise split the polyline.
- Collecting orientation for the map keeps the sensor on while the map is visible; acceptable (shared registration with the Course card).
- In flight the magnetic compass is unreliable; the speed threshold makes the GPS track the source whenever the aircraft moves.
