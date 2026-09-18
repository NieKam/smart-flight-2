# TASK-002 — Add offline GNSS status card

## Status

READY_FOR_DESIGN

## Goal

Replace TASK-001's minimal granted entry state with a single offline GNSS status card that tells a user whether Smart Flight is waiting for a signal, can use GNSS satellites for a position, or cannot begin because device location services or GNSS hardware are unavailable.

## Context

TASK-001 establishes precise/fine location as the prerequisite for Smart Flight. The next useful user-facing step is feedback about acquiring a position from the device during a flight, including when no network is available. It must not imply that a route, map, city lookup, altitude, speed, or other flight dashboard feature already exists.

The legacy app showed a satellites card with an animated waiting state and a count of satellites used in a fix. Its legacy service also mixed location updates, background behavior, notifications, and later dashboard concerns. Those behaviors are evidence only; this task deliberately limits the rewrite to a foreground GNSS-status experience.

## User-visible behavior

After fine location is granted, Smart Flight shows its existing purple app shell and one GNSS status card in place of the TASK-001 granted confirmation card.

The card has these mutually exclusive states:

| State | Required visible content | User action |
| --- | --- | --- |
| Waiting for GNSS status | Title: `GNSS status`. Message: `Waiting for GPS signal…` | None |
| GNSS status available | Title: `GNSS status`. Summary: `Using N satellite` for one satellite or `Using N satellites` otherwise, where N is the current number used in the fix. Show a compact visual list of the currently reported satellites that distinguishes satellites used in the fix from satellites not used in the fix without relying on color alone. | None |
| Device location services disabled | Title: `Location services are off`. Message explains that location services must be turned on to receive a GNSS signal. | `Open location settings` opens Android's location-source settings. |
| GNSS unavailable on this device | Title: `GNSS unavailable`. Message explains that the device does not provide GNSS hardware. | None |
| GNSS status cannot be read | Title: `Unable to read GNSS status`. Message says Smart Flight could not start GNSS status. | `Try again` retries status observation while the screen remains visible. |

The waiting state is normal indoors, in flight before acquisition, and after a GNSS callback reporting zero satellites. It is not an error and must not offer a misleading GPS-enable action.

## Functional requirements

- Show the GNSS status card only when precise/fine location is currently granted. If fine permission is absent or reduced to approximate-only, continue to show TASK-001 onboarding; coarse location alone is never sufficient.
- While the GNSS status card is visible and the host UI is active, observe device GNSS satellite status using the Android platform API.
- On each platform status callback, render the current satellite collection and derive N from satellites marked as used in the current fix. Do not retain or display stale satellite values once observation is restarted.
- The visual satellite list must identify each reported satellite by a stable position/index within the current report and indicate whether it is used in the fix. Signal strength may be shown when reported by the platform, but it is supplementary and must not be the sole indicator of connection.
- If the device reports zero satellites, render the waiting state rather than an empty chart, zero-satellite success message, or error.
- Before starting observation and whenever the screen resumes, determine whether device location services are enabled. If they are disabled, render the location-services-disabled state and do not register a GNSS callback.
- If location services are enabled but the device does not advertise GPS/GNSS hardware, render the GNSS-unavailable state and do not register a GNSS callback.
- If registering or observing GNSS status fails unexpectedly, render the GNSS-status-cannot-be-read state. A retry must re-evaluate hardware/service availability before attempting registration again.
- Opening location settings must target Android's location-source settings. On return to the app, re-evaluate fine permission, hardware availability, and location-services state; update the displayed state without an app restart.
- Start observation only for the active, visible foreground UI and stop/unregister it when that UI is no longer active. Do not continue GNSS observation in the background for this task.
- All app-visible strings must be resources. The singular/plural satellite summary must be localized using Android plurals.

## States and edge cases

- Fresh fine grant: transition from onboarding to the waiting state until a GNSS callback is received or an availability state is detected.
- Fine granted plus coarse granted: fine remains sufficient; the card may show GNSS status.
- Coarse-only, denied, or revoked fine permission: TASK-001 onboarding takes precedence and GNSS observation stops.
- Location services disabled after a prior successful status: replace the prior satellite data with the location-services-disabled state on the next lifecycle/availability evaluation.
- Returning from Android location settings with services enabled: resume waiting/observation; do not show old satellite data until a new callback arrives.
- A zero-satellite callback: show waiting, not a failure.
- A device without GNSS: show the unavailable state even if generic location services are enabled.
- A callback-registration failure or platform security failure: show the retryable error state and do not crash.
- Rotating or recreating the activity must not leak duplicate GNSS callbacks or leave background observation running.
- This task does not require a usable GNSS signal in the test environment; the waiting state must remain valid indefinitely.

## Acceptance criteria

- [ ] With fine location granted and location services enabled on a GNSS-capable device, the prior TASK-001 granted confirmation is replaced by one GNSS status card whose initial state is `Waiting for GPS signal…`.
- [ ] A supplied GNSS status containing satellites updates the card with the correct localized count of satellites used in the fix and a non-color-only distinction between used and unused reported satellites.
- [ ] A supplied zero-satellite status renders the waiting state.
- [ ] With fine permission absent or approximate-only, onboarding remains visible and GNSS status is not shown or observed.
- [ ] With device location services disabled, the card shows `Location services are off`; tapping `Open location settings` opens Android location-source settings.
- [ ] Returning from location settings re-evaluates availability and resumes waiting/observation when services are enabled.
- [ ] A device without GNSS hardware shows `GNSS unavailable` and does not attempt GNSS observation.
- [ ] A controlled registration/observation failure shows `Unable to read GNSS status`; `Try again` retries without crashing.
- [ ] GNSS observation is registered while the foreground status UI is active and unregistered when it is no longer active; lifecycle recreation does not accumulate registrations.
- [ ] UI state derivation, plural/count derivation, and platform-observation lifecycle behavior have focused automated coverage. UI content/actions have Compose UI or instrumentation coverage where the existing test setup supports it.
- [ ] No map, route, city lookup, telemetry calculations, background service, notification, network request, or third-party chart/location dependency is added.

## Out of scope

- Displaying the device's latitude, longitude, altitude, speed, bearing, vertical speed, pressure, horizon, or course.
- Persisting a last known location or any satellite history.
- Maps, offline map data, routing, airport/city selection, city lookup, estimated arrival, or geocoding.
- Background location/GNSS collection, foreground services, notifications, widgets, and boot receivers.
- A custom satellite sky plot, signal-strength chart, or importing the legacy chart/Lottie libraries.
- Requesting any new permission beyond TASK-001's fine and Android-12+ coarse companion permissions.
- Changing TASK-001 onboarding, its task/design artifacts, or unrelated project architecture.

## Technical/platform constraints

- Use Android's GNSS-status APIs supported by the rewrite's current minimum SDK. Do not use deprecated legacy GPS-status APIs.
- Fine location is required before interacting with GNSS status. Android 12+ coarse permission may remain part of TASK-001's paired system request, but it is not a success condition for this feature.
- The feature must work without internet connectivity and must not depend on a network provider or remote service.
- Treat Android system settings screens and their availability as platform-owned. If the location-settings intent cannot be handled, retain the disabled state and provide a short accessible transient failure message.
- Keep platform callbacks, registration, and teardown lifecycle-aware and testable. The existing focused permission/domain and UI feature packages are an appropriate baseline; do not require a broad architecture migration solely for this task.
- Use the existing Compose and Material 3 stack. Do not add a location SDK, map SDK, chart library, or animation library.
- Preserve edge-to-edge treatment, app identity, and TASK-001's permission behavior.

## Relevant original application references

- Dashboard composition and permission gating: `app/src/main/java/kniezrec/com/flightinfo/cards/adapter/CardViewContainer.kt`.
- Legacy satellite UI and empty/waiting state: `cards/satellites/SatellitesCardView.kt`, `SatellitesCardViewPresenter.kt`, `NoSatellitesFoundView.kt`, `res/layout/satellites_card_layout.xml`, and `res/layout/no_satellites_layout.xml`.
- Legacy GNSS/location API use: `services/location/LocationProvider.kt`, `LocationProviderApi24.kt`, `LocationProviderImpl.kt`, and `LocationService.kt`.
- Legacy dashboard shell and visual tokens: `res/layout/activity_main.xml`, `res/layout/content_main.xml`, `res/values/colors.xml`, `res/values/dimens.xml`, and `res/values/strings.xml`.
- Legacy visual media inspected as context: `promo/promo.png` (1024 by 500); the sandbox image viewer could not render it, so layout requirements above are derived from inspectable source resources rather than image measurements.

## Open questions

None. The task intentionally limits the next increment to foreground GNSS status; later product work can specify position, flight data, and navigation features separately.

## Future work

- A precise-position card using location fixes after GNSS status is established.
- Flight parameters such as altitude, speed, course, and vertical speed.
- Route planning, city/airport selection, and offline map behavior.
- A richer GNSS visualization if a future product/design task establishes its value and accessibility requirements.
- Explicit product policy for background tracking and user-controlled notifications, if needed.
