# TASK-006 — Offline nearby-city card design specification

## Scope and product intent

Add exactly one foreground-only `Nearby city` card directly after `Horizon` on the precise-location dashboard. From the current foreground GPS fix, it gives the pilot useful geographic context: the nearest city in the bundled immutable legacy database, its country, the geographic distance in kilometres, and the city's current local time.

This is an offline informational card, not a map, address/geocoding result, search result, route waypoint, airport list, or selectable city. It has no interaction except recovery from a local city-data failure. TASK-001 onboarding remains the entire screen whenever fine permission is absent, approximate-only, denied, or revoked; in those states the card and all city preparation/query work are absent.

## Inputs, requirements, and deliberate decisions

### Observed legacy behavior

- The permission-gated legacy dashboard inserts `LocationCardView` after `FlightParametersCardView`. Its layout is a 160 dp-minimum purple surface containing four left-aligned label/value rows: city, country, distance, and time. Values begin as a dash.
- A legacy location callback is sent to a bound `FindCityService`, which builds an R-tree from the bundled `cities_info.db` and returns the nearest `City`. The presenter then reads the last obtained location to format the distance and uses the stored zone/offset to format time.
- The legacy database contains city name, country, latitude, longitude, IANA-style time-zone ID, and GMT-offset fields. Its R-tree/JAR/service and manual-offset formatter are implementation details, not visual requirements.

### Required behavior from TASK-006

- One card follows Horizon only while the fine-location dashboard is eligible and foregrounded. Each eligible observation session starts with `Waiting for GPS position…`, no retained city values, and no last-known location.
- A usable current fix has finite, in-range latitude/longitude. It is delivered through the existing singular foreground GPS path. A valid fix causes an offline nearest-city lookup; only the newest accepted fix/result in the active session may replace the UI.
- The available state lists exactly `Closest city`, `Country`, `Distance to the city`, and `Time`, in that order. City/country are dataset values; distance is locale-formatted to one decimal place in `km`; time is locale short time in the city's valid stored IANA zone with accessible localized UTC-offset context.
- Opening/copying/querying data happens off the main thread. Dataset/open/empty/nearest/time-zone failures are local to the card and show an unavailable state plus an accessible retry. They do not alter GNSS, Flight parameters, Course, or Horizon.

### Design decisions and intentional deviations

- Use the rewrite's titled, padded, semantically paired label/value card rather than copying the legacy unheaded grid. A title makes the card's purpose clear and a waiting state avoids the ambiguous legacy `—` placeholders.
- Show a centered `Looking up nearby city…` state after a valid fix while the city asset is being prepared or queried. There is no spinner requirement: concise truthful text retains the dashboard's calm instrumentation character and avoids an unnecessary animated indicator.
- Replace all rows with a state layout during waiting, lookup, and error; never show partly updated, previous-session, or stale rows. This is intentionally safer than the legacy asynchronous presentation.
- Keep the legacy four-item information hierarchy, card colour, radius, and dashboard placement. Do not reproduce its fixed ConstraintLayout columns or legacy manual time-offset arithmetic; responsive Compose rows and platform zone formatting improve accessibility and correctness.
- The card does not display source coordinates, a city pin/icon, a map, a compass direction, a time-zone ID, or a continuously ticking clock. These are not required and would compete with the geographic-context purpose.

## Shared visual system and screen structure

Continue the current dashboard unchanged: `#484685` page background, edge-to-edge safe insets, centered 56 dp-minimum `Smart Flight` header (`#D9D9ED`, 20 sp medium), and a single outer vertically scrolling column with 12 dp page gutters and 600 dp maximum card width.

`Nearby city` is the only new surface. It follows the existing Horizon card immediately, using Horizon's 12 dp bottom spacing; it should itself retain 12 dp bottom spacing in the outer column. It has no nested scrolling region.

- Card surface: `#5B5999`, 10 dp rounded corners, 4 dp elevation, `fillMaxWidth` within the 600 dp column.
- Padding: 24 dp logical start/end and 20 dp vertical. Content expands naturally; 160 dp is a minimum for centered non-available states, never a clipping height.
- Title: `#D9D9ED`, 22 sp / 28 sp, medium. Labels, values, and state body: `#D9D9ED`, 18 sp / 25 sp. Values use medium weight to establish the same hierarchy as Flight parameters.
- Recovery action: existing cyan `#6CF0FF` text-button treatment, including ripple/pressed feedback and a 2 dp cyan, 4 dp-rounded focus outline. Minimum touch/focus target is 48 × 48 dp.

```text
Smart Flight shell (one outer page scroll)
├── existing GNSS status card
├── existing Flight parameters card
├── existing Course card
├── existing Horizon card
└── Nearby city card
    ├── waiting: title + waiting message
    ├── preparing / lookup: title + lookup message
    ├── available: title + four label/value rows
    └── unavailable: title + concise explanation + Try again
```

No app-bar action, icon-only control, map affordance, selection control, city search, or independent location/GNSS indicator is added.

## State designs

### Waiting for a current GPS position

- On initial foreground eligibility and every session reset/restart, center the title `Nearby city` over `Waiting for GPS position…`, separated by 12 dp, vertically within the 160 dp-minimum card.
- Render no data values, rows, old state, progress icon, retry action, or coordinates. Invalid, non-finite, or out-of-range callbacks leave this state unchanged.
- This visible state is deliberately identical to a currently valid session that has not yet yielded a usable coordinate; it must not imply a problem or claim a city from last known location.

### Preparing or looking up the nearest city

- Immediately after an accepted valid current-session position, replace waiting content with centered `Nearby city` and `Looking up nearby city…`, using the same 12 dp title/body rhythm and minimum height.
- This covers first asset copy/open/preparation and every pending newest lookup. It has no visible partial row, retained city, indeterminate numeric value, loading percentage, or cancellation action.
- If newer accepted coordinates arrive while work is pending, keep this state until the newest result is ready. The visual has no need to announce the individual coordinate changes.

### Available nearby-city result

- Use a left-aligned `Nearby city` title. Leave 16 dp before the row group, matching Flight parameters.
- Render four full-width rows, in this exact order: `Closest city`, `Country`, `Distance to the city`, `Time`. Between rows, use the existing compact 4 dp rhythm; every row has a 48 dp minimum height.
- Each row uses logical start/end alignment. The label occupies available start-side space; its value is visually end-aligned and medium weight. Label/value are one semantic unit, not separate TalkBack stops.
- Preserve city name and country exactly as supplied by the data. Do not uppercase, title-case, transliterate, add country codes, ellipsize, or substitute an English local name.
- Distance is a localized one-decimal number and resource-backed unit template, e.g. `12.3 km`. It is geographic distance from the accepted fix to the selected city, not route distance or ETA. The format does not expose metres, miles, raw precision, or an icon.
- Time is a locale-appropriate short clock time for the selected city. The visible value may append concise localized UTC offset context in parentheses when it remains readable, for example `10:42 (UTC+02:00)`. At minimum its merged spoken value must include the localized UTC offset, for example `Time, 10:42, UTC plus 2 hours`; it must not claim device-local time or show the raw zone ID. Use a resource-backed template for the visible and spoken context.
- A result replaces the entire four-row group atomically. A polite one-time `Nearby city available` announcement may be sent when a session changes from waiting/looking up to its first available result; routine newer-fix replacements remain non-live to avoid GPS-update chatter.

Illustrative content only:

```text
Nearby city

Closest city                                  Gdańsk
Country                                       Poland
Distance to the city                          12.3 km
Time                              10:42 (UTC+02:00)
```

### Unavailable city data

- Center `Nearby city unavailable` and a concise resource-backed explanation. Use a generic explanation that remains accurate for data-copy/open/query/empty-nearest/invalid-time-zone failures, such as `City data could not be loaded.`, unless implementation can reliably distinguish and localize an equally concise cause. Do not expose SQL details, paths, time-zone IDs, exception messages, or a stale result.
- Place `Try again` 12 dp below the body in the established cyan text-action style. The card remains content-driven, with no spinner or waiting rows behind the error.
- Tapping retry immediately clears the unavailable message and any stale cache/presentation values. If a latest valid active-session position exists, enter `Looking up nearby city…` and re-open/reload/query it; otherwise return to `Waiting for GPS position…`. The action is safely ignored/deferred if eligibility has ended. It cannot start a new location listener or request a permission.
- Announce entry to error politely once using its title; never repeatedly announce failures on recomposition or repeated rejected callbacks.

## Interaction, state ownership, and motion handoff

- Compose receives immutable presentation state only: `WaitingForPosition`, `LookingUp`, `Available(cityName, country, formattedDistance, formattedTime, spokenTime)`, and `Unavailable`. It receives no `Location`, SQLite cursor/database, exception, raw city row, or time-zone object.
- The foreground-location coordinator/controller owns eligibility, one monotonically distinct session token, latest usable current-session coordinate, and work invalidation. The city boundary owns safe off-main-thread immutable asset copy/open/query; a pure nearest-city/result formatter owns coordinate validation, geographic distance, zone validation, and localized presentation inputs. This is a UI/design handoff, not a requirement for a particular class layout.
- Start, pause, destruction, fine-permission change, GNSS/location eligibility loss, retry replacement, and every new observation session synchronously clear the city UI to waiting (or remove the dashboard entirely) and invalidate/cancel outstanding work. Results carrying an old session or older accepted-fix identity are discarded before they can change state.
- Valid fixes may be coalesced/cancelled so only the newest query matters. First-use data preparation and lookup must remain off main; a slow result must never flash before the result for a newer fix.
- Static content may use the dashboard's subtle 150–200 ms crossfade/size transition if it honors reduced-motion preferences. Do not animate values continuously, add a spinner, or delay a truthful state reset. Available row replacement should be atomic.
- The nearby-city feature consumes the exact existing `FlightLocationFix` delivery fan-out used by Flight parameters and Course bearing. Extend that fan-out/coordination only; do not register another `LocationManager` listener, use a last-known location, add a service, or change the behavior of the other cards.

## Accessibility and semantics

- Focus order: title then state body then retry in non-available states; title then the four merged rows in required order for an available result. The card container is not an additional focusable control.
- A row merges its descendants and supplies localized label-plus-current-value speech: `Closest city, Gdańsk`; `Country, Poland`; `Distance to the city, 12.3 kilometres`; `Time, 10:42, UTC plus 2 hours`. The visible abbreviated `km` and UTC form must have resource-backed accessible expansions rather than relying on punctuation/symbol pronunciation.
- The static waiting/looking-up/error message is understandable without colour or motion. Use one polite live region for meaningful state transitions: first availability and entry to unavailable. Do not announce routine fixes, repeated lookup starts, every city/time refresh, or ordinary lifecycle waiting.
- `Try again` has a visible text label, button role, resource-backed semantic hint such as `Reloads nearby city data and tries the latest GPS position.`, a 48 dp minimum target, visible keyboard/switch focus, and normal activation feedback. It is the only actionable child.
- Keep logical start/end layout for RTL. Time, numerical distance, signs, unit/UTC templates, and spoken formats must derive from Android resources and locale-aware formatting. Ensure a right-aligned value does not reorder label/value semantics in RTL.
- Support TalkBack, touch exploration, keyboard/switch navigation, magnification, display cutouts/gesture insets, and 200% font scale. Do not communicate availability/failure solely through purple/cyan color, alignment, or state transition animation.

## Responsive behavior

- Portrait phones retain the one-column stack; the available card normally displays four compact 48 dp-minimum rows beneath its title.
- On narrow windows, 200% font, long city/country names, or localized time/offset text, values may wrap below their label within the same semantic row or rows may use a responsive vertical label/value arrangement. Preserve their stated order, reading order, logical alignment, and at least 48 dp touch/traversal height. Do not clip, overlap, ellipsize dataset text, horizontal-scroll the card, or create nested scroll.
- Landscape, tablets, and expanded windows retain the centered 600 dp column rather than forming a multi-column dashboard. The available width can keep label/value rows on one line when comfortably readable; outer page scroll keeps all cards reachable in short heights or when keyboard/switch UI reduces visible space.
- The 160 dp state minimum is not a maximum: long unavailable copy or a large-font retry control grows the card. Safe drawing insets remain owned by the existing screen/activity shell.

## Resource-backed copy and formatting handoff

Add all user-facing content to Android resources; names are illustrative:

- Titles/state: `Nearby city`; `Waiting for GPS position…`; `Looking up nearby city…`; `Nearby city unavailable`; concise unavailable explanation; optional `Nearby city available` announcement.
- Row labels: `Closest city`; `Country`; `Distance to the city`; `Time`.
- Action: `Try again`; retry hint `Reloads nearby city data and tries the latest GPS position.`
- Value/spoken templates: one-decimal kilometre value, spoken kilometre value, visible time-plus-UTC-offset context, and spoken time-plus-localized-offset context. Retain `UTC` as the recognized acronym but localize plus/minus and hour/minute wording through templates.

Use locale-aware number formatting with exactly one fraction digit after a finite geographic calculation. Use platform `java.time` zone APIs valid for minSdk 31 and localized short-time formatting; treat an invalid selected city's zone as unavailable, never silently fall back to the device zone. The current clock is read when a successful newest result is prepared and refreshes only upon a newer accepted fix/result; no ticking composable or timer is required.

## Design verification checklist

- The eligible foreground dashboard order is GNSS, Flight parameters, Course, Horizon, then exactly one Nearby city card. Onboarding/non-foreground/permission loss contains neither that card nor any city work.
- Every new/restarted/retried session clears old city/country/distance/time values. It visibly begins waiting until a usable current-session fix, never using last-known location.
- A valid current position transitions through lookup and then presents the geographically nearest bundled-dataset city, unchanged country/name, one-decimal locale-aware kilometres, and locale short local time with accessible UTC-offset context, with no network call.
- Malformed coordinates do not crash or replace visible truth. Newer accepted fixes win over delayed prior work; stale callbacks after pause, permission change, retry, stop, or destruction have no UI effect.
- Copy/open/query/empty-result/invalid-zone failure shows the local unavailable/retry surface while GNSS, Flight parameters, Course, and Horizon retain their own current state. Retry clears stale content, safely reopens/reloads as needed, and waits for or reruns the latest valid active-session position.
- Row semantics pair each label and value; state/action copy is resource-backed; TalkBack avoids update chatter; retry is visibly focusable and at least 48 dp.
- Portrait, landscape, expanded windows, RTL, 200% font scale, display/gesture insets, magnification, keyboard, and switch access keep all content readable and reachable with no clip, overlap, truncation, or nested scrolling.
- No map, route, airport/city search, manual picker, unit setting, online geocoder, extra permission/listener/service/notification, persistence, raw coordinate UI, legacy R-tree/JAR dependency, or manual time-offset implementation is introduced.

## Inspected references

- Authoritative task: `.ai/tasks/TASK-006.md`.
- Current rewrite: `MainActivity.kt`; `flight/FlightParametersController.kt`; `flight/AndroidFlightLocationPlatform.kt`; `course/ForegroundCourseObservationCoordinator.kt`; `ui/gnss/GnssStatusScreen.kt`; `ui/gnss/FlightParametersCard.kt`; `ui/gnss/HorizonCard.kt`; `ui/permission/PermissionColors.kt`; `ui/theme/Color.kt`, `Theme.kt`, and `Type.kt`; and `res/values/strings.xml`.
- Legacy behavior/data: `cards/adapter/CardViewContainer.kt`; `cards/gps/LocationCardView.kt`; `cards/gps/LocationCardViewPresenter.kt`; `services/city/FindCityHelper.kt`; `services/city/FindCityService.kt`; `db/City.kt`; `db/CitiesDataSource.kt`; `db/CitiesDatabaseHelper.kt`; `avionic/calculators/DistanceCalculator.kt`; and `avionic/calculators/TimeCalculator.kt`.
- Legacy visual/data resources: `res/layout/location_card_layout.xml`; `res/values/colors.xml`; `res/values/dimens.xml`; and `assets/databases/cities_info.db` (SQLite, user version 2). `promo/promo.png` is present at 1024 × 500 but could not be rendered in this workspace sandbox, as anticipated by the task; the inspectable layout/resources provide the visual reference.

## Open questions

None. TASK-006 specifies fixed metric units, required fields, state recovery, and the legacy immutable dataset. The time-offset presentation above uses a visible concise UTC form where space permits and requires accessible localized offset context in all cases; this is a design interpretation of the task's explicit accessibility requirement, not a new product behavior.
