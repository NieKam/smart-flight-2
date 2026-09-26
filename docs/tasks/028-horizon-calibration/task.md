# TASK-028 — Horizon: calibration kept across pause/resume, long-press reset to absolute, input filtering

## Goal
Restore the original horizon calibration semantics: calibrate once (first sample) and on "Calibrate", keep the reference while the app is paused/resumed or rotated, reset to absolute zero on long-press, and smooth the input with the original low-pass filter.

## Context
- Parity finding 17 (REGRESSION, LOW):
  - Original: `~/smart-flight/app/src/main/java/kniezrec/com/flightinfo/cards/horizon/HorizonCardViewPresenter.kt:29-30,70-73` captures the reference pitch on the first sample after the card attaches and keeps it across pause/resume; `onCalibrateClicked` (`:82-84`) re-captures on the next sample; long-press on Calibrate (`HorizonCardView.kt:35`, presenter `:90-93`) sets the reference to 0 (absolute). Input is low-pass filtered (`avionic/calculators/Filter.kt`, α = 0.25, applied to rotation-vector values in `OrientationCalculator.kt:24`).
  - Rewrite: the reference was cleared on every `stop()` (pause) and re-captured on resume (`horizon/HorizonController.kt:43-49`; preserved by TASK-010 as "reset when collection restarts"); no long-press; no filter.
- Parity MODERNIZATION (accepted, keep): the redesigned horizon (horizon moves behind a fixed aircraft symbol, pitch ladder, numeric readout — `HorizonCard.kt:128-231` before moves).
- TASK-010 added rotation-matrix tests; if it recorded a wrong roll/pitch sign in `docs/tasks/README.md` open questions, fix it here (see "Scope").

## Dependencies
- TASK-016.

## Original app reference
- `cards/horizon/HorizonCardViewPresenter.kt`, `HorizonCardView.kt`, `layout/horizon_card_layout.xml`, `avionic/calculators/Filter.kt`, `OrientationCalculator.kt`.

## Scope
- `HorizonViewModel`: reference pitch stored in the ViewModel (and `SavedStateHandle` so it survives process death) and NOT cleared when collection restarts. First sample ever (reference unset) captures it. `calibrate()` captures on next sample. New `resetToAbsolute()` sets reference = 0.0.
- `HorizonCard`: Calibrate button supports long-press (`combinedClickable`) → `resetToAbsolute()`; accessibility: add a custom action "Reset to level" so the long-press is discoverable for TalkBack.
- Filter: pure low-pass (α = 0.25) applied to pitch and roll (angle-aware for roll near ±180°) before mapping.
- If TASK-010 flagged a sign error in roll/pitch for some display rotation, fix it here with the failing test turned green.

## Out of scope
- Hiding the card (TASK-029).

## Requirements
Required:
- Pause/resume and rotation keep the calibration; long-press resets to absolute; filtered input.
- Behavior changes listed in PR.

## Acceptance criteria
- [ ] ViewModel tests: first-sample capture; persists across collection restart; calibrate; reset to absolute; restored from `SavedStateHandle` — verified by: CI unit test
- [ ] Filter tests (step response, roll wrap) — verified by: CI unit test
- [ ] Compose test: long-press on Calibrate triggers reset; accessibility custom action present — verified by: CI unit test
- [ ] Tilt phone, pause/resume: horizon does not re-level — verified by: HUMAN on device

## Tests to add or update
- `HorizonViewModelTest`, `HorizonFilterTest`, `HorizonCardTest`.

## Risks and edge cases
- Filter latency makes the horizon feel sluggish; α = 0.25 at `SENSOR_DELAY_UI` matches the original.
