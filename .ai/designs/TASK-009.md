# TASK-009 — Barometric pressure in the flight parameters card

## Purpose and scope

Add a live barometric pressure reading to the existing authorized foreground
Flight parameters card. Pressure is an optional fourth reading after Altitude;
it does not become a separate card, a permission, a setting, a service, or a
dashboard-level failure state.

The screen remains the existing vertically scrollable dashboard. This design
covers the pressure row, its states, formatting, accessibility, and its
foreground-session presentation. Speed, vertical speed, altitude, GNSS,
course, horizon, nearby-city, map, and route behavior remain as they are.

## Inputs inspected

### Authoritative requirements

- `.ai/tasks/TASK-009.md`

### Rewrite implementation and theme

- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/FlightParametersCard.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt`
- `app/src/main/java/kniezrec/com/flightinfo/flight/FlightParametersState.kt`
- `app/src/main/java/kniezrec/com/flightinfo/flight/FlightParametersController.kt`
- `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/java/kniezrec/com/flightinfo/ui/permission/PermissionColors.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/theme/Theme.kt`
- `.ai/designs/TASK-008.md`

### Legacy behavior and visual references

- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/cards/gps/FlightParametersCardView.kt`
- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/cards/gps/FlightParametersCardViewPresenter.kt`
- `/home/ai-dev/smart-flight/app/src/main/res/layout/flight_parameters_card_layout.xml`
- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/avionic/calculators/Pressure.kt`
- `/home/ai-dev/smart-flight/app/src/main/res/values/colors.xml`
- `/home/ai-dev/smart-flight/app/src/main/res/values/dimens.xml`
- `/home/ai-dev/smart-flight/promo/promo.png` (1024×500 RGBA image; metadata inspected, preview was unavailable in this environment)

## Behavior classification

### Observed legacy behavior

- Pressure is the fourth row in the same Flight parameters card, after
  altitude.
- The legacy sensor callback supplies Android barometric pressure directly in
  millibars and the default formatter displays one decimal place followed by
  `mbar`.
- The legacy card starts each value with a dash and updates the pressure value
  independently from location values.
- The legacy implementation obtains pressure through a service/presenter
  contract and has preference-driven `mbar`/`inHg` formatting.

### Required behavior from TASK-009

- Show `Pressure` after `Altitude` in the authorized foreground dashboard.
- Accept only finite, non-negative sensor values. Format valid values with one
  locale-aware decimal place and the compact visible unit `mbar`.
- Show the established dash placeholder before the first valid value and when
  pressure hardware is absent. Speak the placeholder as `unavailable`.
- Ignore invalid callbacks. Once a session has shown a valid pressure, retain
  that value through later invalid callbacks until the session ends.
- Register and unregister the optional pressure observation only with the
  authorized foreground session. A new foreground session starts unavailable,
  and callbacks from an older session have no visual effect.
- Do not register pressure from permission onboarding, add a permission, add a
  preference, or make pressure failure invalidate another dashboard card.

### Design decisions

- Pressure is modeled as a field of the existing flight-parameters state, not
  as an independent composable card or an additional dashboard section.
- The existing Flight parameters card remains the owner of the row layout and
  its purple surface. This keeps the four values visually grouped as one
  aircraft-state summary.
- The card’s reading content is allowed to grow with the fourth row and text
  scale. The legacy fixed-height constraint is not carried into the rewrite.
- Pressure availability is deliberately local to the row. There is no card
  error, retry button, sensor explanation, or replacement message when a phone
  lacks a barometer, because the task defines missing hardware as a normal
  unavailable state.
- Formatting is a pure presentation rule: pressure is not converted, rounded
  for storage, or passed through the legacy preference calculator. Android’s
  pressure value is treated as mbar/hPa as specified by the task.

## Dashboard placement and layout

The authorized dashboard retains its current single-column order and 12dp
horizontal gutter. The Flight parameters card remains immediately after the
GNSS card, centered within the existing 600dp maximum width. Its internal
reading order is:

1. `Speed`
2. `Vertical speed`
3. `Altitude`
4. `Pressure`

The pressure row uses the same structure as the existing rows:

- full-width logical-start label;
- flexible value area aligned to the logical end;
- minimum 48dp row height;
- existing 4dp inter-row rhythm;
- existing card padding and title treatment.

The row is informational and has no click, swipe, or toggle affordance. Do not
add a pressure icon: the visible label carries the meaning and avoids adding
an inconsistent fourth visual language to an otherwise text-based card.

The card continues to use the established purple surface, rounded shape,
elevation, lavender text, and title `Flight parameters`. The implementation
must not retain a fixed height that clips the fourth row; a minimum height may
remain for visual consistency, but content and accessibility sizing determine
the final height.

### Reading states

| State | Card presentation | Pressure value |
| --- | --- | --- |
| Permission onboarding | Flight parameters card is not composed | No pressure row or registration |
| Authorized session, existing card waiting for a GPS position | Preserve the existing card waiting treatment | No fabricated pressure value; when the reading rows are shown, pressure is `—` |
| Authorized session, reading content shown, before first valid pressure | Existing four-row reading layout | `—`, spoken as `unavailable` |
| Valid pressure received | Existing four-row reading layout | Locale-aware one decimal plus `mbar`, e.g. `1013.3 mbar` in English |
| Invalid event before a valid event | Four-row reading layout remains unchanged | `—` |
| Invalid event after a valid event | Four-row reading layout remains unchanged | Retain the last valid value |
| No `TYPE_PRESSURE` sensor or registration failure | Existing four-row reading layout remains usable | `—`, spoken as `unavailable`; other rows/cards remain unaffected |
| Foreground session stopped/restarted or permission lost | Existing lifecycle reset/waiting behavior | Clear pressure; next session starts at `—` |

The existing GPS waiting treatment remains authoritative for the card’s
location-driven waiting state; pressure must not fabricate a location reading
or disturb the current speed/vertical-speed/altitude transition. Once the
card is rendering reading rows, the pressure row is always present, including
when its only state is unavailable.

## Pressure value presentation

- Visible label: localized `Pressure`.
- Visible valid value: localized decimal number with exactly one fractional
  digit, followed by a localized resource-backed compact unit string `mbar`.
- Visible unavailable value: the existing `—` placeholder, never `0`, `NaN`,
  infinity, an empty string, or a sensor error message.
- Validity gate: reject negative values and all non-finite values before they
  reach Compose-visible state.
- Retention: an invalid event cannot clear or replace a valid value within
  the active session.
- Reset: stopping the session clears the pressure value and any availability
  flag; restarting creates a fresh unavailable state.

Use the same locale-aware number-formatting approach as the existing flight
rows. Keep the compact visual unit separate from the accessibility expansion:
TalkBack should hear `millibars` where the unit is spoken, rather than relying
on pronunciation of `mbar`.

All visible labels, units, placeholders, and accessibility text must be
resource-backed. The pressure row must not embed user-visible English strings
in the composable or controller.

## Lifecycle and state interaction

The design expects one small, testable platform/controller boundary around
`SensorManager` and `Sensor.TYPE_PRESSURE`, coordinated with the existing
foreground flight observation lifecycle. The pressure boundary owns the exact
listener instance it registers and reports state on the UI-safe executor.

Start pressure observation only when the authorized dashboard begins its
active foreground session. Stop it when the activity pauses, loses fine
location, is destroyed, or otherwise stops the active observation. Hardware
absence and registration failure produce the row’s unavailable state, not a
global GNSS or dashboard error.

Session identity must be part of callback acceptance. On stop, invalidate the
active session before unregistering; a callback captured from an old session
must be ignored even if it arrives after unregistration. A fresh start clears
the previous pressure value before accepting new events. Pressure must not add
a second location listener or alter the existing location-fix fan-out used by
course, nearby city, map, or route behavior.

## Accessibility

- Merge each row’s label and value as one semantic reading, following the
  current card pattern: `Pressure, 1013.3 millibars` or `Pressure, unavailable`.
- The dash is visual shorthand only; its spoken equivalent is the existing
  localized unavailable string.
- Do not mark every sensor update as an interruptive live-region event. The
  existing card-level availability announcement may remain, but a changing
  numeric pressure reading should not repeatedly steal focus or interrupt
  speech.
- Keep the row at least 48dp high even though it is not interactive, so it is
  comfortably readable and consistent with neighboring rows.
- At large font scales, permit the label and value to wrap and let the card
  grow vertically. Do not truncate, overlap, or hide `mbar`.
- Use logical start/end alignment so the label/value relationship mirrors in
  RTL. Do not use absolute left/right positioning.

## Responsive and orientation behavior

### Portrait

Use the existing vertically scrollable dashboard and the same card width,
gutter, padding, and row rhythm. The card expands naturally for four rows and
large text. The dashboard remains scrollable so the taller card does not push
later cards off-screen permanently.

### Landscape and wider windows

Retain the centered 600dp maximum card width and the same four-row order. Do
not introduce a second pressure column or a landscape-only card. If the
available height is constrained, the outer dashboard scroll remains the
escape hatch; no row is clipped to preserve a legacy 160dp card height.

### Font scale and RTL

Use resource-backed text and natural wrapping at large font scales. Values
remain end-aligned while labels use the logical start edge. Verify the full
`Pressure` label, the dash, and a representative long localized number/unit
combination at high font scale and in RTL.

## Intentional deviations from the legacy application

- The rewrite keeps pressure in the same modern Compose card but removes the
  legacy service/presenter dependency and preference-driven unit selection;
  TASK-009 explicitly fixes the initial unit to `mbar` and defers settings.
- The legacy card uses a fixed 160dp ConstraintLayout. The rewrite lets the
  card grow for the fourth row, large text, wrapping, and accessibility rather
  than risk clipping.
- The legacy card uses a dash as a purely visual default. The rewrite keeps the
  dash visually for continuity but provides an explicit localized `unavailable`
  accessibility value.
- The rewrite rejects invalid, negative, NaN, and infinite sensor values and
  retains the last valid value after later invalid callbacks, as required by
  the task. The legacy callback path does not define these safeguards.
- The rewrite treats missing pressure hardware as a local row state and binds
  observation to the authorized foreground session. It does not reproduce the
  legacy `SensorService` or its broader service lifecycle.
- The promo image was used only as an available visual-reference resource;
  exact card behavior and sizing are taken from the inspectable layout and
  resource files, not from recreating the promo composition.

## Developer verification checklist

Verify the design through focused tests and visual checks for:

- row order: Speed, Vertical speed, Altitude, Pressure;
- valid `1013.25` formatting as `1013.3 mbar` in an English locale and
  equivalent locale-aware decimal separators elsewhere;
- placeholder before the first valid event, after session restart, and for a
  device without a pressure sensor;
- rejection of NaN, positive/negative infinity, negative values, and other
  invalid events;
- retention of the last valid value after invalid events in the same session;
- registration only in the authorized foreground session, exact unregistration,
  reset on stop/restart, and stale-callback rejection;
- registration failure isolation from the other dashboard cards;
- resource-backed labels, unit, placeholder, and expanded accessibility text;
- 48dp row minimum, large-font wrapping, RTL mirroring, and no clipping in
  portrait and landscape;
- unchanged location, GNSS, course, horizon, nearby-city, map, and route
  behavior, including no additional location listener.

## Unresolved questions

None. TASK-009 explicitly establishes `mbar`, the row order, optional hardware
behavior, lifecycle, invalid-value handling, and the accessibility/content
constraints needed for implementation.
