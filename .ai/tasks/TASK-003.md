# TASK-003 — Add live GPS flight-parameters card

## Status

READY_FOR_DESIGN

## Goal

Add a foreground-only flight-parameters card beneath the existing GNSS-status card. Once Smart Flight receives a GPS location fix, the card must display the current ground speed, GPS altitude, and vertical speed in fixed metric units. Before a usable fix is available, it must plainly communicate that it is waiting rather than displaying invented or stale data.

## Context

TASK-001 established fine-location permission and TASK-002 established a foreground GNSS-status card. The original dashboard's next location-driven surface is its flight-parameters card, which updates speed, altitude, and vertical speed from location fixes. Adding that core, offline data is the next useful step after satellite-status feedback.

The legacy card also displays barometric pressure. Pressure comes from a separate, optional device sensor and is not required to establish the location-fix data path. It is explicitly out of scope for this task so the rewrite does not combine two independent hardware integrations.

## Original Application

`app/src/main/java/kniezrec/com/flightinfo/cards/adapter/CardViewContainer.kt` places `FlightParametersCardView` directly after `SatellitesCardView` in the permission-gated dashboard.

`cards/gps/FlightParametersCardView.kt`, `FlightParametersCardViewPresenter.kt`, and `res/layout/flight_parameters_card_layout.xml` render four label/value rows. The presenter receives GPS location updates from the legacy `LocationService` and formats location speed, altitude, and calculated vertical speed. Its pressure row is fed independently by `SensorService`.

Relevant legacy calculation helpers are `avionic/Speed.kt`, `avionic/calculators/Altitude.kt`, and `avionic/calculators/VerticalSpeedCalculator.kt`. They expose metric defaults and one-decimal formatting; the rewrite should preserve the user-visible meaning and units, not copy the legacy service, deprecated location APIs, preference plumbing, or its time calculation literally.

`res/values/dimens.xml`, `res/values/styles.xml`, and `promo/promo.png` establish the shared purple-card visual language. The local promo image exists at `promo/promo.png` (1024 × 500), but the sandbox viewer could not render it; the inspectable layout/resources are the authoritative detail for this task.

The requested legacy library directory `/home/ai-dev/smart-flight/lib` does not exist. The actual local legacy libraries are under `app/libs/` (`jsi-1.0.0.jar` and `trove4j-2.0.2.jar`); neither supports this feature and neither should be imported.

## Current Application

The rewrite currently gates its one-card GNSS screen on precise/fine permission in `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`.

`gnss/GnssStatusController.kt` and `gnss/AndroidGnssStatusPlatform.kt` use the current Android `GnssStatus.Callback` API, check location-services and GNSS-hardware availability, register only while the activity is foregrounded, and clear status on restart. `ui/gnss/GnssStatusScreen.kt` supplies the existing purple shell, header, responsive scrolling, card dimensions, accessible state copy, and location-settings/retry actions. The app has minSdk 31 and already declares the fine/coarse permissions required by TASK-001.

There is no location-fix observer, flight-parameter state, converter, settings UI, or sensor/barometer integration in the rewrite.

## Functional Requirements

- With precise/fine location granted, show the existing GNSS-status card followed by one `Flight parameters` card in the same scrollable dashboard. Fine permission remains the sole authorization gate; approximate-only, denied, or revoked fine permission continues to show only TASK-001 onboarding and stops all location-fix observation.
- The flight-parameters card must initially show `Waiting for GPS position…` and no numeric values until a valid location fix is received in the current foreground observation session.
- Observe foreground GPS location fixes using the Android platform location API. For each valid current fix, display these location-derived rows:
  - `Speed`: ground speed, converted from the platform metres-per-second value to kilometres per hour, formatted to one decimal place with `km/h`.
  - `Vertical speed`: rate of altitude change derived from successive valid GPS altitude samples, formatted to one decimal place with an explicit `+` or `−` sign and `m/s`.
  - `Altitude`: GPS altitude, formatted to one decimal place with `m`.
- The metric units above are fixed for this task. Do not add a settings screen, persistence, or selectable units.
- The first valid location fix provides speed and altitude, but vertical speed must remain unavailable (shown as `—`) until a later valid sample permits a rate calculation. A non-positive or unusable elapsed interval must not produce a false rate, infinity, or `NaN`; retain `—` until a valid subsequent sample.
- Derive vertical speed using the location timestamps intended for elapsed-time measurement, not wall-clock time. If modest smoothing is used to avoid noisy display changes, it must be deterministic, test-covered, and must not delay the first valid rate indefinitely.
- A location update without speed and/or altitude validity must not fabricate the corresponding value. Keep that individual row as `—`; valid fields in the same fix may still update. Do not substitute a last-known location for a current observation-session fix.
- A newly started observation session, loss of fine permission, location services becoming disabled, unavailable GPS/GNSS hardware, registration/security failure, or returning from settings must clear all previously displayed flight data and vertical-speed history. The next card state must again wait for a new fix.
- Before registering, and whenever the activity resumes or the user retries GNSS observation, use the same availability facts as TASK-002. Do not request location updates when location services are disabled or GNSS hardware is unavailable.
- Register location-fix observation only while the precise-permission dashboard is active and foregrounded; unregister it on pause, permission loss, screen/lifecycle destruction, and any unavailable/error state. Do not leave duplicate listeners after recreation or retry.
- If foreground location registration fails unexpectedly (including a security failure), keep the GNSS card's existing error/retry behavior and render the flight-parameters card in its waiting/no-data state. The app must not crash.
- Preserve TASK-001 permission behavior and every TASK-002 GNSS-status state/action. This feature must work without internet access and must not depend on a network provider.

## Technical Requirements

- Use the platform `LocationManager`/current Android location-listener API supported by minSdk 31. Do not use deprecated `GpsStatus`, fused-location/network SDKs, a background service, or a third-party location dependency.
- Reuse or extend the current narrow GNSS platform/controller boundary where that keeps availability checks and lifecycle ownership coherent. It is acceptable to introduce a focused location-fix observer/controller and pure presentation/calculation state; do not require a broad architecture migration, ViewModel, repository, DI framework, Flow migration, or a legacy bound service.
- Keep location callbacks, registration, and teardown testable through a small platform interface. Model whether speed/altitude are present and avoid passing Android framework `Location` into pure UI/calculation code where a small data value is sufficient.
- Use `Location.hasSpeed()` and `Location.hasAltitude()` (or their API-equivalent validity guarantees) before rendering values. Use elapsed-realtime timing supplied by location fixes for vertical-speed intervals.
- All user-visible strings, including the waiting message, labels, units, and unavailable placeholder content descriptions where needed, must be Android resources. Format numeric values with a locale-aware formatter; do not rely on the device default locale through Kotlin/Java `String.format` without an explicit locale-aware resource/formatter strategy.
- Do not add manifest permissions: TASK-001's fine/coarse declarations are sufficient. Preserve edge-to-edge behavior, app identity, and existing location-settings handoff.

## UI Requirements

- Retain the TASK-002 shell/header and place the flight-parameters card after the GNSS-status card, with the established 12 dp outer spacing, purple rounded card surface, and responsive single-column/600 dp maximum-width behavior.
- Match the legacy flight-parameters hierarchy: simple left-aligned label/value rows in this order: Speed, Vertical speed, Altitude. Do not add a chart, map, gauge, compass, or pressure row.
- The card must have a clear title, `Flight parameters`, distinct from the individual row labels.
- In its waiting state, center `Waiting for GPS position…` in the card and do not present a misleading zero value. When at least one field is available, render the titled row layout and show `—` for only unavailable fields.
- Values and labels must remain legible at 200% font scale, work in RTL, expose label plus value together to TalkBack, and avoid color as the sole carrier of waiting/unavailable state. The page, not a nested card list, must scroll when space is limited.
- State changes from waiting to the first available data may use the same brief, reduced-motion-respecting transition style as TASK-002. Continuous animation is not required.

## Acceptance Criteria

- [ ] With fine location granted, enabled location services, and GNSS hardware, the dashboard contains the existing GNSS-status card followed by one `Flight parameters` card.
- [ ] Before a current-session valid GPS location fix, the flight-parameters card says `Waiting for GPS position…` and shows no prior-session numeric readings.
- [ ] A valid fix with speed and altitude updates the card to show correctly locale-formatted, one-decimal km/h speed and metre altitude.
- [ ] The first valid altitude sample shows `—` for vertical speed; a later valid sample with a positive elapsed interval shows correctly signed, one-decimal m/s vertical speed based on altitude change over elapsed time.
- [ ] Missing speed or altitude validity leaves only that affected value as `—`; the app does not render stale values, `NaN`, infinity, or an invented zero.
- [ ] Restarting/returning to the foreground, revoking fine permission, disabling location services, reporting absent GNSS hardware, or a registration failure clears old flight values and vertical-speed history; no location listener remains active when the dashboard is not foregrounded/eligible.
- [ ] Existing TASK-002 waiting, available, disabled, unavailable, error, retry, and settings behavior remains available and does not accumulate duplicate GNSS or location registrations.
- [ ] The card supports portrait, landscape/expanded windows, RTL, TalkBack, keyboard focus, gesture insets, and 200% font scale without clipped or unreachable content.
- [ ] Focused automated tests cover the converter/calculator and its first-sample/invalid-interval/missing-field cases, observer availability/error/lifecycle cleanup, and Compose UI waiting/value/placeholder content.
- [ ] No pressure sensor, settings/unit selector, map, route, city lookup, network request, background service, notification, last-known-location persistence, or third-party location dependency is added.

## Implementation Plan

1. Define a small location-fix data/state model and pure metric formatting/vertical-speed calculation with explicit validity and session-reset behavior.
2. Add a lifecycle-testable Android location-observation boundary using current platform APIs; align its availability checks, start/stop, retry, and error handling with the existing GNSS controller.
3. Coordinate precise-permission, foreground lifecycle, GNSS retry/settings-return handling, and the location observer from the existing narrow activity/controller boundary so both registrations are cleaned up together.
4. Extend the GNSS dashboard screen to render the flight-parameters card below the existing status card, including localized strings, accessible row semantics, responsive scrolling, and the no-data state.
5. Add focused unit and Compose/instrumentation coverage for calculations, state reset, lifecycle cleanup, and rendered content; run the configured checks where the Android SDK is available.

## Files / Components Likely Affected

- `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- `app/src/main/java/kniezrec/com/flightinfo/gnss/GnssStatusController.kt` and `AndroidGnssStatusPlatform.kt`, if safely extended for shared availability/lifecycle coordination
- New focused location/flight-parameter state, calculation, controller, and Android platform-adapter files under `app/src/main/java/kniezrec/com/flightinfo/`
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt` or a new adjacent flight-parameters composable
- `app/src/main/res/values/strings.xml`
- Focused unit and Compose/instrumentation tests under `app/src/test/` and `app/src/androidTest/`

## Reusable Existing Libraries / Components

- Rewrite: `LocationManager`, existing fine-permission controller, `GnssStatusController`, `AndroidGnssStatusPlatform`, Compose Material 3, and the TASK-002 dashboard/shell tokens.
- Legacy behavior references: `FlightParametersCardView`, `FlightParametersCardViewPresenter`, `Speed`, `Altitude`, `VerticalSpeedCalculator`, and `flight_parameters_card_layout.xml`.
- Legacy `app/libs/jsi-1.0.0.jar` and `trove4j-2.0.2.jar` are unrelated and must not be reused. No osmdroid/map dependency is needed for this task.

## Risks and Edge Cases

- A GNSS satellite-status callback does not itself guarantee a location fix; the waiting flight-parameters state may legitimately persist while TASK-002 shows satellites.
- GPS altitude and vertical speed can be noisy. This task reports platform GPS-derived values and must not imply aviation-grade accuracy or certification.
- Location timestamps can repeat or arrive out of order; invalid/non-positive elapsed intervals must reset/withhold the vertical-speed result rather than divide by zero or calculate backwards.
- Some fixes omit speed and/or altitude, and emulators may never emit a fix. The partial/no-data display must remain stable indefinitely.
- Android location-service/hardware checks can change while the app is paused or the settings screen is open. Clear visible values before restarting observation so stale data is never represented as current.
- Coordinate the new listener with TASK-002 carefully: retries, activity recreation, and pause/resume must not leak or duplicate either listener.

## Open Questions

None. Fixed metric units and the omission of pressure are deliberate scope decisions for this location-fix increment; future tasks can specify unit preferences and optional barometer behavior.
