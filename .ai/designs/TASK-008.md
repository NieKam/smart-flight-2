# TASK-008 — Offline route card and map overlay design

## Purpose and scope

Add one route surface to the authorized foreground dashboard. A user can choose a departure and destination city from the bundled city database, review straight-line route information, and see that route on the existing offline map. The feature is informational; it does not provide turn-by-turn navigation, airway routing, online geocoding, or a second location session.

The route card appears immediately before Map, after Nearby city. It exists only while the fine-location dashboard is active. Permission onboarding and unauthorized states do not create route UI, picker UI, calculations, persistence reads that drive visible route state, or map overlays.

This document defines the user experience, visual language, states, transitions, accessibility, and lifecycle behavior. It intentionally leaves class/file structure to the Developer, subject to the task’s technical boundaries.

## Inputs inspected

### Authoritative requirements

- `.ai/tasks/TASK-008.md`

### Rewrite implementation and theme

- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/MapCard.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/NearbyCityCard.kt`
- `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- `app/src/main/java/kniezrec/com/flightinfo/course/ForegroundCourseObservationCoordinator.kt`
- `app/src/main/java/kniezrec/com/flightinfo/flight/FlightParametersState.kt`
- `app/src/main/java/kniezrec/com/flightinfo/nearby/AndroidNearbyCityRepository.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/permission/PermissionColors.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/theme/Theme.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/theme/Type.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/assets/databases/cities_info.db`
- `app/src/main/assets/osmdroid.zip`

### Legacy behavior and visual references

- `app/src/main/java/kniezrec/com/flightinfo/cards/route/RouteCardView.kt`
- `app/src/main/java/kniezrec/com/flightinfo/cards/route/RouteCardViewPresenter.kt`
- `app/src/main/java/kniezrec/com/flightinfo/cards/route/FindCityActivity.kt`
- `app/src/main/java/kniezrec/com/flightinfo/cards/route/FindCityPresenter.kt`
- `app/src/main/java/kniezrec/com/flightinfo/cards/map/MapCardViewPresenter.kt`
- `app/src/main/res/layout/route_card_layout.xml`
- `app/src/main/res/layout/activity_find_city.xml`
- `app/src/main/res/layout/disambiguation_item.xml`
- `app/src/main/res/drawable/take_off_icon.xml`
- `app/src/main/res/drawable/landing_icon.xml`
- `app/src/main/res/drawable/delete_icon.xml`
- `app/src/main/res/drawable/city_on_map_icon.xml`
- `app/src/main/res/drawable/ic_city_found_marker.xml`
- `/home/ai-dev/smart-flight/app/src/main/res/values/colors.xml`
- `/home/ai-dev/smart-flight/app/src/main/res/values/dimens.xml`
- `/home/ai-dev/smart-flight/promo/promo.png` (1024×500 RGBA visual reference)

The promo image was located and its metadata inspected; image preview was unavailable in this environment. Exact behavior and resource inspection therefore came from the layouts and source files above, as directed by TASK-008.

## Behavior classification

### Observed legacy behavior

- The route card has departure and destination endpoints, initially represented by take-off and landing actions.
- Once both endpoints are selected it reveals distance between cities, distance to destination, estimated arrival, and a delete/reset action.
- Tapping an endpoint opens a city finder. A previously selected city is available when editing.
- Text search can return zero, one, or many cities; many results require explicit disambiguation.
- A map long press can resolve a nearby city. Confirmation returns the chosen city to the route card.
- Route changes update a straight map line and endpoint markers.
- Saved endpoint IDs restore the route after recreation.

### Required behavior from TASK-008

- Use the bundled offline database and the existing map/database boundaries.
- Use one shared newest valid foreground fix for remaining distance and ETA; invalidate live values when that session stops.
- Preserve valid endpoint IDs across recreation/process restart, safely discard invalid IDs, and keep database failures local to route/picker UI.
- Keep the existing map instance, live aircraft marker, offline-only configuration, and user viewport intact while route overlays change.
- Use one-decimal, locale-aware kilometres; show an explicit unavailable state for missing or unusable live data.
- Provide explicit confirm/cancel, clear-per-endpoint, and clear-route actions.

### Design decisions

- Use a Compose full-screen picker destination/sheet rather than an activity-style screen. A full-screen route keeps the search field, result list, map, preview, and actions usable at large text sizes and in landscape; it also makes Cancel/back semantics unambiguous.
- Treat the picker selection as draft state. Search results and map long-presses never mutate the saved endpoint until the user taps Confirm.
- Use separate labeled endpoint rows instead of relying on a large icon and city-name truncation. This improves RTL, TalkBack, and 200% font-scale behavior.
- Keep the route card compact when incomplete and information-dense only when complete. This avoids the legacy layout’s large empty icon area while retaining its quick endpoint affordances.
- Render route details as stacked label/value rows rather than inline label/value pairs. Values can wrap, and labels remain understandable when the device is narrow or using RTL.
- Use a visual distinction for route markers and aircraft marker, plus a text summary on the map card. Color is supplementary, never the only distinction.

## Dashboard placement and layout

The active dashboard remains a vertically scrollable column with a 12 dp horizontal gutter and a 600 dp maximum card width. Order is:

1. GNSS
2. Flight parameters
3. Course
4. Horizon
5. Nearby city
6. Route
7. Map

Each card uses the existing rounded purple surface and 12 dp inter-card spacing. Route is one card instance, not two endpoint cards. The card must be composed only in the authorized active dashboard branch.

### Incomplete route card

- Card title: `Route` at the leading edge, with the same title/body contrast used by neighboring cards.
- Below the title, show two full-width endpoint action rows in logical order: `Departure` then `Destination`.
- Each row is at least 48 dp high, has a leading take-off/landing concept icon, a visible text label, and a trailing action affordance.
- Empty rows use a clear action phrase such as `Choose departure city` and `Choose destination city`; the icon must not carry the meaning alone.
- If one endpoint exists, show its city name in that row and retain an obvious `Edit` action. The other row remains a choose action.
- A clear icon/action is available for each populated endpoint. A route-level `Clear route` action is available once any endpoint is selected; it may be a text button beneath the rows or an overflow-equivalent labeled action, but must not be hidden behind a long press.
- Do not show route metric rows until both endpoint records are valid.

### Complete route card

- Keep the two endpoint rows at the top, with departure and destination names allowed to wrap.
- Show a subtle route relationship cue between rows (for example, a vertical connector or divider); it is decorative and does not replace the labels.
- Show three stacked detail rows:
  - `Distance between cities` — fixed straight-line distance, one decimal km.
  - `Distance to destination` — current valid fix to destination, or an explicit `Waiting for current position`/`Unavailable` value.
  - `Estimated arrival` — destination-local arrival time and remaining duration when current fix and finite positive speed are available; otherwise `Waiting for usable speed` or equivalent truthful state.
- Keep the fixed route distance visible even when live values are unavailable.
- The route card’s accessibility summary should announce departure, destination, fixed distance, and the current availability of live details as one coherent region after meaningful changes, politely rather than interruptively.

## City picker

Open the picker when either endpoint row is tapped. Pass the endpoint role and, when editing, the current city as draft context. The picker must not start aircraft tracking or another foreground location observer.

### Picker structure

From top to bottom in portrait:

1. Top app bar with endpoint-specific title (`Choose departure` or `Choose destination`) and a clearly labeled Back/Cancel action.
2. Prominent search field labeled `City name`, with IME Search and a visible Search action. Trim whitespace and match case-insensitively.
3. Supporting instruction: search the offline city database or long-press a point on the map.
4. Results/selection region, occupying the flexible middle area.
5. Offline map preview with long-press affordance/instruction. It may be smaller than the results list but must remain usable by touch.
6. Selected-city preview and a bottom action row with Cancel and Confirm. Confirm is disabled until a valid city draft exists.

The map preview uses the existing offline archive/configuration and has no live aircraft marker or location session. A valid long press resolves the nearest city from the same city dataset, updates the draft preview, and exposes Confirm. A long press with invalid/out-of-range coordinates is ignored and produces a readable, non-blocking message such as `No city found at this location`.

### Search and selection states

- Initial: empty search field, instruction visible, no draft selection unless editing an existing endpoint; editing shows the existing city as the current preview and permits replacement.
- Loading: show progress and `Searching offline city data`; disable repeated search submission while the current request is active.
- Zero results: show `No cities found` and a suggestion to check spelling or try a map long press; retain the query.
- One result: show the city as a selectable result and make it the draft selection only when the user selects it, not merely because it is returned.
- Multiple results: show a scrollable list of clearly selectable city rows. Each row exposes the city name and enough distinguishing information available from the record (for example, country/region if present). Never silently choose the first result.
- Draft selected: show selected city name/details, map marker, and enabled Confirm. A later search or map long press replaces the draft only after explicit user selection.
- Database error: show a localized error in the picker with Retry. Keep the existing endpoint unchanged and keep Cancel available.
- Cancel/back: discard the entire draft, query result, and map marker; return to the card unchanged.
- Confirm: persist the selected endpoint ID atomically, return to the card, and update route/map state.

Search result rows and the selected preview are minimum 48 dp touch targets. Keyboard Search submits the same action as the Search button. The search field should retain focus/keyboard behavior naturally on entry, but the user must still be able to dismiss the keyboard and reach results/actions.

## Visual language

- Background: retain the current dashboard dark-purple background.
- Card surface: retain `cardPurple`/legacy `purple_main` approximately `#5B5999`; use the current Material 3 color scheme only where it preserves readable contrast with the established dashboard.
- Primary text: light lavender approximately `#D9D9ED`.
- Action emphasis: cyan approximately `actionCyan`/legacy `cyan_main`; verify contrast for text and icons and provide a pressed/focused state.
- Secondary surface/divider: use the existing darker purple family with sufficient separation; avoid introducing a bright white card that breaks the dashboard composition.
- Shape: rounded corners consistent with neighboring cards (legacy reference is 10 dp); use existing dashboard conventions rather than a new route-specific shape language.
- Spacing: 12 dp outer/card rhythm, 16–24 dp internal content padding as needed, 8–12 dp between label/value groups, and 48 dp minimum interactive height.
- Typography: use resource-backed Material typography. Card title follows the dashboard title style; endpoint names and metric values use a readable body/title size with normal wrapping. Do not hard-code visible strings or use fixed single-line widths.
- Icons: use take-off and landing concepts for endpoint roles; use standard accessible edit/clear/delete/search/back/confirm affordances. Icon color reinforces role but text labels carry the meaning.

Route polyline should be visually legible over the offline tiles and visually distinct from the aircraft marker. Endpoint markers should differ from the aircraft shape and from each other by role where practical; provide the same distinction in the companion text summary. Do not recenter, animate the viewport, or cover existing map controls when overlays update.

## Route/map state behavior

The user-visible state is derived from two endpoint records, the route calculation, and the current foreground session:

| State | Route card | Map |
| --- | --- | --- |
| No endpoints | Two choose actions; no metrics | Live aircraft marker only |
| One valid endpoint | One city row plus one choose action; no metrics | Live marker only; no route overlay |
| Two endpoints, no current fix | Names and fixed distance; live rows explicitly waiting/unavailable | Straight polyline and both endpoint markers |
| Two endpoints, valid fix and positive finite speed | All metrics, including destination-local ETA and duration | Same route overlays plus live aircraft marker |
| Endpoint cleared/invalid | Return to incomplete state; clear only affected data | Remove polyline and both route markers, retain aircraft marker |
| Session stopped/permission lost | Retain valid saved endpoint selection; clear remaining distance and ETA | Stop route-related updates; retain only overlays appropriate to the authorized map lifecycle |
| Map not ready | Card state remains authoritative | Apply the latest complete route when map becomes ready |
| Database failure | Picker/card error with retry | Existing map and other dashboard cards remain usable |

Only the newest valid fix from the current foreground session may update remaining distance and ETA. Invalid coordinates, non-finite values, out-of-range values, stale callbacks, stopped sessions, replaced sessions, and permission loss do not overwrite current route details. On the next valid fix, live values repopulate.

The polyline is exactly one straight geographic segment between the selected city coordinates. Overlay add/update/remove happens through the existing map interop boundary and does not recreate the MapView, alter offline-only configuration, alter the live marker, or reset the user’s viewport.

## Persistence and failure handling

- Persist only endpoint roles and city IDs in app-private local preferences.
- Restore IDs only when the authorized dashboard is being created. Resolve records asynchronously through the existing city-data boundary.
- If an ID no longer resolves or its record has invalid coordinates/time-zone data, discard only that endpoint and show its choose action; never crash or fabricate a city.
- Clearing an endpoint removes its stored ID before reporting the route as incomplete. Clear route removes both IDs durably.
- Search, nearest-city lookup, restore, formatting, or time-zone failures are represented in route/picker state and do not transition GNSS, flight, course, horizon, nearby-city, or map availability into an error state.
- Retry repeats only the failed database operation and preserves a still-valid draft/current endpoint where possible.

## Responsive, orientation, and large-text behavior

### Portrait

Use the single-column card described above. The picker gives the result list and map flexible vertical space; bottom actions remain reachable without clipping or relying on a fixed-height legacy layout.

### Landscape and wider windows

At widths above the dashboard’s 600 dp content limit, retain the centered max-width card. Inside the picker, a two-pane arrangement is allowed: results/selection on one side and map on the other. It must preserve the same reading order, labels, and actions as portrait and must not create a second map session. On narrow landscape windows, fall back to the portrait vertical arrangement.

### Font scale and RTL

All endpoint names, detail values, errors, and actions wrap naturally at 200% font scale. Avoid fixed-width inline pairs, clipped single-line city names, or icon-only actions. Use logical start/end alignment and role labels (`Departure`, `Destination`) so the layout mirrors correctly in RTL. Date/time and numeric formatting remain locale-aware while the route’s semantic order remains clear.

## Accessibility

- Every endpoint action announces its role, current value/state, and action, for example `Departure, not selected, choose city` or `Destination, Berlin, edit city`.
- Every detail row exposes a label/value pair to TalkBack; unavailable values are spoken as unavailable/waiting rather than a dash alone.
- Confirm, Cancel, Retry, clear endpoint, and clear route have resource-backed labels and hints where the action is not self-evident.
- Search results are individually focusable and announce their city identity and distinguishing information.
- Loading and error changes use polite live-region announcements. Do not announce every position/ETA update as an interruptive event.
- Focus order follows title, search, result selection, map instruction, preview, Cancel, Confirm. Back/cancel never commits a draft.
- Touch targets are at least 48 dp. Focus/pressed/disabled states must be visible without relying only on color.
- The map card retains its existing controls and adds a companion description such as `Route from A to B`; route markers remain supplementary to the card’s text.

## Intentional deviations from the legacy application

- Legacy endpoint city names are single-line and use long press to clear. The rewrite provides visible per-endpoint clear/edit actions and wrapping names, improving discoverability, RTL, and large-text support.
- Legacy search can immediately display a single found city and uses a service-backed picker. The rewrite keeps selection as draft until explicit Confirm and uses the existing asynchronous database boundary, preventing accidental changes and legacy service coupling.
- Legacy picker layout is a fixed ConstraintLayout with a map/results split and a dialog for no results. The rewrite uses responsive Compose content with inline zero-result/error states and stable Cancel/Confirm actions.
- Legacy route detail rows are hidden/shown through animated constraints. The rewrite uses stable semantics and state-aware content, allowing the card to resize without hiding meaning from accessibility services.
- Legacy route ETA behavior can derive from service-provided location values. The rewrite explicitly shows unavailable/waiting states for absent, stale, invalid, or non-positive speed and clears live values on session stop, as required by TASK-008.
- Legacy map changes are broadcast-driven and may use service architecture. The rewrite updates the existing MapView boundary directly from shared route state, preserving viewport and the live marker.

## Developer verification checklist

Verify the design through focused unit/UI and map tests for:

- search trimming/case normalization and zero/one/multiple-result disambiguation;
- draft selection, explicit confirm/cancel, edit, per-endpoint clear, and clear-route behavior;
- nearest-city long press, invalid coordinates, and localized retry/error states;
- saved valid/invalid IDs and durable clearing;
- fixed distance, remaining distance, positive-speed ETA, destination time zone, locale formatting, identical endpoints, and unavailable live states;
- newest-session-fix acceptance and stale/invalid/session-stop/permission-loss invalidation;
- route card placement, semantics, 48 dp actions, 200% text scale, RTL wrapping, and picker keyboard/back behavior;
- map overlay add/update/remove and restoration after map readiness without a second map instance, viewport reset, network tiles, or disruption to the aircraft marker.

## Unresolved questions

None. TASK-008 specifies the metric convention, straight-line route, picker behavior, lifecycle rules, and offline boundaries sufficiently for implementation.
