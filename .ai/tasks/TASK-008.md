# TASK-008 — Add offline route planning and map route overlay

## Status

READY_FOR_DESIGN

## Goal

Add an offline route card to the foreground Smart Flight dashboard so the user can choose a departure and destination city from the bundled city data, see route and live-to-destination information, and see the selected route overlaid on the existing offline map.

## Context

TASK-001 through TASK-007 established the permission-gated Compose dashboard, shared foreground location session, GNSS/flight/orientation cards, nearby-city lookup, and the offline osmdroid map. Route selection is the remaining major dashboard surface present in the original Smart Flight application. The rewrite already contains the city database boundary and map interop; this task connects them into a route feature without importing the legacy service, activity, or broadcast architecture.

The route is a straight geographic segment between two selected city records. It is informational and offline; this task does not add turn-by-turn navigation, aviation airway routing, or network geocoding.

## Original Application

The legacy dashboard creates `RouteCardView` in `app/src/main/java/kniezrec/com/flightinfo/cards/adapter/CardViewContainer.kt`, and its `RouteCardViewPresenter` is implemented in `app/src/main/java/kniezrec/com/flightinfo/cards/route/RouteCardViewPresenter.kt`. `res/layout/route_card_layout.xml` shows departure and destination controls, then reveals route details once both cities are selected:

- distance between the two cities;
- distance from the current aircraft location to the destination; and
- estimated arrival time at the destination.

`cards/route/FindCityActivity.kt`, `FindCityPresenter.kt`, `activity_find_city.xml`, and `disambiguation_item.xml` implement city selection. The user can search city names in the offline database, select one result when a search is ambiguous, use the map's long-press lookup, and confirm the selected city. The same picker is used for departure and destination, and an existing selection is loaded into the picker when editing.

`FlightAppPreferences.kt` stores the selected city IDs. `RouteCardViewPresenter` reloads those IDs, updates the fixed metric/default distance presentation, and updates remaining distance and estimated arrival from location-service callbacks. A short/long press on the selected city edits or clears that endpoint; the delete action clears both endpoints.

`cards/map/MapCardViewPresenter.kt` listens for route changes and draws a straight polyline between the two city coordinates, with endpoint markers managed by `MapCardView`. The legacy implementation communicates through `LocalBroadcastManager` and bound `FindCityService`/`LocationService`; those are behavioral references only and must not be copied.

The original promo reference is `/home/ai-dev/smart-flight/promo/promo.png` (1024×500, RGBA). Its dashboard composition and purple card treatment were used as visual context; inspectable layout/resources are the source of exact behavior because the image viewer was unavailable in this environment. Relevant visual resources include `take_off_icon.xml`, `landing_icon.xml`, `delete_icon.xml`, `city_on_map_icon.xml`, `ic_city_found_marker.xml`, and the shared colors/dimensions in `res/values/colors.xml` and `res/values/dimens.xml`.

## Current Application

`app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt` owns the foreground lifecycle and the single `FlightParametersController` location session. `FlightParametersController` forwards each current `FlightLocationFix` to its callback, and `ForegroundCourseObservationCoordinator` already fans that path out to `NearbyCityController`; route updates must reuse this path and must not register another location listener.

`app/src/main/java/kniezrec/com/flightinfo/nearby/AndroidNearbyCityRepository.kt` already copies and reads the immutable `app/src/main/assets/databases/cities_info.db`, and `CitiesDataSource`/`City`-equivalent domain records provide the fields needed for search and map points. `ui/gnss/GnssStatusScreen.kt` currently renders the cards in GNSS, Flight parameters, Course, Horizon, Nearby city, then Map order. Insert the route card immediately before Map so route information precedes its visual overlay.

`ui/gnss/MapCard.kt` wraps one osmdroid `MapView` using the packaged `osmdroid.zip`, explicitly disables network tile access, and already manages the live plane marker, map session reset, recenter, and expand/collapse. It currently has no route overlay or endpoint markers. The rewrite has no route state, picker screen/dialog, persistence for selected city IDs, or route calculations.

## Functional Requirements

- Show exactly one `Route` card immediately before the Map card while the fine-location dashboard is active. Do not create the card, picker, route calculations, or route overlays while permission onboarding is visible.
- Initially show a clear departure action and destination action. Use labels that distinguish the two endpoints and remain understandable in RTL and with large text.
- Selecting an endpoint opens an offline city picker backed by the existing bundled city database. The picker must support case-insensitive trimmed city-name search, display zero/one/multiple-result states, allow choosing one result, and require an explicit confirmation before returning the selected city.
- The picker must also support selecting a city from a valid map long-press coordinate by resolving the nearest city from the same offline dataset. If multiple name matches are returned by text search, do not silently choose one; require the user to choose a result.
- When editing an already selected endpoint, prefill/show that city and allow replacing it. An explicit clear action must be available for each endpoint and a delete/reset action must clear the entire route.
- Persist selected city IDs in app-private local preferences so a recreated activity restores valid selections. If a saved ID no longer resolves, discard only that invalid endpoint and show its selection action; never fabricate a city or crash.
- When both endpoints exist, show route details: departure, destination, distance between them, distance from the newest valid current foreground position to the destination, and estimated arrival at the destination.
- Calculate geographic distances using the city coordinates and the existing rewrite metric convention. Format distance locale-aware to one decimal place in kilometres. The distance between identical endpoints is `0.0 km`.
- Calculate estimated arrival from the newest valid current fix's finite positive ground speed and the geographic distance to the destination. Show a truthful unavailable value/state while there is no current fix or usable positive speed; do not display an infinite or fabricated ETA. When sufficient data exists, show destination-local arrival time plus remaining travel duration, using the destination's stored IANA time zone.
- Consume only the newest valid current-session fix. Invalid/non-finite/out-of-range coordinates and stale callbacks from a stopped or replaced session must not update distance or ETA.
- When both endpoints are selected, draw one straight route polyline between their coordinates on the existing offline map and show distinguishable departure/destination endpoint markers. When either endpoint is cleared or invalid, remove the polyline and both route markers while retaining the live aircraft marker.
- Route changes must update the visible map without recreating the map, interrupting the live plane marker, changing the map's offline-only configuration, or resetting the user's map viewport. Loading/restoring a saved route before the map is ready must apply it when the map becomes ready.
- Preserve the route and endpoint selection while the activity is recreated, but clear live remaining-distance/ETA values when the foreground observation session stops; the next valid current fix repopulates them. Permission loss must stop route calculations and map updates while retaining only valid saved endpoint IDs for the next authorized dashboard session.
- City-database open/read/search/nearest-city/time-zone failures must produce a readable picker/card error with retry where applicable and must not affect GNSS, flight parameters, course, horizon, nearby-city, or map availability states.

## Technical Requirements

- Reuse the rewrite's `cities_info.db` asset and existing nearby-city database copy/read boundary. Do not add `SQLiteAssetHelper`, JSI, trove4j, Room, a geocoding SDK, or a network service.
- Keep database I/O and search off the main thread. Model picker results, selected endpoints, route details, and route overlay data as small immutable values; do not expose cursors, `Location`, SQLite handles, or legacy Parcelable objects to Compose state.
- Extend the existing foreground location-fix fan-out/coordinator for current-position distance and ETA. Do not register another `LocationManager`, GNSS, sensor, service, notification, or background observer.
- Use the existing `MapView` interop boundary and osmdroid overlays. Configure route rendering as a straight `Polyline` plus two endpoint markers; route data must not enable network tile access or depend on map availability.
- Prefer one route state/controller boundary that owns endpoint selection, persistence, current-fix freshness/session invalidation, and derived details. Avoid recreating the entire dashboard architecture or copying the legacy MVP/service classes.
- Persist only city IDs and the selected endpoint roles. Writes must be atomic enough that a process interruption cannot produce an invalid pair that crashes startup. Clearing an endpoint must remove its stored ID.
- Use Android resources for all visible strings, labels, errors, picker actions, content descriptions, and accessibility state text. Use locale-aware number/time formatting and Android plurals/resources where needed.
- Preserve minSdk 31, edge-to-edge behavior, existing app identity, offline operation, and TASK-001 through TASK-007 lifecycle contracts.

## UI Requirements

- Match the existing dashboard's dark-purple background, purple rounded card surface, 12 dp spacing, logical start/end alignment, 600 dp maximum width, and resource-backed typography.
- The route card's incomplete state must visibly show two endpoint actions using take-off/departure and landing/destination concepts without relying on icon color alone. The complete state must show both city names and clearly labeled route/detail rows.
- Endpoint selection may be a Compose full-screen route or dialog, but it must provide a prominent city search field, Search action, result list, map long-press affordance/instruction, selected-city preview, and Confirm/Cancel actions. It must handle keyboard search/IME action, back/cancel without changing the current endpoint, and loading/error/zero-result states.
- The picker map may reuse the existing offline map archive and osmdroid configuration, but it must not start live aircraft tracking or create a second foreground location session. A long press outside valid coordinates must be ignored with an understandable message.
- Route details must remain readable at 200% font scale, support wrapping and RTL, expose each label/value pair to TalkBack, and keep every action at least 48 dp. Do not use hidden text, color alone, or clipped single-line city names as the only communication.
- The dashboard Map card must retain its current controls and live marker. Route overlays must have accessible descriptions or a companion text summary such as “Route from A to B”; endpoint markers must be visually distinct from the aircraft marker.
- Error and no-current-position states must explain what is missing and offer retry/edit/clear actions appropriate to the state. Do not show stale remaining distance or ETA after a session reset.

## Acceptance Criteria

- [ ] With fine location granted, exactly one Route card appears immediately before Map; onboarding and unauthorized states create no route picker, calculations, or overlays.
- [ ] A user can open departure and destination selection independently, search the offline city database, resolve zero/one/multiple results correctly, select a result, cancel without changing the endpoint, and confirm a new endpoint.
- [ ] A valid map long press in the picker resolves the nearest offline city and allows confirmation; no network request or live location listener is created by the picker.
- [ ] Selecting both endpoints displays their names, one-decimal locale-formatted kilometre distance, current-position-to-destination distance, and a truthful ETA when the current fix and positive speed are available.
- [ ] Missing current position or non-positive/invalid speed shows an explicit unavailable/waiting ETA state and never displays infinity, NaN, or a fabricated arrival time.
- [ ] A newer valid current-session fix updates remaining distance and ETA; malformed fixes, stale callbacks, paused sessions, and permission loss cannot overwrite current route state.
- [ ] Selected endpoint IDs survive activity recreation/process restart when records remain valid; missing saved records are discarded safely and do not crash the dashboard.
- [ ] Clearing either endpoint removes the route details, polyline, and endpoint markers as applicable; clearing the route leaves the live aircraft marker and Map card usable.
- [ ] With both endpoints selected, the existing offline Map card shows one straight polyline and distinct endpoint markers; route changes do not recreate the map, reset its viewport, or enable network tiles.
- [ ] Route overlays restore after the map becomes ready and are removed when the route becomes incomplete, while the live marker continues to update through the existing shared path.
- [ ] City database failures are localized to picker/route UI and expose a safe retry; GNSS, Flight parameters, Course, Horizon, Nearby city, and Map behavior remains unchanged.
- [ ] Focused automated tests cover search normalization/result disambiguation, nearest-city long-press resolution, endpoint persistence/invalid IDs, distance and ETA derivation/formatting, session and stale-fix invalidation, route-clear behavior, and Compose accessibility/actions; map overlay tests cover add/update/remove without a second map instance where the environment supports it.
- [ ] The implementation adds no network geocoder, online tiles, route engine, background service, notification, new runtime permission, legacy bound service, LocalBroadcastManager route contract, or unrelated dependency.

## Implementation Plan

1. Define immutable city-picker, endpoint, route-detail, and map-overlay state plus pure search normalization, coordinate validation, distance, speed/ETA, and persistence rules; add focused unit tests.
2. Reuse the existing city database repository for asynchronous name search and nearest-city lookup, including safe asset/read failures, cancellation, retry, and saved-ID restoration.
3. Add a lifecycle-safe route controller to the existing foreground fix fan-out, invalidating current-position details on session stop and forwarding route overlay state to the map without a second location observer.
4. Build the Compose Route card and offline picker UI with accessible endpoint actions, search/disambiguation, map long-press selection, confirm/cancel, edit, clear, and resource-backed loading/error states.
5. Extend the existing Map card interop to render/update/remove a straight route polyline and endpoint markers without disturbing the live marker or viewport; apply restored route state when the map is ready.
6. Add Compose/instrumentation coverage for picker and card semantics, run the configured unit/UI checks, and verify offline behavior and recreation/lifecycle cleanup on an Android environment.

## Files / Components Likely Affected

- `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- `app/src/main/java/kniezrec/com/flightinfo/course/ForegroundCourseObservationCoordinator.kt` and/or `flight/FlightParametersController.kt` for shared current-fix delivery
- `app/src/main/java/kniezrec/com/flightinfo/nearby/AndroidNearbyCityRepository.kt` or a small shared city-data/search boundary
- New route domain/controller/persistence files under `app/src/main/java/kniezrec/com/flightinfo/route/`
- New Compose picker and route card files under `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/` or a focused route UI package
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/MapCard.kt` and map state/interop files
- `app/src/main/res/values/strings.xml`, plurals, and route marker/vector resources as needed
- Focused tests under `app/src/test/` and `app/src/androidTest/`

## Reusable Existing Libraries / Components

- Rewrite: Compose Material 3, existing dashboard/card primitives, `FlightLocationFix`, `FlightParametersController`, `ForegroundCourseObservationCoordinator`, `AndroidNearbyCityRepository`, `cities_info.db`, osmdroid `MapView` interop, `MapSessionRules`, and the existing offline archive/configuration.
- Original behavior references: `RouteCardView`, `RouteCardViewPresenter`, `FindCityActivity`, `FindCityPresenter`, `route_card_layout.xml`, `activity_find_city.xml`, `MapCardViewPresenter`, `DistanceCalculator`, `FlightAppPreferences`, `City`, and `cities_info.db`.
- Original visual references: `take_off_icon.xml`, `landing_icon.xml`, `delete_icon.xml`, `city_on_map_icon.xml`, `ic_city_found_marker.xml`, shared legacy colors/dimensions, and `promo/promo.png`.
- Do not reuse legacy `FindCityService`, `LocationService`, `SensorService`, `LocalBroadcastManager`, JSI/trove4j, `SQLiteAssetHelper`, `FlightAppPreferences` itself, or old XML/MVP/service classes as implementation architecture.

## Risks and Edge Cases

- City-name searches can return many records; choosing the first result would change observed behavior and could select the wrong airport/city. Preserve explicit disambiguation.
- The city data contains city records rather than an aviation route graph; the polyline and calculations must remain straight-line informational values.
- A destination may be selected before any current fix or with zero/negative speed. Keep route selection available and show waiting/unavailable live details rather than stale or infinite ETA.
- Database work, picker dismissal, map readiness, and foreground lifecycle can race. Stale search/restore/overlay callbacks must be ignored after cancellation, endpoint replacement, pause, permission loss, or destruction.
- The user can pan/zoom the map while route state changes. Overlay updates must not recenter or recreate the map, and route overlays must not obscure or be confused with the aircraft marker.
- A saved city ID can become invalid, a city can contain malformed coordinates/time-zone data, and a long press can be outside valid bounds. Handle each locally and keep the rest of the dashboard usable.
- Persisted endpoint IDs must not leak a previous route into a deliberately cleared endpoint; clear operations must be durable before the card reports the route as incomplete.

## Open Questions

None. The original picker, route-card, and map-overlay behavior resolve the product choices; this specification fixes metric units and a straight-line route to match the rewrite's existing scope and explicitly excludes legacy preferences and services.
