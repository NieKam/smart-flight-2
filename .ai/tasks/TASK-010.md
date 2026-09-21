# TASK-010 — Add configurable display units

## Status

READY_FOR_DESIGN

## Goal

Add an accessible Compose settings flow that lets users choose the display units used by the existing Smart Flight dashboard, nearby-city card, and route card. Persist the choices locally and apply them immediately to already-rendered values without changing the underlying sensor, distance, or route calculations.

## Context

TASK-001 through TASK-009 established the authorized foreground dashboard, GNSS status, flight parameters, course, horizon, nearby city, offline map, route planning, and barometric pressure. The current rewrite intentionally presents fixed metric units. TASK-009 explicitly deferred unit preferences while fixing its initial pressure presentation to `mbar`.

The original Smart Flight application exposes unit preferences in its Settings screen and recalculates the displayed speed, vertical speed, altitude, pressure, distance, and route values from those preferences. The rewrite should preserve that user-visible capability in a Compose-native way, without copying the legacy PreferenceFragment, service lifecycle, or unrelated legacy settings.

## Original Application

The legacy entry point exposes `SettingsActivity` from the main activity menu (`app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`, `common/Navigation.kt`, and `res/menu/app_menu.xml`). `SettingsFragment` loads `res/xml/app_preferences_layout.xml`, whose Units category contains list preferences for:

- speed: kilometres per hour, miles per hour, or knots;
- altitude: metres or feet;
- distance: kilometres or miles;
- vertical speed: metres per second, metres per minute, or feet per minute;
- pressure: millibar or inch of mercury.

The legacy default is the first option for each preference. `FlightAppPreferences.kt` reads the values from the shared preference file, and the legacy calculators define the conversion behavior in `avionic/Speed.kt`, `avionic/calculators/Altitude.kt`, `VerticalSpeedCalculator.kt`, `Pressure.kt`, and `DistanceCalculator.kt`. Cards register preference listeners so displayed values update while the dashboard is active. Route distance, remaining distance, and nearby-city distance use the distance preference.

The original visual reference is the dark-purple card dashboard shown in `/home/ai-dev/smart-flight/promo/promo.png`; its exact pixel layout is not a specification for the new settings screen. The legacy settings resource files are the authoritative reference for available choices and defaults.

## Current Application

The rewrite has one `MainActivity` and a vertically scrollable Compose dashboard rooted at `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt`. There is no app bar, settings destination, unit preference model, or persisted unit store.

Current display formatting is fixed in these locations:

- `ui/gnss/FlightParametersCard.kt`: km/h, m/s, m, and mbar;
- `ui/gnss/NearbyCityCard.kt`: kilometres;
- `ui/route/RouteCard.kt` and `route/RouteModels.kt`: kilometres for fixed and remaining route distance;
- `res/values/strings.xml`: the visible and accessibility unit strings.

The domain state stores canonical values: `FlightParametersState` stores kilometres per hour, metres per second, metres, and millibars; nearby-city and route state store kilometres. `FlightParametersController`, `NearbyCityController`, `RouteController`, and map/route geometry must continue to use these canonical values.

The current project already uses Compose Material 3, `SharedPreferences` is available through the Android platform, and no new settings or preference dependency is required.

## Functional Requirements

- Add a discoverable **Settings** action from the authorized dashboard. The settings destination must be reachable without requiring a new permission or network connection.
- Provide five independent unit selectors with these exact options and defaults:
  - Speed: `km/h` (default), `mph`, `kt`.
  - Altitude: `m` (default), `ft`.
  - Distance: `km` (default), `mi`.
  - Vertical speed: `m/s` (default), `m/min`, `ft/min`.
  - Pressure: `mbar` (default), `inHg`.
- Persist each selection across activity recreation and process restart.
- Apply a changed selection when the user confirms/selects it; the dashboard must show the selected unit without requiring an app restart.
- Convert and format only at presentation boundaries. Keep controller/domain state and route calculations in the current canonical metric units.
- Apply the speed choice to the Speed row, the vertical-speed choice to the Vertical speed row, the altitude choice to the Altitude row, and the pressure choice to the Pressure row.
- Apply the distance choice to nearby-city distance, route fixed distance, and route remaining distance. Arrival time and duration remain time values and are not affected by distance selection.
- Preserve existing waiting, unavailable, error, and placeholder states. A unit change must not start or stop sensors, reload city data, reload map tiles, alter route endpoints, or reset route state.
- Recompose visible values immediately when settings change, including values currently displayed in the route and nearby-city cards.
- If settings are opened while permission onboarding is displayed, the settings action is not required to be shown; unit settings may remain reachable only from the authorized dashboard.

## Technical Requirements

- Follow the existing single-activity Compose architecture. Do not introduce a separate module, navigation framework, or legacy `PreferenceFragment` solely for this feature.
- Use a small typed unit-preference model and a local persistence boundary. Preference keys and default values must be stable and must not collide with the existing location-permission or route preference stores.
- Keep conversion/formatting functions deterministic and independently testable. Reject or safely handle non-finite values using the same safeguards already used by the current cards; never render `NaN` or infinity.
- Preserve locale-aware number formatting and the existing sign behavior for vertical speed. Use resource-backed labels, units, option names, and accessibility text.
- Match the legacy conversion semantics where they are observable: `1 m/s = 3.6 km/h`, `1 km = 0.621371 mi`, `1 m = 3.28084 ft`, `1 m/s = 60 m/min`, `1 m/s = 196.850394 ft/min`, and `1 mbar = 0.02953 inHg`. Round displayed values according to the existing row conventions and the legacy calculators: one decimal place for normal values, with a sensible locale-aware representation for unit values.
- Use the existing canonical `FlightParametersState`, `NearbyCityState`, and `RouteDetails` values as inputs. Do not store converted values in state or preferences.
- Unit changes must be observed by the dashboard while it is composed. Restarting or returning from the settings destination must not create duplicate sensor, GNSS, location, compass, horizon, city, map, or route observers.
- Do not add a third-party preferences library, network dependency, or map library. The existing osmdroid integration remains unchanged.

## UI Requirements

- Add a Material 3 top app bar or equivalent discoverable dashboard control labeled **Settings** with an accessible action description. Preserve the established dark-purple Smart Flight shell and avoid changing card order or unrelated dashboard styling.
- Present settings as a clearly titled screen or destination with a back action. The user must be able to identify the current value for every selector without opening each control.
- Use standard accessible single-choice controls such as dropdown menus or modal single-choice dialogs. Do not use a custom interaction that depends on color alone.
- Group the five selectors under a localized **Units** section. Each row must expose its label and current selection to TalkBack and support normal touch targets of at least 48 dp.
- Settings must remain usable in portrait and landscape, at large font scales, and in RTL. Labels/options may wrap; no value may be clipped or hidden.
- Use the legacy dark-purple/purple/cyan visual language as a reference, but keep the new screen consistent with the current Material 3 theme rather than reproducing legacy XML styling literally.
- Keep unit abbreviations compact in card values (`km/h`, `mph`, `kt`, `m`, `ft`, `m/s`, `m/min`, `ft/min`, `mbar`, `inHg`, `km`, `mi`) and provide expanded accessible wording where needed.

## Acceptance Criteria

- [ ] An authorized user can open a clearly labeled Settings screen from the dashboard and return without losing dashboard state.
- [ ] Settings shows Speed, Altitude, Distance, Vertical speed, and Pressure selectors with the specified options and metric defaults.
- [ ] Each selector exposes its current value accessibly and supports selecting exactly one option.
- [ ] Every changed selection persists across activity recreation and process restart.
- [ ] Speed values render correctly in km/h, mph, and kt; vertical speed renders correctly in m/s, m/min, and ft/min; altitude renders correctly in m and ft; pressure renders correctly in mbar and inHg.
- [ ] Nearby-city distance and both route distance values render correctly in km and mi; route arrival time and duration remain unchanged by distance selection.
- [ ] Changing a unit immediately updates currently visible dashboard values, including an already available route or nearby-city value, without restarting sensor or location observation.
- [ ] Waiting, unavailable, error, and dash states remain unchanged except for the unit of a valid displayed value; no invalid numeric value is rendered.
- [ ] Canonical domain values and route/map calculations remain metric and are not mutated by display preferences.
- [ ] Unit labels, option names, visible values, and accessibility descriptions are resource-backed and behave correctly at large font scale and in RTL.
- [ ] Focused unit-conversion, formatting, persistence/default, and Compose UI coverage is added; existing dashboard, route, map, and sensor lifecycle tests continue to pass.
- [ ] No notification, background tracking, forced orientation, map zoom, network request, or unrelated legacy setting is added by this task.

## Implementation Plan

1. Define typed unit selections, stable preference keys, defaults, and a persistence/read-observation boundary.
2. Add pure conversion and formatting helpers with focused tests for all options, signs, rounding, locale behavior, and invalid inputs.
3. Thread the current unit preferences into the flight-parameters, nearby-city, and route composables without changing their canonical state models or sensor/location controllers.
4. Add the authorized dashboard Settings entry and a responsive Compose settings screen with accessible single-choice selectors and back navigation.
5. Subscribe the dashboard presentation to preference changes so visible values recompose immediately while active.
6. Add resource strings and Compose/unit tests, then verify lifecycle behavior and that existing route/map/sensor observation remains unchanged.

## Files / Components Likely Affected

- `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/FlightParametersCard.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/NearbyCityCard.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/route/RouteCard.kt`
- `app/src/main/java/kniezrec/com/flightinfo/route/RouteModels.kt` or a new presentation-formatting package
- New unit-preference model/store and settings composables under `app/src/main/java/kniezrec/com/flightinfo/`
- `app/src/main/res/values/strings.xml` and, if needed, localized resource files
- Relevant unit and Compose/instrumentation tests

## Reusable Existing Libraries / Components

- Existing Compose Material 3 `Scaffold`, cards, theme, `SnackbarHost`, and scrollable dashboard shell.
- Existing canonical state models and controllers: `FlightParametersState`, `NearbyCityState`, `RouteDetails`, and `RouteController`.
- Android `SharedPreferences`/`SharedPreferences.OnSharedPreferenceChangeListener` or an equivalent small platform-backed store; no new dependency is needed.
- Legacy references only for behavior/conversions: `FlightAppPreferences`, `app_preferences_layout.xml`, `Speed.kt`, `Altitude.kt`, `VerticalSpeedCalculator.kt`, `Pressure.kt`, and `DistanceCalculator.kt`.
- Existing `promo/promo.png` and rewrite theme colors for visual continuity; do not copy legacy XML settings layouts.

## Risks and Edge Cases

- A preference update arriving while a route/city lookup callback is pending must affect only presentation; it must not invalidate or duplicate the lookup.
- A process restart with missing, malformed, or out-of-range stored values must fall back independently to each selector's metric default.
- Negative vertical speed must retain its sign after conversion and formatting.
- Pressure conversion must preserve the current valid/unavailable retention behavior from TASK-009; changing units must not turn an unavailable value into zero.
- Rounding near unit boundaries can produce locale-specific output; tests must assert conversion/formatting behavior without assuming an English decimal separator for all locales.
- Large values, zero values, and non-finite values must not overflow or produce invalid text.
- Settings should not be exposed as a false replacement for app permission settings: location permission onboarding and Android system settings behavior remain unchanged.
- The legacy `Show notification`, `Keep screen always on`, `Force bigger map zoom`, and `Force portrait orientation mode` preferences are intentionally excluded because the rewrite has no corresponding requested behavior and some would affect lifecycle or platform policy beyond this task.

## Open Questions

None. The legacy unit choices, defaults, conversion semantics, current canonical state units, and TASK-009's deferred-unit decision provide the required product behavior. The Designer may choose between a dropdown and single-choice dialog as long as the accessibility and immediate-update requirements are met.
