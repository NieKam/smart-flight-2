# TASK-020 — Flight parameters: pressure without GPS, smoothed vertical speed

## Goal
Show barometric pressure as soon as the sensor reports it, independent of GPS, and smooth vertical speed with the original 3-sample moving average.

## Context
- Parity finding 4 (REGRESSION, MEDIUM): original sends pressure straight from the sensor to the card with or without GPS (`~/smart-flight/app/src/main/java/kniezrec/com/flightinfo/cards/gps/FlightParametersCardViewPresenter.kt:102-113`). Rewrite merges pressure only into `FlightParametersState.Readings` (before migration: `MainActivity.kt:519-523`; after TASK-009: in `FlightParametersViewModel`); while `Waiting`, the card shows only "Waiting for GPS position…" (`FlightParametersCard.kt:81-83` before moves).
- Parity finding 16 (REGRESSION, LOW): original 3-sample moving average (`~/smart-flight/.../avionic/calculators/VerticalSpeedCalculator.kt:16,49-52`, `AverageCalculator.kt`); rewrite uses the raw delta between two fixes (`FlightParametersController.kt:95-112`, pure function kept by TASK-009). First sample: original "+0.0", rewrite "—".
- Parity MODERNIZATION (accepted, keep): ft/min factor fixed (`displayunits/UnitPresentation.kt:37`, correct 196.85; original divided by 1000); sample timing in nanoseconds; units correct.
- Architecture review K2 (KEEP): vertical-speed computation stays a pure, tested function.

## Dependencies
- TASK-016.

## Original app reference
- `cards/gps/FlightParametersCardViewPresenter.kt`, `cards/gps/FlightParametersCardView.kt`, `layout/flight_parameters_card_layout.xml`, `avionic/calculators/VerticalSpeedCalculator.kt`, `AverageCalculator.kt`.

## Scope
- `FlightParametersState`: allow pressure without GPS readings (e.g. `Readings` with nullable speed/altitude/vertical speed, or a separate `pressureMillibars` field alongside the GPS part). Card layout: rows for speed, vertical speed, altitude show "—" until GPS; the pressure row shows the value as soon as available. Keep the current rendering of the pressure row when no value exists (today `FlightParametersCard` renders the row from `state.pressureMillibars?.let { … }`; check and keep whatever it shows for `null`).
- Also: the card must show the readings layout (not the "Waiting for GPS position…" layout) as soon as either a GPS reading or a pressure value exists.
- Vertical speed: moving average over the last 3 computed deltas (same window as original); reset on observation restart and on non-monotonic timestamps (as today).
- First value decision (planner): keep "—" until a first delta exists (the rewrite's behavior). Reason: showing "+0.0" claims a measurement that does not exist; this is a presentation detail, not a feature. List in PR.

## Out of scope
- Unit conversion changes.

## Requirements
Required:
- Pressure visible without GPS fix on devices with a barometer.
- Vertical speed averaged over 3 samples.
- ft/min conversion stays correct (behavior change vs original already accepted; mention in PR as retained).

## Acceptance criteria
- [ ] ViewModel test: pressure emitted before any fix is in state — verified by: CI unit test
- [ ] Pure function tests: moving average of 3, window reset, first value null, non-monotonic reset — verified by: CI unit test
- [ ] Compose test: card with only pressure shows pressure row and "—" for GPS rows — verified by: CI unit test
- [ ] TASK-004 scenario 5 updated to the new behavior — verified by: CI unit test
- [ ] On a device with a barometer indoors, pressure shows before GPS fix — verified by: HUMAN on device

## Tests to add or update
- `FlightParametersViewModelTest`, `VerticalSpeedTest`, `FlightParametersCardTest`, characterization scenario 5.

## Risks and edge cases
- Accessibility live region should not announce pressure changes every sensor event (throttle or mark polite as today).
