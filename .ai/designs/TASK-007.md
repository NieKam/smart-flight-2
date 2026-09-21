# TASK-007 — Offline live-position map card design

## Purpose and scope

Add one map card to the foreground Compose dashboard, immediately after Nearby city. It displays packaged offline map tiles and the aircraft's current valid position during the active foreground location session. The card supports first-fix centering, live marker movement, course orientation, explicit recentering, and expand/collapse.

This design covers the user-visible card, its states, and the boundaries needed by the Developer. It does not add route drawing, route selection, city search, network tiles, background tracking, notifications, settings, or a new permission.

## Source of truth and design classification

### Required behavior from the task

- Render exactly one Map card after Nearby city only when fine location is granted and the dashboard is in the foreground.
- Use the unchanged `osmdroid.zip` asset with an offline-only osmdroid tile provider. The map must never request network tiles.
- Center on the first valid fix in each foreground session. Later valid fixes move only the marker and do not recenter a manually panned or zoomed viewport.
- Recenter on the latest valid fix, or `(32.0, -32.0)` at zoom `3` when no valid fix exists.
- Orient the marker from the existing shared course/bearing path; missing or invalid course leaves a neutral orientation.
- Expand/collapse the map while preserving access to both controls and the outer dashboard scroll.
- Stop updates and detach map resources on pause/permission loss/destroy; a new session starts without the previous marker or first-fix state.
- Show a readable unavailable state with a retry action for archive copy/open/read failure. Retry must be safe and offline.

### Observed legacy behavior

- `MapCardView` places a full-bleed map in a card-sized container, with a recenter control in the top trailing corner and a resize control in the bottom trailing corner.
- `MapCardViewPresenter` centers on the first callback, moves the marker on later callbacks, resets to `(32.0, -32.0)` at zoom `3`, and rotates the plane marker from course callbacks.
- `BaseMapView` enables fling/multitouch, disables data connection, and uses `MapquestOSM`, `.jpg`, 256 px tiles, zoom 1–6.
- The old implementation also has route polylines, saved-route broadcasts, service bindings, preferences, and a larger-zoom option. Those are excluded by TASK-007.

### Design decisions and intentional deviations

- Keep the map full-bleed visually, but wrap it in a rounded/clipped Material card and place controls in high-contrast scrims. This retains the legacy map character while making controls visible over variable tiles.
- Use Compose state for card presentation and accessibility, with a narrow `AndroidView`/native-map boundary for `MapView`. Map objects and lifecycle calls remain outside domain state.
- Replace legacy image-only controls with 48 dp minimum Material icon buttons, resource-backed content descriptions, visible focus indication, and an expanded/collapsed state announcement.
- Replace the legacy blank/implicit map failure behavior with a centered, readable unavailable panel and `Try again` action. This is required for resilience and accessibility.
- Use a deterministic expanded presentation rather than legacy height animation: the card changes between a normal map height and a taller bounded height while remaining one child of the existing dashboard scroll. The animation, if used, must not create a nested scrolling container.
- Do not copy the promo image into the application. It is a visual reference only.

## Screen placement and hierarchy

The existing `GnssStatusScreen` remains a single vertically scrolling column:

1. GNSS status
2. Flight parameters
3. Course
4. Horizon
5. Nearby city
6. **Map**

The map card uses the same outer `12.dp` horizontal spacing, `widthIn(max = 600.dp)`, bottom spacing, purple card surface, 10 dp corner radius, and dashboard scroll state as the other cards. It is not composed while permission onboarding is visible, so no map instance or archive work starts during onboarding.

### Card anatomy

- **Card surface:** existing `cardPurple`, with the existing dashboard elevation and rounded shape. Clip map content and overlays to the card shape.
- **Map content:** full card width, no separate map title bar, so the map remains the primary visual surface. A small accessible state layer may overlay the map when loading or when no valid fix exists.
- **Top-trailing control:** `My location`/recenter icon button, inset 12 dp from the logical trailing and top edges.
- **Bottom-trailing control:** expand or collapse icon button, inset 12 dp from the logical trailing and bottom edges.
- **Control treatment:** 48 dp minimum touch target; use a compact contrasting container/scrim behind the icon so it remains legible on light or dark tiles. Use logical `topEnd`/`bottomEnd` placement for RTL.

## Size and responsive behavior

Use a useful aspect ratio rather than a fixed legacy card height:

- Normal: map content is approximately `16:9`, with a minimum usable height of 240 dp and a maximum normal height of 360 dp. The exact height may be constrained by available width and accessibility font scale.
- Expanded: use approximately `4:3`, capped to the available window height so the card is still part of the outer scroll. On narrow portrait windows, prioritize a minimum 240 dp map region; on wide landscape windows, allow the width-driven height to grow without making the dashboard awkwardly tall.
- Do not add a `verticalScroll` or other nested scrolling modifier to the map. Map gestures may pan/zoom within the native map; the outer dashboard remains the only vertical content scroll.
- Respect display cutout and gesture insets through the screen's existing edge-to-edge content padding. Keep the card controls inset from the card bounds so they are not hidden by cutouts.
- At 200% font scale, status text and retry action may increase card height. Never clip text or controls; map content may yield height before controls do.

## Visual hierarchy and typography

Follow the current dashboard language:

- Page background: existing dark purple page color.
- Card: existing purple card surface.
- Primary text: existing accessible light lavender token.
- Action/focus accent: existing cyan action token.
- Map controls: icon plus tonal/scrim container with sufficient contrast against the tile below; no meaning relies on color alone.
- Loading/error panel title: existing `titleLarge` treatment used by Nearby city/GNSS cards (approximately 22 sp, 28 sp line height, medium weight).
- Supporting/loading/error copy: existing body-large treatment (approximately 18 sp, 25 sp line height), centered in the panel.
- Retry: existing text-button pattern with at least 48 dp height and the established focus border.

All visible copy and content descriptions are Android string resources. Suggested resource roles are `map_title`, `map_loading`, `map_unavailable`, `map_unavailable_body`, `map_try_again`, `map_try_again_hint`, `map_recenter`, `map_recenter_hint`, `map_expand`, `map_expand_hint`, `map_collapse`, `map_collapse_hint`, and concise marker/state announcements. Exact wording may follow the project's established voice.

## States and transitions

The UI should expose a small, explicit presentation state independent of the native map object:

| State | Visible presentation | Interaction |
| --- | --- | --- |
| Loading archive/map | Card-sized centered panel over the purple card surface, title/status text, no blank map region; controls are omitted or disabled until a map is ready | No map gestures; no retry unless the operation fails |
| Ready, no valid fix | Offline map at default center and zoom 3, without a plane marker; controls available | Recenter returns to default center; expand/collapse works |
| Ready, valid fix | Offline map with one plane marker; first valid fix centers once | Marker follows later valid fixes; user pan/zoom is preserved |
| Ready, valid fix with course | Same as ready, marker rotates to latest valid course | Orientation is supplementary, not the only course cue |
| Unavailable | Centered title, concise explanation, and visible `Try again` action in the card; do not leave a misleading blank map | Retry starts one guarded archive/map load; no network request |
| Expanded | Same map/data state with taller bounded content region and collapse control | Outer dashboard scroll remains usable |

State transitions:

- Permission granted + foreground start: create a new map session, clear marker/position/first-fix/manual-viewport state, begin archive preparation asynchronously.
- Archive succeeds: create/configure one native map instance and display ready/no-fix at `(32.0, -32.0)`, zoom 3.
- First valid fix: place marker and center exactly once for this session.
- Later valid fix: update marker position only. Invalid, non-finite, or out-of-range coordinates are ignored without altering the marker or viewport.
- Valid course/bearing: update marker orientation after safe normalization. Missing/non-finite/out-of-range course leaves the last neutral orientation; it must not invalidate a valid position.
- Explicit recenter: center on latest valid position, otherwise default center and zoom 3. This is the only later operation that intentionally changes the user's viewport.
- Retry: invalidate the previous load attempt, cancel/ignore stale callbacks, and allow at most one active map/archive attempt. Do not duplicate `MapView`, overlays, or listeners.
- Foreground stop, permission loss, or destruction: stop delivery, detach marker/overlays/listeners, release the map, and invalidate asynchronous archive callbacks. A later foreground start creates a clean session.

## Offline map and interop boundary

The Developer should keep the following boundary explicit:

- Asset: package the original `app/src/main/assets/osmdroid.zip` unchanged.
- Storage: copy asynchronously to app-private cache/files storage using a temporary/partial filename and an atomic move/rename only after successful completion and validation. A failed/interrupted copy must not be treated as usable.
- Native map: create one controlled osmdroid `MapView` for the active card/session. Configure offline tile provider and `setUseDataConnection(false)` (or the supported equivalent), `MapquestOSM`, `.jpg`, 256 px tiles, zoom 1–6, and initial zoom 3.
- Lifecycle: forward the appropriate map lifecycle events through the interop boundary and dispose it from the Compose lifecycle/session teardown. Clear/detach overlays explicitly.
- Location/course: consume the existing shared `FlightLocationFix` and course stream. Do not add a location, GNSS, compass, sensor, service, notification, or permission observer.
- Stale work: associate copy/load/map callbacks with a session token or equivalent invalidation mechanism. A callback from a previous session, retry, or disposed composition must be ignored.

The map card's state model should be testable without `MapView`. Keep coordinate validity, first-fix centering, manual viewport preservation, recenter/default behavior, course normalization, and session invalidation as pure rules where practical.

## Interaction and accessibility

- Recenter button content description: announces `My location` and its behavior. It is enabled in ready and no-fix states; in loading/unavailable state it is disabled or absent with no misleading action.
- Expand button content description changes to `Expand map`; collapse changes to `Collapse map`. State changes should be announced politely, for example through a state description/live region, without interrupting frequent location updates.
- Plane marker must have an accessible map summary or host semantics describing that the aircraft position is shown; marker rotation is never the only course communication.
- Map content should expose a concise state summary such as offline map ready, current position unavailable, or map unavailable. Avoid exposing every GPS update as a TalkBack announcement.
- Controls use logical start/end placement, keyboard and switch focus order (recenter before expand in reading order), visible focus rings, and at least 48 dp touch targets.
- Ensure icon-only controls have no decorative-only semantics and remain usable with magnification and large text. Text in loading/error panels wraps and remains readable.
- Map gestures may be performed with touch; accessibility actions for recenter and expand/collapse remain available independently of map gestures.

## Legacy reference inventory inspected

Original implementation and layout:

- `app/src/main/java/kniezrec/com/flightinfo/cards/map/MapCardView.kt`
- `app/src/main/java/kniezrec/com/flightinfo/cards/map/MapCardViewPresenter.kt`
- `app/src/main/java/kniezrec/com/flightinfo/base/BaseMapView.kt`
- `app/src/main/java/kniezrec/com/flightinfo/cards/map/MapHelper.kt`
- `app/src/main/res/layout/map_card_layout.xml`
- `app/src/main/res/drawable/small_plane_icon.png`
- `app/src/main/res/drawable/drawing_pin_icon.xml`
- `app/src/main/res/drawable/ic_expand.xml`
- `app/src/main/res/drawable/ic_shrink.xml`
- `app/src/main/assets/osmdroid.zip` (the task records its SHA-256 as `d69fb06c06ae3d66fc95f51365ef350a310aed3f9092b9684bffeeb0ecfcb8ff`)
- `promo/promo.png` (1024×500 visual reference only; not application content)

Rewrite implementation and theme inspected:

- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/NearbyCityCard.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/permission/PermissionOnboardingScreen.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/theme/Theme.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/theme/Color.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values/colors.xml`

## Verification expectations for the Developer

Focused tests should cover:

- finite/range coordinate validation;
- first-fix centering once per session;
- later marker movement without viewport recentering;
- explicit recenter and default center/zoom;
- valid, missing, and invalid course orientation;
- session invalidation and stale archive callback suppression;
- atomic archive copy failure and retry without duplicate instances;
- Compose semantics, control labels, enabled states, expand/collapse, and card placement after Nearby city where the configured test environment supports it.

The completed feature must leave GNSS, Flight parameters, Course, Horizon, and Nearby city behavior unchanged in both successful and map-unavailable paths.

## Unresolved questions

None. TASK-007 defines the map source, zoom limits, lifecycle, offline behavior, and excluded legacy features sufficiently for implementation.
