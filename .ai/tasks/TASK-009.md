# TASK-009 — Add barometric pressure to flight parameters

## Status

READY_FOR_DESIGN

## Goal

Extend the existing Flight parameters card with a live barometric pressure reading when the device provides a pressure sensor, while preserving the current speed, vertical-speed, and altitude behavior.

## Context

The rewrite has a complete foreground flight-parameters card, but it intentionally omitted pressure in TASK-003. The original Smart Flight application displayed pressure as the fourth row of the same card and obtained it from Android's barometric pressure sensor. Pressure is the next small, self-contained dashboard capability to restore after the route/map work specified by TASK-008. It can use the rewrite's existing foreground lifecycle and does not require another location listener, a service, a permission, network access, or a new dependency.

This task restores the pressure reading only. It does not introduce the legacy settings screen or unit preferences; the rewrite currently uses fixed metric presentation and pressure must initially be shown in millibars (`mbar`), matching the legacy default.

## Original Application

The legacy Flight Parameters card is implemented by:

- `app/src/main/java/kniezrec/com/flightinfo/cards/gps/FlightParametersCardView.kt`
- `app/src/main/java/kniezrec/com/flightinfo/cards/gps/FlightParametersCardViewPresenter.kt`
- `app/src/main/res/layout/flight_parameters_card_layout.xml`

The legacy presenter subscribes to `SensorService.PressureCallback` and updates the pressure row whenever `Sensor.TYPE_PRESSURE` reports a value. `SensorService.kt` registers `Sensor.TYPE_PRESSURE` together with the other sensors and forwards the raw value in millibars. `avionic/calculators/Pressure.kt` formats the default unit as one decimal place followed by `mbar`, and converts to `inHg` only when the legacy preference selects that unit.

The legacy card shows speed, vertical speed, altitude, and pressure together. A device without a pressure sensor does not have usable pressure data; the rewrite must represent that locally without hiding or invalidating the other flight readings.

Relevant visual references are the legacy `flight_parameters_card_layout.xml`, `res/values/colors.xml`, `res/values/dimens.xml`, and the original promo image at `/home/ai-dev/smart-flight/promo/promo.png`. The promo image was available as a visual reference path, but exact behavior and dimensions are taken from the inspectable layout/resources.

## Current Application

The rewrite's `app/src/main/java/kniezrec/com/flightinfo/flight/FlightParametersState.kt` models the current speed, vertical speed, and altitude readings from `FlightLocationFix`. `FlightParametersController.kt` owns one foreground location listener, resets session data on stop, and already exposes a callback path suitable for keeping related live data in the active session.

`app/src/main/java/kniezrec/com/flightinfo/ui/gnss/FlightParametersCard.kt` renders the existing three rows in the established purple card style. `GnssStatusScreen.kt` places this card in the authorized dashboard. `MainActivity.kt` starts and stops the shared foreground observation in `onResume`/`onPause` and currently has no pressure-sensor registration.

The rewrite has no pressure state, Android pressure platform adapter, or pressure row. The current project already uses Compose Material 3, lifecycle-aware controller boundaries, resource-backed strings, and focused unit/Compose tests; follow those existing conventions.

## Functional Requirements

- In the authorized foreground dashboard, extend the existing Flight parameters card with a fourth row labeled `Pressure`.
- When a valid pressure reading is available, display it with one decimal place and the `mbar` unit. Use locale-aware decimal formatting.
- A pressure value must be finite and non-negative before it is displayed. Ignore invalid, NaN, infinite, or negative sensor values.
- Before the first valid pressure value in a foreground session, show the existing unavailable placeholder for the pressure row; do not display zero as a fabricated value. After a valid value has been shown, ignore invalid events and retain the last valid value until the session ends.
- If the device has no `Sensor.TYPE_PRESSURE` sensor, keep the pressure row visible with an explicit unavailable state and keep speed, vertical speed, altitude, GNSS, course, horizon, nearby-city, map, and route behavior unchanged.
- Register pressure observation only while the authorized dashboard is active in the foreground. Unregister it when the activity pauses, loses fine location, is destroyed, or otherwise stops the active observation session.
- Clear pressure data when a foreground session stops or restarts. A delayed callback from an older session must not repopulate the current card.
- Returning to the foreground starts a fresh pressure session and displays the unavailable placeholder until a new valid sensor event arrives.
- Permission onboarding must not register the pressure sensor or display the dashboard card.

## Technical Requirements

- Use the Android platform `SensorManager` and `Sensor.TYPE_PRESSURE`; do not add a sensor SDK, service, broadcast contract, or dependency.
- Keep sensor registration behind a small testable platform/controller boundary consistent with the existing `FlightParametersController`, GNSS, course, and horizon adapters.
- The pressure sensor is optional. Detect its absence before registration and report an unavailable pressure state without throwing or treating it as a global dashboard failure.
- Deliver sensor callbacks on the main/UI-safe executor or otherwise synchronize state before updating Compose-visible state. Unregister the exact listener that was registered.
- Do not register a second location listener. Pressure must join the existing foreground observation lifecycle and must not alter the shared location-fix fan-out.
- Preserve the current edge-to-edge shell, minimum SDK, offline behavior, package identity, and TASK-001 through TASK-008 lifecycle contracts.
- Keep all visible labels, units, placeholders, accessibility text, and state announcements in Android resources. The compact visible unit is `mbar`; expanded accessibility text should say `millibars` where needed.
- Do not add preference storage or a settings entry point as part of this task.

## UI Requirements

- Keep pressure in the existing Flight parameters card, after Altitude, matching the legacy card's information hierarchy.
- Reuse the current card background, spacing, typography, row height, placeholder, and accessibility approach. Do not create a separate pressure card.
- The pressure row must remain readable at large font scale, wrap without clipping, support RTL layout, and retain a minimum 48 dp interactive-free row height consistent with the other readings.
- The unavailable pressure value must be visually consistent with the existing `—` placeholder and must have an accessible equivalent such as `unavailable`; it must not be confused with a real zero pressure value.
- If the pressure sensor is unavailable, do not replace the whole Flight parameters card with an error state. Only the pressure row is unavailable.

## Acceptance Criteria

- [ ] With fine location granted and a pressure-capable device, the Flight parameters card contains Speed, Vertical speed, Altitude, and Pressure rows in that order.
- [ ] A valid pressure event such as `1013.25` is displayed as a locale-aware one-decimal millibar value, e.g. `1013.3 mbar` in an English locale.
- [ ] Before the first valid event or after session restart, the pressure row shows the existing unavailable placeholder; an invalid-only event sequence does not produce a value or show zero, NaN, or infinity, while invalid events after a valid reading retain that last valid reading.
- [ ] NaN, positive/negative infinity, negative values, and otherwise rejected sensor readings cannot update the displayed pressure.
- [ ] A device without `TYPE_PRESSURE` keeps the pressure row visible as unavailable and does not crash or mark the other dashboard cards unavailable.
- [ ] Pressure observation registers only during the active authorized foreground session and unregisters on pause, permission loss, destruction, and session restart; no listener registrations accumulate across recreation or retry.
- [ ] A delayed callback from a stopped or previous session cannot update the current pressure row.
- [ ] The pressure row uses resource-backed visible/accessibility strings, locale-aware formatting, and remains readable at large font scale and in RTL layouts.
- [ ] Existing speed, vertical-speed, altitude, GNSS, course, horizon, nearby-city, map, and TASK-008 route behavior remains unchanged.
- [ ] Focused unit tests cover formatting, validation, unavailable hardware, session reset, stale callbacks, registration failure, and unregister behavior; Compose/instrumentation coverage verifies row order, content, placeholder, and accessibility where the configured test environment supports it.
- [ ] No new permission, background service, notification, network request, pressure preference, settings screen, or third-party dependency is added.

## Implementation Plan

1. Define the immutable pressure state and pure validation/formatting rules, including the unavailable and waiting semantics, with unit coverage.
2. Add a testable Android pressure-sensor platform adapter and foreground controller that handles optional hardware, registration failure, exact unregistration, session tokens, and stale callback rejection.
3. Integrate pressure start/stop with the existing authorized foreground lifecycle without adding another location observer or disturbing route/map state.
4. Extend `FlightParametersState` and `FlightParametersCard` with the pressure row, resource-backed labels/units/accessibility semantics, and preserved existing reading behavior.
5. Add focused unit and Compose/instrumentation tests, then run the configured checks in an Android SDK/device environment.

## Files / Components Likely Affected

- `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- `app/src/main/java/kniezrec/com/flightinfo/flight/FlightParametersState.kt`
- `app/src/main/java/kniezrec/com/flightinfo/flight/FlightParametersController.kt` and/or new pressure controller/platform files under `flight/`
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/FlightParametersCard.kt`
- `app/src/main/res/values/strings.xml`
- Focused tests under `app/src/test/java/kniezrec/com/flightinfo/flight/` and `app/src/androidTest/java/kniezrec/com/flightinfo/ui/gnss/`

This list is guidance. Do not modify route, map, or unrelated dashboard components unless required for lifecycle integration or regression coverage.

## Reusable Existing Libraries / Components

- Android platform `SensorManager`/`Sensor.TYPE_PRESSURE`; no additional library is needed.
- Rewrite `FlightParametersController`, `FlightLocationPlatform`, and existing foreground lifecycle in `MainActivity`.
- Rewrite `FlightParametersState`, `FlightParametersCard`, `FlightParametersCardTest` patterns, Compose Material 3 card/row primitives, and resource-backed accessibility strings.
- Legacy behavioral references: `SensorService.PressureCallback`, `FlightParametersCardViewPresenter`, `Pressure.kt`, and `flight_parameters_card_layout.xml`.
- Legacy visual references: `res/values/colors.xml`, `res/values/dimens.xml`, and `/home/ai-dev/smart-flight/promo/promo.png`.

Do not reuse the legacy `SensorService`, `LocalBroadcastManager`, `FlightAppPreferences`, MVP presenters, XML layouts, or `Pressure.kt` as rewrite architecture.

## Risks and Edge Cases

- Many phones and emulators do not expose a barometer; unavailable pressure is a normal state, not a fatal dashboard error.
- Sensor callbacks can arrive after unregister or after activity recreation. Session tokens and exact listener ownership must prevent stale updates and leaks.
- Pressure sensor units are millibars/hPa on Android; do not apply an unverified conversion or display a duplicated unit.
- Sensor values may be transiently invalid. Reject them without clearing a previously valid value within the same session unless the controller explicitly resets the session; never replace an invalid event with zero.
- Large font scales and RTL layouts can expose row clipping or accessibility-label omissions.
- The route task's metric distance/ETA behavior and all existing foreground observation lifecycle must remain unaffected.

## Open Questions

None. The legacy default and current rewrite's fixed metric convention establish `mbar` for this task; unit preferences and settings are intentionally deferred to a separate future specification.
