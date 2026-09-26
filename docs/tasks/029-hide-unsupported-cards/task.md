# TASK-029 — Offer to hide the Course/Horizon card when the device lacks the sensor, and a "Show hidden cards" setting

## Goal
When the compass or horizon cannot work on this device, show the original choice "This device doesn't have … Hide this card?" and, if accepted, hide the card persistently. Add a "Show hidden cards" setting that brings hidden cards back.

## Context
- Parity finding 18 (MISSING, LOW): original blurred overlay with message `missing_sensor_content` ("This device doesn't have magnetic sensor. Hide this card?") and a `hide` button (`~/smart-flight/app/src/main/java/kniezrec/com/flightinfo/cards/overlay/BlurUtils.kt:27-38`, `layout/card_overlay_layout.xml`, `CourseCardView.kt:44-64`, `HorizonCardView.kt:63-72`), persisted with `is_course_card_hidden` / `is_horizon_card_hidden` in SharedPreferences `LocalPrefs` (`settings/FlightAppPreferences.kt:112-134`). Hidden cards are not added (`cards/adapter/CardViewContainer.kt:43-55`).
- Rewrite: a permanent "Compass unavailable" / "Horizon unavailable" card (`CourseCard.kt:72`, `HorizonCard.kt:67` before moves).
- The original offered no way to unhide. Human decision: add a "Show hidden cards" setting.
- Human decision: no import of the original app's `LocalPrefs` values (no Play Store update release), so the new keys start empty.
- After TASK-015, Settings is driven by `SettingsViewModel` over settings repositories; after TASK-026 availability rules are final (`Unavailable` = sensor missing).

## Dependencies
- TASK-026 (final availability rules), TASK-028, TASK-006 (settings repository pattern), TASK-015 (Settings ViewModel).

## Original app reference
- `cards/overlay/BlurUtils.kt`, `cards/overlay/OverlayView.kt`, `layout/card_overlay_layout.xml`, `cards/course/CourseCardView.kt`, `cards/horizon/HorizonCardView.kt`, `settings/FlightAppPreferences.kt`, strings `missing_sensor_content`, `hide`.

## Scope
- `CardVisibilityRepository` (SharedPreferences file `display_behavior` or a new `card_visibility`; keys `card_hidden_course`, `card_hidden_horizon`): `hiddenCards: StateFlow<Set<HideableCard>>`, `suspend fun hide(card)`, `suspend fun showAll()`.
- Unavailable state of Course/Horizon cards: blurred/dimmed placeholder (Compose `Modifier.blur` on API 31+ over a static preview of the instrument, dimmed with the `overlay50` token) with the message and a "Hide" button; separate messages for compass (magnetic sensor) and horizon (motion sensor). Colors from palette tokens (TASK-018): message `valueText`, button text `accent`.
- Dashboard omits hidden cards (their ViewModels are not created).
- Settings: a "Show hidden cards" row in the display section. Subtitle lists the hidden cards ("Compass, Horizon") or "No hidden cards"; the row is disabled when nothing is hidden. Tapping calls `showAll()`; the cards reappear on the dashboard immediately (a card whose sensor is still missing shows the "Hide this card?" placeholder again). Show a short confirmation (snackbar or the subtitle change is enough; pick one and justify).
- New strings (English here; Polish in TASK-036).

## Out of scope
- Hiding any other card (the original only offered it for sensor-dependent cards).

## Requirements
Required:
- Hide choice persists across launches; hidden cards are not composed.
- "Show hidden cards" restores all hidden cards and persists that.
- Only `Unavailable` (sensor missing) offers hiding, never a transient `Error`.

## Acceptance criteria
- [ ] Repository tests (default nothing hidden, hide persists, showAll clears, change emission) — verified by: CI unit test
- [ ] Compose test: unavailable course card shows message + Hide; clicking removes the card — verified by: CI unit test
- [ ] Compose/ViewModel test: Settings row disabled with nothing hidden; with a hidden card it shows its name, and clicking it brings the card back on the dashboard — verified by: CI unit test
- [ ] Settings shows the "Show hidden cards" row (disabled, "No hidden cards") in the palette — verified by: HUMAN on device
- [ ] Unavailable overlay, hiding and restoring look right — verified by: HUMAN on a device without magnetometer/rotation sensor if one is available; otherwise the human reviews the overlay's `@Preview` and this flow is covered by the CI tests only (state which in the PR)

## Tests to add or update
- `CardVisibilityRepositoryTest`, `CourseCardTest`, `HorizonCardTest`, `DashboardScreenTest`, `UnitSettingsScreenTest`/`SettingsViewModelTest`.

## Risks and edge cases
- A device that temporarily reports registration failure (`Error` state) must not offer hiding; only `Unavailable` (sensor missing) does.
- Hidden + later sensor available (e.g. after an OS update): the card stays hidden until the user uses "Show hidden cards"; acceptable.
