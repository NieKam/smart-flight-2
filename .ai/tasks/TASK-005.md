# TASK-005 — Add live attitude horizon card

## Status

READY_FOR_DESIGN

## Goal

Add a foreground-only Horizon card to the precise-location dashboard. The card must present live, display-relative device pitch and roll as an accessible attitude visualization, provide an explicit calibration action, and truthfully communicate unsupported or failed orientation observation.

## Context

TASK-001 through TASK-004 now provide the permission-gated Compose dashboard, GNSS state, GPS flight parameters, and a live compass course. The legacy Smart Flight dashboard also includes a sensor-driven horizon/attitude instrument immediately after its course card. This task brings that remaining orientation instrument into the rewrite without reintroducing legacy bound services, card-hiding preferences, background sensor collection, or the legacy animation/blur overlay framework.

The legacy implementation is evidence for the instrument's purpose, its initial calibration behavior, its purple-card visual language, and its visible `Calibrate` control. It is not a requirement to retain its hidden long-press reset, accelerometer feature check, fixed pixel translation, or service lifecycle.

## Original Application

`app/src/main/java/kniezrec/com/flightinfo/cards/adapter/CardViewContainer.kt` constructs `HorizonCardView` immediately after `CourseCardView`; unlike location-dependent cards, its `ServiceBasedCardView` base reports that it does not need location permission.

`cards/horizon/HorizonCardView.kt`, `HorizonCardViewPresenter.kt`, and `res/layout/horizon_card_layout.xml` implement a 160 dp card with a shaded lower half, left/right pitch scales, a central aircraft symbol, and the cyan all-caps `Calibrate` action. The legacy presenter takes rotation-vector output from `SensorService`: the first pitch callback establishes a baseline, subsequent pitch differences are multiplied by four and clamped to a fixed visual range, and tapping Calibrate makes the next pitch callback the new baseline. It also has an undocumented long-press reset-to-zero behavior.

`services/SensorService.kt` receives `TYPE_ROTATION_VECTOR` events and forwards `OrientationCalculator` values. `avionic/calculators/OrientationCalculator.kt` performs its own filtering and display-rotation mapping. Its axis naming/mapping is inconsistent with Android's standard pitch/roll terminology, so it must not be copied as a mathematical specification. `res/drawable/fake_plane_instrument_icon.xml`, `scale_left_icon.xml`, `scale_right_icon.xml`, `res/values/colors.xml`, and `res/values/dimens.xml` are relevant visual references. The `promo/promo.png` screenshot is present (1024 × 500) but could not be rendered in the sandbox; use the listed XML/layout resources as the inspectable visual source of truth.

The legacy `app/libs/jsi-1.0.0.jar` and `trove4j-2.0.2.jar` do not support this feature and must not be reused. No map dependency is relevant.

## Current Application

`MainActivity.kt` renders the precise-location dashboard only after TASK-001 grants fine location, and starts/stops GNSS, shared foreground location, and Course observation as foreground state changes. `GnssStatusScreen.kt` is the scrollable, centered 600 dp maximum-width card column; it currently renders GNSS status, Flight parameters, then Course.

TASK-004 added `course/AndroidCourseOrientationPlatform.kt`, which has one `TYPE_ROTATION_VECTOR` listener and already remaps its rotation matrix for display rotation before deriving compass heading. `CourseController`, `ForegroundCourseObservationCoordinator`, `CourseState`, `CourseCard.kt`, and their tests establish state, lifecycle, retry, Material 3 card, resource-backed copy, focus, and TalkBack patterns. The adapter currently exposes only heading and cannot provide attitude values. The rewrite has no attitude state/controller/card or reusable legacy service.

The app has minSdk 31 and only Android platform sensor APIs are needed. The dashboard's current location permission gate is an established rewrite product decision; this task keeps the Horizon card and all of its sensor observation behind that gate even though the legacy card itself did not require location permission.

## Functional Requirements

- With fine location granted, render exactly one Horizon card directly after Course. With only approximate location, denied/revoked fine location, or while permission onboarding is visible, render no Horizon card and register no Horizon listener.
- While the eligible dashboard is foregrounded, obtain orientation from a usable device sensor source and show the device's display-relative pitch and roll as a live artificial-horizon visualization. It is an informational device-attitude indicator, not aircraft-certified telemetry and not a GPS-derived value.
- Before the first valid orientation callback in each foreground session, show `Waiting for attitude data…`. Do not retain an earlier session's calibration or attitude image, and do not present a level indication as a measured reading before a callback.
- On the first valid callback of a session, establish that pitch as the initial level reference and present the attitude visualization. Pitch movement relative to that reference moves the horizon/aircraft vertically; roll rotates it. Values must remain visually bounded under extreme, invalid, or rapidly changing sensor data.
- Tapping `Calibrate` clears the existing pitch reference and enters a recalibrating waiting state. The next valid orientation callback establishes the new level reference and recenters the pitch indication. Calibration must not restart the activity, change Course/GNSS/Flight parameters state, or use a location fix.
- Use current display rotation when interpreting orientation so portrait, reverse portrait, landscape, and reverse landscape show pitch/roll relative to the visible top of the device. A display rotation/configuration change must not leave a false previous orientation; reset to waiting/re-establish the reference from a current callback.
- If no supported orientation source is available, show a stable `Horizon unavailable` state explaining that the device cannot provide attitude data. Do not register a listener and do not offer a hide-card preference or misleading retry action.
- If a supported source cannot be registered or fails unexpectedly, show `Unable to read horizon` with `Try again`. Retry must re-evaluate availability, clear calibration/data, and register only when the eligible dashboard is foregrounded.
- New foreground sessions, pause, fine-permission loss, dashboard destruction, retry, sensor registration/read failure, and settings return must clear calibration/attitude state and unregister all added listeners. Repeated lifecycle transitions must not duplicate callbacks.
- Horizon observation is independent of GNSS fixes, GPS bearing, and whether location services are enabled. While the fine-permission dashboard remains foregrounded, it may read attitude when GNSS is unavailable or location services are off; it must still stop when the dashboard is not eligible/foregrounded.
- Do not add a service, background observation, notification, persistence, network request, new runtime permission, map/route functionality, pressure instrument, or user-configurable card visibility.

## Technical Requirements

- Reuse/refactor `AndroidCourseOrientationPlatform` into the smallest shared, testable Android orientation boundary needed to deliver both the existing Course heading and Horizon display-relative pitch/roll. One active dashboard session must have at most one underlying sensor listener; do not attach a second independent `SensorEventListener` for Horizon.
- Prefer `TYPE_ROTATION_VECTOR`; an Android-platform fallback may be used only if it yields display-relative pitch/roll with the same defined behavior. Hardware availability, registration failure, callback validity, and listener cleanup must remain outside Compose and be independently testable.
- Preserve all TASK-004 Course behavior: normalized compass heading, its existing display-rotation handling, current-session reset semantics, retry behavior, optional GPS bearing, and its coordination with the one shared foreground location listener. Refactoring the adapter must not cause Course to lose updates while Horizon is observing, or vice versa.
- Keep attitude presentation/framework state pure and testable. Reject non-finite input. Define and test a bounded mapping from relative pitch to visual vertical offset and from roll to visual rotation; it may be a direct/clamped mapping and must not copy the legacy `SCALE = 4` / `MAX_RANGE = 200` pixel formula.
- A calibration reference belongs only to the active Horizon session. A fresh reading after calibration or display rotation establishes its reference; stale callbacks from stopped/replaced registrations must be ignored.
- Coordinate Horizon from `MainActivity`'s existing foreground/permission lifecycle without changing the established GNSS/course precedence. It must not be coupled to `FlightParametersController.start()` because it has no location dependency.
- Put every user-visible/action/accessibility string in Android resources. Use semantic state descriptions rather than exposing raw sensor values unless visible text needs them. Preserve edge-to-edge and existing TASK-001 through TASK-004 behavior.

## UI Requirements

- Keep the dark-purple page/header and the centered, scrollable, maximum-600-dp column. Add Horizon after Course with the established 12 dp gap, `#5B5999` rounded card surface, and 4 dp elevation.
- In available state, retain the legacy visual intent: a compact 160 dp-or-larger attitude instrument with a distinguishable sky/ground split or horizon, symmetric pitch-scale cues, and a centered aircraft reference. The pitch and roll response should be apparent but must remain bounded; the visual cannot be the sole carrier of state.
- Include a clear `Horizon` text label and a concise textual attitude summary/state for TalkBack. The summary may describe pitch relative to the calibrated level and roll direction/amount, but it must not announce every sensor update. Use polite announcements only for state transitions, calibration completion, unavailable, and error.
- In initial/recalibrating waiting, unavailable, and error states, center the title/body using the established Course/GNSS card pattern. Only the error state exposes the existing cyan `Try again` text-action treatment.
- In available state, expose a cyan `Calibrate` control with a minimum 48 dp touch/focus target, visible keyboard/switch focus treatment, and an accessibility label/hint that says it sets the current pitch as level. Do not carry over the legacy hidden long-click reset.
- Use logical start/end placement, responsive sizing, and the outer dashboard scroll container. Portrait, landscape/expanded windows, RTL, TalkBack, keyboard/switch access, gesture insets, and 200% font scale must remain usable without clipped, overlapping, or unreachable content. If text scale makes the compact instrument insufficient, grow the card rather than clipping content.
- Reuse/adapt the legacy vector artwork only when it fits the Compose implementation. Do not add a raster asset or external icon library solely for this card. Avoid continuous decorative animation; sensor updates may use a brief reduced-motion-respecting visual transition.

## Acceptance Criteria

- [ ] With fine location granted, the dashboard renders exactly one Horizon card immediately after Course; it is absent with permission onboarding and no horizon sensor listener is active then.
- [ ] A new foreground session initially shows `Waiting for attitude data…` with no stale attitude/calibration result; the first valid callback establishes level reference and displays bounded pitch/roll response.
- [ ] Tapping Calibrate enters a recalibrating waiting state and the next valid callback becomes the new pitch reference, without changing Course, GNSS, or Flight parameters state.
- [ ] Display rotation/recreation resets the prior reference and makes the next valid orientation callback establish an attitude response correct for portrait, reverse portrait, landscape, and reverse landscape.
- [ ] An unavailable sensor source shows `Horizon unavailable`, registers no listener, and has no hide or retry action.
- [ ] A registration/read failure shows `Unable to read horizon`; `Try again` safely clears state and retries only while the eligible dashboard is foregrounded.
- [ ] Horizon can receive orientation while location services/GNSS are unavailable, but no Horizon listener remains after pause, fine-permission loss, dashboard destruction, retry replacement, or repeated foreground lifecycle events.
- [ ] Course continues to receive its correct heading and optional GPS bearing while Horizon is active, and there is no more than one underlying orientation listener for the active dashboard session.
- [ ] The available, waiting, unavailable, error, and calibration-complete UI is resource-backed, accessible via TalkBack/keyboard/switch access, responsive through 200% font scale and RTL, and has no clipped/overlapping/reachable-content regressions.
- [ ] Focused automated tests cover attitude mapping/clamping, calibration and current-session reset semantics, display-rotation reset, unavailable/registration failure/retry, stale callback/listener cleanup, shared Course+Horizon orientation delivery, and Compose state/action content.
- [ ] No map, route/city lookup, pressure display, background service, notification, persistence, network request, new permission, third-party sensor dependency, or unrelated dashboard behavior is added.

## Implementation Plan

1. Define framework-independent Horizon state, valid orientation sample/reference model, bounded pitch/roll mapping, calibration transition, and pure tests.
2. Refactor the existing Course Android orientation adapter into a small shared orientation source that derives display-relative heading, pitch, and roll and owns one idempotent sensor registration.
3. Add a Horizon controller/coordinator with active-session tokens, unavailable/error/retry behavior, display-rotation reset, and lifecycle ownership independent of foreground location observation.
4. Wire the Horizon controller into `MainActivity` foreground/permission/display-configuration handling without changing TASK-001 through TASK-004 outcomes.
5. Add an accessible responsive Horizon composable after Course, resource-backed copy, and Compose-native visual(s) or appropriate adapted legacy vectors.
6. Add focused unit and Compose/instrumentation tests, then run the configured checks where Android SDK/device support is available.

## Files / Components Likely Affected

- `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- `app/src/main/java/kniezrec/com/flightinfo/course/AndroidCourseOrientationPlatform.kt`, `CourseController.kt`, `CourseState.kt`, and `ForegroundCourseObservationCoordinator.kt` as needed to create/share the orientation boundary
- New focused Horizon state, controller/coordinator, and Android orientation-platform files under `app/src/main/java/kniezrec/com/flightinfo/`
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt` and a new adjacent Horizon-card composable
- `app/src/main/res/values/strings.xml`
- Optionally Compose-appropriate adaptations of legacy `fake_plane_instrument_icon.xml`, `scale_left_icon.xml`, and `scale_right_icon.xml`
- Focused unit and Compose/instrumentation tests under `app/src/test/` and `app/src/androidTest/`

## Reusable Existing Libraries / Components

- Rewrite: Android `SensorManager`, existing `AndroidCourseOrientationPlatform`, `CourseController`, `CourseCard`, `MainActivity` foreground/permission state, Material 3 card styling, Compose `Canvas`, and established purple/cyan UI tokens.
- Legacy visual/behavior reference: `HorizonCardView`, `HorizonCardViewPresenter`, `SensorService`, `OrientationCalculator`, `horizon_card_layout.xml`, and the plane/scale vector drawables.
- The original app's `jsi`/`trove4j` JARs, osmdroid archive/map libraries, city database, Lottie asset, and location service are not relevant and must not be reused.

## Risks and Edge Cases

- Rotation-vector availability and accuracy vary across physical devices and emulators. Waiting and unavailable states must remain useful and must not fabricate a level reading.
- Android orientation axes are display-dependent and easy to invert. Verify all four display rotations with controlled samples/platform tests; do not rely on the legacy calculator's mislabeled axes.
- Pitch/roll at extreme angles can be unstable or ambiguous. Clamp the visual mapping, reject non-finite samples, and keep the indicator informational rather than claiming aircraft attitude accuracy.
- A sensor callback can arrive after stop/retry/configuration changes. Session tokens and listener cleanup must prevent stale samples from changing visible state.
- Sharing the current course adapter risks cross-feature lifecycle regressions. Start/stop ownership must remain idempotent and preserve Course updates while both cards are present.
- Fine-location revocation is the dashboard gate, not a sensor permission. Ensure the product gate stops Horizon promptly without adding a sensor permission.

## Open Questions

None. This scope intentionally keeps the legacy initial-pitch calibration and visible attitude instrument, omits its undocumented long-press zero reset and hide preference, and fixes Horizon as a foreground, fine-dashboard-gated device-attitude feature independent of GNSS location availability.
