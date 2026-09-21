# TASK-006 — Add offline nearby-city card

## Status

READY_FOR_DESIGN

## Goal

Add a foreground-only `Nearby city` card after the existing Horizon card.  From each current GPS position, it identifies the nearest city in Smart Flight's bundled offline city database and shows the city name, country, distance, and that city's local time without a network request.

## Context

TASK-001 through TASK-005 establish the permission-gated, foreground flight dashboard: GNSS status, GPS flight parameters, compass course, and horizon.  The next legacy dashboard surface is the location card.  Its useful user-visible purpose is not to repeat latitude/longitude; it gives a pilot geographic context for the live position by finding the closest entry in the app's packaged city dataset.

The rewrite has intentionally kept metric units fixed and has no settings screen.  This increment follows that established product scope: nearby-city distance is shown in kilometres, with no unit preference.  It must reuse the legacy dataset rather than substituting an online geocoder or a hand-maintained sample list.  Route selection, city search, and maps remain later, separate features.

## Original Application

`app/src/main/java/kniezrec/com/flightinfo/cards/adapter/CardViewContainer.kt` inserts `LocationCardView` after `FlightParametersCardView` in the permission-gated dashboard.  `cards/gps/LocationCardView.kt`, `LocationCardViewPresenter.kt`, and `res/layout/location_card_layout.xml` render four label/value rows: closest city, country, distance to the city, and time.

For each legacy location callback, `LocationCardViewPresenter` asks `FindCityService` to locate the nearest record.  `services/city/FindCityHelper.kt` builds an R-tree over the bundled city data and returns the nearest city; it is not a network geocoder.  `db/City.kt` supplies name, country, latitude, longitude, time-zone ID, and GMT offset.  `avionic/calculators/DistanceCalculator.kt` uses geographic distance and formats the legacy default in kilometres; `TimeCalculator.kt` formats time in the city's stored time zone.

The reusable immutable source data is `app/src/main/assets/databases/cities_info.db` (about 2.9 MB).  Its legacy reader is `db/CitiesDataSource.kt` and `CitiesDatabaseHelper.kt`, which rely on the obsolete `SQLiteAssetHelper` library.  The legacy spatial-index libraries are `app/libs/jsi-1.0.0.jar` and `app/libs/trove4j-2.0.2.jar`; do not import them.  The legacy application's `osmdroid` dependency and `assets/osmdroid.zip` support the later map card, not this task.

`promo/promo.png` (1024 × 500) was inspected as an available visual reference, but its contents could not be rendered in this sandbox.  The inspectable location-card layout and shared dimensions/colors are the detailed visual reference.

## Current Application

`MainActivity.kt` renders the fine-location foreground dashboard and owns lifecycle start/stop.  `GnssStatusScreen.kt` provides its shared purple shell, scroll column, 600 dp maximum card width, and currently renders GNSS status, Flight parameters, Course, then Horizon.  `FlightParametersCard.kt` establishes the existing label/value-row treatment.

`flight/AndroidFlightLocationPlatform.kt` already converts each foreground `LocationManager.GPS_PROVIDER` update to `FlightLocationFix`, including speed, altitude, elapsed-realtime timestamp, and optional bearing.  `FlightParametersController.kt` owns the single listener session and clears readings when observation stops; `ForegroundCourseObservationCoordinator.kt` begins that controller alongside Course.  There is no city asset, city lookup model, nearest-city query, or location card in the rewrite.

## Functional Requirements

- Show exactly one `Nearby city` card after the existing Horizon card only while precise/fine location is granted and the dashboard is foregrounded.  Approximate-only, denied, or revoked fine permission must show TASK-001 onboarding and must leave no location or city-lookup work active.
- At the beginning of every eligible foreground observation session, show `Waiting for GPS position…` with no prior-session city, country, distance, or time values.
- Reuse the same current foreground GPS fixes observed for Flight parameters; do not register a second `LocationManager` listener.  Do not use a last-known location.
- A usable lookup position has finite latitude in -90..90 and finite longitude in -180..180.  Ignore malformed positions.  Until a usable current-session fix is received, retain the waiting state.
- For a usable current-session fix, find the geographically nearest record in the bundled legacy `cities_info.db` dataset without network access.  When a result is available, show these rows in order: `Closest city`, `Country`, `Distance to the city`, and `Time`.
- Display the matched city name and country exactly as supplied by the data.  Display geographic distance from the current fix to that city in fixed metric units as a locale-formatted value rounded to one decimal place with `km`.
- Display the matched city's current local clock time using its stored IANA time-zone identifier and locale-appropriate short-time formatting.  Include the UTC offset in an accessible, localized form so the value has the legacy card's time-zone context.  Time is refreshed whenever a newer accepted fix produces a result; this task does not require a continuously ticking clock.
- Only the newest accepted fix in the active observation session may replace the card.  Background queries/results from a stopped, retried, paused, or superseded session must be discarded.  A slower older query must not overwrite a newer location result.
- Load/open/query the city data off the main thread.  The UI must remain responsive while the data is first prepared and while nearest-city lookup runs.
- If the bundled data cannot be opened, is empty, has no valid nearest result, or contains an invalid time-zone value for the selected record, show `Nearby city unavailable` with a concise explanation and an accessible `Try again` action.  Retry clears stale values, reopens/reloads the data as needed, and waits for or reruns the latest valid current-session position; it must not crash.
- Stop/cancel or invalidate outstanding lookup work and clear visible city values when the foreground location session stops, fine permission is lost, the activity pauses/destroys, GNSS/location availability becomes ineligible, or a new observation session starts.  Do not persist a previous city or position.
- Preserve all TASK-001 through TASK-005 behavior, including their GNSS/location failure handling, lifecycle cleanup, course bearing delivery, and horizon observation.  A city-data failure is local to this card and must not change GNSS, Flight parameters, Course, or Horizon states.

## Technical Requirements

- Copy the existing legacy database unchanged into the rewrite's app assets as `databases/cities_info.db`; it is source data for this feature, not an online cache.  Do not modify the original project.
- Use Android platform SQLite APIs (copying the read-only asset to an app-private readable location when required) or another already-available platform mechanism.  Do not add `SQLiteAssetHelper`, JSI, trove4j, Room, a geocoding SDK, an R-tree library, or a network dependency solely for this card.
- A simple deterministic nearest-record scan/query over the immutable dataset is acceptable if it runs off the UI thread and remains cancellation/session-safe.  An optimization may be added only if it preserves nearest-city results and does not add unnecessary dependencies.
- Keep framework/database I/O behind a narrow, testable boundary.  Use small immutable location/city/result values in domain and UI state; do not leak `Location`, cursors, or SQLite objects into Compose.
- Extend the existing single location-fix delivery path (for example, a fan-out/coordinator adjacent to `FlightParametersController`) instead of registering a duplicate GPS listener or broadening unrelated architecture.
- Use a robust geographic-distance calculation or Android's platform equivalent; guard all non-finite coordinates/results.  Use `java.time`/Android platform time-zone APIs supported by minSdk 31, not the legacy `TimeCalculator`'s manual offset arithmetic.
- Add all visible, action, content-description, state, and error text to Android resources.  Format numbers and clock time locale-aware; do not hard-code English values or use default-locale `String.format` shortcuts.
- No additional runtime permission, foreground/background service, notification, settings screen, persistence, map, route, city search, manual city picker, geocoding request, or map dependency is in scope.  Preserve edge-to-edge behavior and app identity.

## UI Requirements

- Retain the existing single-column purple dashboard and place the card immediately below Horizon using the established 12 dp spacing, `#5B5999` rounded card surface, logical start/end padding, and 600 dp maximum width.
- The available card has the title `Nearby city` followed by four clear left-aligned label/value rows in the order required above.  It should visually follow the existing Flight parameters card rather than imitate a map, airport list, or route selector.
- In waiting, lookup/preparation, and unavailable states, use a content-driven, centered state layout consistent with GNSS and Horizon.  `Looking up nearby city…` may be shown after a valid position is received while lookup is pending; it must not expose partial/stale rows.
- Available row semantics must expose each label and its current value together to TalkBack.  The state message/error must be understandable without color; `Try again` must have a visible label, button semantics, a resource-backed retry hint, and a 48 dp minimum target.
- Support portrait, landscape/expanded windows, RTL, display cutouts/gesture insets, keyboard/switch navigation, TalkBack, magnification, and 200% font scale.  Values may wrap and the card/page may grow; do not clip, overlap, truncate, or create a nested scrolling list.

## Acceptance Criteria

- [ ] With fine location granted and the eligible foreground dashboard visible, exactly one `Nearby city` card appears after Horizon; onboarding or a non-foreground dashboard has no city card or active lookup work.
- [ ] A new/restarted session initially shows `Waiting for GPS position…` and never shows a city result from an earlier session.
- [ ] A controlled valid current GPS position produces the nearest city from the bundled legacy city dataset and displays its name, country, one-decimal locale-formatted kilometre distance, and locale-formatted local time with UTC-offset context.
- [ ] The card works with no network connectivity and performs no geocoding/network request.
- [ ] A newer valid position wins over an older delayed query result; stale callbacks from prior sessions, pause, retry, or destruction cannot alter the UI.
- [ ] Invalid/non-finite/out-of-range positions do not crash, fabricate a city, or replace a valid current display with false data.
- [ ] Dataset open/query/empty-result/invalid-time-zone failure produces `Nearby city unavailable`; `Try again` safely clears stale UI and recovers when a valid dataset/current position is available.
- [ ] The existing location listener remains singular: city lookup reuses the Flight parameters path, and lifecycle/retry/permission changes leave no duplicate listener, leaked query, or persisted location/city value.
- [ ] GNSS status, Flight parameters, Course, and Horizon retain their existing behavior when nearby-city lookup succeeds or fails.
- [ ] Focused automated tests cover nearest-city selection, metric distance formatting, time-zone/offset formatting, malformed data/positions, newest-result/session invalidation, retry, and Compose state/content/action semantics.
- [ ] No map, route, airport/city search, unit preference, online geocoder, new runtime permission, background collection, foreground service, notification, or unrelated dependency is added.

## Implementation Plan

1. Add the immutable legacy city database asset to the rewrite and implement a testable, off-main-thread read-only database access boundary with safe first-use/copy behavior.
2. Define pure nearby-city state, coordinate validation, nearest-record selection, metric distance, and city-time presentation logic; cover bad data and time-zone cases.
3. Extend the existing foreground location-fix coordination so nearby-city lookup receives the same current-session fixes as Flight parameters without a second listener, and enforce newest-result/session cancellation rules.
4. Add the `Nearby city` Compose card after Horizon, including waiting/looking-up/available/error states, localized resource copy, accessible rows, retry action, and responsive styling.
5. Add focused unit and Compose/instrumentation coverage, then run the configured checks where the Android SDK is available.

## Files / Components Likely Affected

- `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- `app/src/main/java/kniezrec/com/flightinfo/flight/FlightParametersController.kt`, `AndroidFlightLocationPlatform.kt`, and/or a focused foreground-location coordinator
- New focused nearby-city data/state/controller/platform files under `app/src/main/java/kniezrec/com/flightinfo/`
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt` and a new adjacent nearby-city composable
- `app/src/main/res/values/strings.xml`
- New `app/src/main/assets/databases/cities_info.db`, copied unchanged from the original project
- Focused tests under `app/src/test/` and `app/src/androidTest/`

## Reusable Existing Libraries / Components

- Rewrite: Compose Material 3, existing dashboard/card visual primitives, `FlightParametersController`, `FlightLocationFix`, `AndroidFlightLocationPlatform`, foreground lifecycle in `MainActivity`, Android `SQLiteDatabase`, `Location` geographic-distance APIs, and `java.time` on minSdk 31.
- Original behavior/data references: `LocationCardView`, `LocationCardViewPresenter`, `location_card_layout.xml`, `FindCityService`, `FindCityHelper`, `City`, `CitiesDataSource`, `CitiesDatabaseHelper`, `DistanceCalculator`, `TimeCalculator`, and `assets/databases/cities_info.db`.
- Not reusable for this task: legacy `SQLiteAssetHelper`, `jsi-1.0.0.jar`, `trove4j-2.0.2.jar`, osmdroid, `osmdroid.zip`, MPAndroidChart, and legacy bound/background services.

## Risks and Edge Cases

- The city database is a relatively large static asset; first-use copy/open must not block rendering or cause repeated copies after recreation.  Treat a partial/corrupt copied database as recoverable through a safe replacement/retry strategy.
- Geographic proximity near the poles, antimeridian, or duplicate city coordinates must use a reliable distance calculation and deterministic tie behavior.  Exact tie selection is not user-visible but must be stable in tests.
- City records can contain obsolete/malformed time-zone information.  Do not silently present the device time as city time; render the defined unavailable state when the chosen record cannot be interpreted.
- GPS updates can be more frequent than database queries.  Coalesce or cancel safely so the newest accepted result wins and the app does not accumulate an unbounded queue.
- The legacy card updated only after its asynchronous city service replied.  This task preserves that asynchronous truthfulness but deliberately does not migrate its service binding, legacy unit preferences, R-tree/JAR dependencies, or manual-offset calculation.
- The legacy map, route, city search, and user-selected departure/destination flows are explicitly outside this task and require separate product specifications.

## Open Questions

None.  The legacy code and immutable city asset establish the intended nearest-city behavior, while fixed metric distance and platform time-zone formatting align it with the rewrite's already-specified scope and minSdk.
