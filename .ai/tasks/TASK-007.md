# TASK-007 — Add the offline live-position map card

## Status

READY_FOR_DESIGN

## Goal

Add a foreground-only offline map card to the Compose flight dashboard.  The card displays the bundled map around the aircraft's current GPS position, follows the first valid position, shows a plane marker oriented to the current course when available, and provides accessible controls to recenter and expand the map.

## Context

TASK-001 through TASK-006 establish the permission-gated foreground dashboard with GNSS status, flight parameters, course, horizon, and nearby-city lookup.  The next major legacy dashboard surface is the map card.  The original Smart Flight map is useful during flight without network access because it reads packaged osmdroid tiles and overlays the current aircraft position.

The rewrite does not yet contain a map SDK, map asset, route-selection UI, or settings screen.  This task is limited to the live-position offline map experience and must fit the rewrite's Compose, lifecycle, accessibility, and single-location-session conventions.  It must not pull route planning or legacy background-service behavior into this increment.

## Original Application

The legacy dashboard creates `MapCardView` after the route card in `app/src/main/java/kniezrec/com/flightinfo/cards/adapter/CardViewContainer.kt`.  The relevant implementation is:

- `app/src/main/java/kniezrec/com/flightinfo/cards/map/MapCardView.kt` creates an osmdroid `MapView`, configures a plane marker and route polyline, shows a recenter control and resize control, centers on the first location callback, and cleans overlays when detached.
- `app/src/main/java/kniezrec/com/flightinfo/cards/map/MapCardViewPresenter.kt` receives foreground location updates, sends the first position to the map center, follows later positions with the marker, rotates the marker from the sensor course, and resets to `(32.0, -32.0)` at zoom `3` when no location is available.
- `app/src/main/java/kniezrec/com/flightinfo/base/BaseMapView.kt` uses osmdroid's `OfflineTileProvider`, disables network map access, enables fling/multitouch, and reads tiles from the cached map archive.  Its map source is `MapquestOSM`, tile extension `.jpg`, tile size 256 px, minimum zoom 1, and normal maximum zoom 6.
- `app/src/main/java/kniezrec/com/flightinfo/cards/map/MapHelper.kt` copies the packaged `assets/osmdroid.zip` to the app cache directory asynchronously; `common/Constants.kt` names the file `osmdroid.zip`.
- `app/src/main/res/layout/map_card_layout.xml` places the map full-bleed in a card-sized container, with top-right recenter and bottom-right expand controls.
- `app/src/main/res/drawable/small_plane_icon.png`, `drawing_pin_icon.xml`, `ic_expand.xml`, and `ic_shrink.xml` are the legacy map marker/control references.

The original map also draws a route polyline and reacts to saved route broadcasts.  Those behaviors require the legacy `RouteCardView`/`FindCityActivity` flow and are not part of this task because the rewrite has no route feature yet.  The original app's background `LocationService`, sensor service, notification behavior, and preference-controlled larger zoom must not be copied.

The original offline map archive is `/home/ai-dev/smart-flight/app/src/main/assets/osmdroid.zip` (approximately 27 MB; SHA-256 observed during analysis: `d69fb06c06ae3d66fc95f51365ef350a310aed3f9092b9684bffeeb0ecfcb8ff`).  The original promo reference is `/home/ai-dev/smart-flight/promo/promo.png` (1024×500); it is a visual reference only and must not be modified or copied as application content.

## Current Application

`app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt` owns the permission-gated foreground lifecycle and constructs the existing platform controllers.  `ForegroundCourseObservationCoordinator` starts and stops the single `FlightParametersController` location session and the course controller.  `FlightParametersController` already delivers each current `FlightLocationFix` to a fan-out callback before updating its own card state; `NearbyCityController` consumes this shared path without registering another location listener.

`app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt` renders one scrollable purple dashboard in this order: GNSS status, Flight parameters, Course, Horizon, and Nearby city.  The new map card should be added after Nearby city, retaining the existing 12 dp spacing, 600 dp maximum card width, edge-to-edge handling, and scroll container.

The rewrite currently has no map-related source files, assets, dependencies, or resources.  Its Gradle catalog contains Compose/AndroidX dependencies only.  `FlightLocationFix` provides latitude, longitude, and optional bearing; the existing orientation/course implementation is the source of course state and must remain the only sensor observation path.

## Functional Requirements

- Show exactly one `Map` card after the Nearby city card while fine location is granted and the dashboard is in the foreground.  Do not show the card or perform map/location work while permission onboarding is visible.
- Use the packaged offline tile archive and configure the map so it never requests network tiles.  The map must remain usable with network connectivity disabled.
- Render a live aircraft plane marker for each valid current-session GPS fix.  A valid position has finite latitude in `-90..90` and longitude in `-180..180`; malformed fixes must be ignored without crashing or moving the marker.
- Center the map on the first valid fix of each foreground observation session.  Later fixes update the marker position without automatically recentering the user's manually selected map viewport.
- Provide a `My location`/recenter control.  When a current valid fix exists, it centers the map on that fix; when none exists, it returns to the defined default center `(32.0, -32.0)` and zoom `3`.
- Rotate the plane marker to the latest valid course/bearing supplied by the existing shared course/location path.  If no valid course is available, retain a neutral orientation; do not start a second sensor or location listener.
- Provide an expand/collapse control that changes the map card between its normal and expanded presentation while keeping both controls reachable and usable.  The expanded state must not break the surrounding dashboard scroll behavior.
- Stop map updates, detach overlays/listeners, and release map resources when the dashboard leaves the foreground, fine permission is lost, or the activity is destroyed.  A new foreground session must clear the prior marker/position and perform first-fix centering again.
- If the offline archive cannot be copied, opened, or read, keep the dashboard usable and show an explicit map-unavailable state with a retry action.  Retrying must not crash, issue network requests, or create duplicate map instances/listeners.
- Preserve TASK-001 through TASK-006 behavior when the map is unavailable or its loading/copy operation fails.

## Technical Requirements

- Use osmdroid as the map implementation, with an offline tile provider and the packaged `osmdroid.zip` archive.  Add only the dependency/configuration needed for this map feature and keep its version compatible with the rewrite's current Android/Kotlin/Compose toolchain.
- Copy `app/src/main/assets/osmdroid.zip` unchanged from the original project into the rewrite's app assets.  The copy must be treated as immutable packaged source data; do not modify the original project or generate map tiles.
- Store the copied archive in app-private cache/files storage using safe, asynchronous I/O.  Avoid blocking composition or the main thread, and handle partial/corrupt copy failures without exposing a partially written archive as valid.
- Keep the osmdroid view behind a narrow Compose interop boundary if a native `MapView` is required.  Map objects, overlays, and lifecycle calls must not leak into domain state or Compose state models.
- Reuse the existing single foreground `FlightLocationFix` delivery path and existing course observation.  Do not register a second `LocationManager` listener, GNSS listener, compass listener, background service, or notification for the map.
- Reset map session state on the same foreground lifecycle transitions used by the existing dashboard controllers.  Guard asynchronous archive-copy/retry callbacks so stale work cannot attach a map after teardown or a newer session.
- Configure offline behavior explicitly (`setUseDataConnection(false)` or the equivalent supported osmdroid API) and use the legacy tile source parameters: `MapquestOSM`, `.jpg`, 256 px tiles, zoom 1 through 6, with default zoom 3.  A larger-zoom preference is out of scope.
- Keep all visible labels, content descriptions, loading/error text, and retry text in Android resources.  Controls must have accessible names and at least 48 dp touch targets.
- Do not add route persistence, city search, geocoding, network tile access, a settings screen, background tracking, or unrelated architectural modules.

## UI Requirements

- Follow the existing Smart Flight visual language: dark purple page background, purple rounded card surface, logical start/end layout, and the established dashboard spacing/card width.
- The map should occupy the card's content area with an aspect ratio/height that is useful in portrait and landscape.  The map may be taller in expanded mode, but it must remain within the dashboard's existing scroll container and must not create a second nested scrolling surface.
- Place a clearly discoverable recenter button in the map's upper trailing corner and an expand/collapse button in the lower trailing corner, matching the legacy placement while adapting icons and contrast to the Compose theme.
- The aircraft marker must be visually distinct from map tiles and remain visible at the current location.  Marker orientation must not be the only indication of course; controls and state must remain understandable without color.
- Loading and unavailable states must provide a concise text explanation and a visible `Try again` action when retry is possible.  Do not show a blank purple rectangle with no status.
- Support portrait, landscape/expanded windows, RTL, display cutouts/gesture insets, keyboard/switch navigation, TalkBack, magnification, and 200% font scale.  Do not clip controls or place them below an inaccessible map region.

## Acceptance Criteria

- [ ] With fine location granted and the foreground dashboard visible, exactly one offline map card appears after Nearby city; onboarding does not create a map instance or map work.
- [ ] The rewrite packages the unchanged legacy `osmdroid.zip` asset and successfully displays its tiles without network access when the archive is available.
- [ ] The map explicitly refuses network tile access; airplane mode or an offline test still shows packaged tiles and no network request is made.
- [ ] A controlled valid current-session fix places the plane marker at the supplied coordinates and centers the map on the first valid fix only.
- [ ] Subsequent valid fixes move the marker without undoing a user's manual pan/zoom; invalid or non-finite fixes do not move it or crash.
- [ ] A valid course/bearing rotates the marker through the existing shared course/location path; no second location or sensor registration is created.  Missing/invalid course leaves a neutral marker orientation.
- [ ] The recenter control centers on the latest valid position, or on `(32.0, -32.0)` at zoom `3` when no valid position exists.
- [ ] The expand/collapse control changes the map presentation, remains accessible, and does not break dashboard scrolling or the other cards.
- [ ] Starting a new foreground session clears the previous marker/position and restores first-fix centering; pausing, permission loss, and destruction detach map resources and cancel/invalidate stale archive work.
- [ ] A missing, corrupt, or failed-to-copy archive shows a readable map-unavailable state with a functioning retry action and does not crash or access the network.
- [ ] Focused tests cover coordinate validation, first-fix/manual-viewport rules, recenter/default behavior, course-orientation handling, session invalidation, archive-copy failure/retry, and Compose control semantics where the configured test environment supports them.
- [ ] Existing GNSS, Flight parameters, Course, Horizon, and Nearby city behavior remains unchanged when the map loads, updates, or fails.
- [ ] No route polyline, route selection, city picker/search, online map service, background service, notification, new runtime permission, or unrelated dependency is added.

## Implementation Plan

1. Add the unchanged legacy offline tile archive and the compatible osmdroid dependency/configuration; define the app-private copy/cache boundary with atomic/asynchronous copy and retryable failure handling.
2. Define a small map state/session model and testable rules for valid coordinates, first-fix centering, marker movement, manual viewport preservation, recentering, default center, and course orientation.
3. Extend the existing foreground location/course coordination so the map consumes the shared fix and course streams without registering duplicate platform observers; reset and invalidate map sessions with the existing lifecycle.
4. Build the Compose map card and native-map interop boundary, including offline configuration, marker/overlay cleanup, loading/error states, recenter and expand controls, resource-backed semantics, and responsive layout.
5. Add focused unit and Compose/instrumentation coverage, then run the configured checks in an Android SDK/device environment.

## Files / Components Likely Affected

- `app/build.gradle.kts` and `gradle/libs.versions.toml` for the compatible osmdroid dependency
- `app/src/main/assets/osmdroid.zip`
- `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- `app/src/main/java/kniezrec/com/flightinfo/flight/FlightParametersController.kt` and/or `course/ForegroundCourseObservationCoordinator.kt` for shared fix/session delivery
- New focused map state/controller/asset-copy/platform files under `app/src/main/java/kniezrec/com/flightinfo/map/`
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt` and a new map-card composable/interop file
- `app/src/main/res/values/strings.xml` and map-related drawable/vector resources as needed
- Focused tests under `app/src/test/` and `app/src/androidTest/`

## Reusable Existing Libraries / Components

- Rewrite: Compose Material 3, existing dashboard scroll/card primitives, `FlightLocationFix`, `FlightParametersController`, `ForegroundCourseObservationCoordinator`, and the existing course state/observation lifecycle.
- Original behavior/configuration: `MapCardView`, `MapCardViewPresenter`, `MapHelper`, `BaseMapView`, `map_card_layout.xml`, `Constants.OSMDROID_FILE`, and the original `osmdroid.zip` asset.
- Original visual assets to consider reproducing or adapting: `small_plane_icon.png`, `drawing_pin_icon.xml`, `ic_expand.xml`, and `ic_shrink.xml`; do not copy legacy view/presenter/service architecture wholesale.
- Do not reuse the legacy `LocationService`, `SensorService`, `FlightAppPreferences`, `LocalBroadcastManager` route broadcasts, `jsi`, `trove4j`, or route-selection activities for this task.

## Risks and Edge Cases

- The archive is large and must not block first composition; interrupted copies must not be mistaken for valid map data.  Cache invalidation/retry must be deterministic and safe across activity recreation.
- osmdroid `MapView` lifecycle and overlay cleanup can leak resources if Compose recomposition creates multiple instances.  Keep one controlled instance per active map card and explicitly detach it on disposal/session stop.
- Location callbacks and archive-copy completion can race with pause, permission revocation, retry, or a new session.  Stale callbacks must not move a newly created map or reattach a disposed view.
- The user may pan/zoom before later GPS fixes arrive.  Automatic marker movement must not unexpectedly recenter the viewport; only first fix and explicit recenter may do so.
- A fix may contain a valid position but no bearing, a bearing outside the normalized range, or a stale/invalid coordinate.  Normalize/ignore orientation safely without affecting position display.
- Devices with no GNSS, disabled location services, or no current fix must still display the defined map/default or unavailable state without starting extra observation work; GNSS availability remains governed by TASK-002.
- Offline tile coverage may be absent at a requested viewport or zoom.  The map must remain stable and communicate only archive/loading failure as specified; it must not silently fall back to network tiles.
- The legacy route polyline and saved city route are intentionally excluded and need a separate specification after route-selection behavior is defined for the rewrite.

## Open Questions

None.  The original map implementation and the rewrite's existing shared foreground streams define the required live-position behavior; route rendering and preferences are explicitly out of scope.
