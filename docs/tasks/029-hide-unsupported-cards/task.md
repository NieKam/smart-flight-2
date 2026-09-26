# TASK-029 — Offer to hide the Course/Horizon card when the device lacks the sensor

## Goal
When the compass or horizon cannot work on this device, show the original choice "This device doesn't have … Hide this card?" and, if accepted, hide the card permanently.

## Context
- Parity finding 18 (MISSING, LOW): original blurred overlay with message `missing_sensor_content` ("This device doesn't have magnetic sensor. Hide this card?") and a `hide` button (`~/smart-flight/app/src/main/java/kniezrec/com/flightinfo/cards/overlay/BlurUtils.kt:27-38`, `layout/card_overlay_layout.xml`, `CourseCardView.kt:44-64`, `HorizonCardView.kt:63-72`), persisted with `is_course_card_hidden` / `is_horizon_card_hidden` in SharedPreferences `LocalPrefs` (`settings/FlightAppPreferences.kt:112-134`). Hidden cards are not added (`cards/adapter/CardViewContainer.kt:43-55`).
- Rewrite: a permanent "Compass unavailable" / "Horizon unavailable" card (`CourseCard.kt:72`, `HorizonCard.kt:67` before moves).
- The original offered no way to unhide. Planner decision: follow the original (no unhide UI). README open question asks whether a "Show hidden cards" setting is wanted.
- TASK-036 migrates the original `is_course_card_hidden`/`is_horizon_card_hidden` values into the keys defined here.

## Dependencies
- TASK-026 (final availability rules), TASK-028, TASK-006 (settings repository pattern).

## Original app reference
- `cards/overlay/BlurUtils.kt`, `cards/overlay/OverlayView.kt`, `layout/card_overlay_layout.xml`, `cards/course/CourseCardView.kt`, `cards/horizon/HorizonCardView.kt`, `settings/FlightAppPreferences.kt`, strings `missing_sensor_content`, `hide`.

## Scope
- `CardVisibilityRepository` (SharedPreferences file `display_behavior` or a new `card_visibility`; keys `card_hidden_course`, `card_hidden_horizon`), Flow + suspend setter.
- Unavailable state of Course/Horizon cards: blurred/dimmed placeholder (Compose `Modifier.blur` on API 31+ over a static preview of the instrument) with the message and a "Hide" button; separate messages for compass (magnetic sensor) and horizon (motion sensor).
- Dashboard omits hidden cards.

## Out of scope
- Unhide UI (open question).

## Requirements
Required:
- Hide choice persists across launches; hidden cards are not composed (their ViewModels not created).

## Acceptance criteria
- [ ] Repository tests (default visible, persist hidden) — verified by: CI unit test
- [ ] Compose test: unavailable course card shows message + Hide; clicking removes the card — verified by: CI unit test
- [ ] Looks acceptable on a device without the sensor (or emulator with sensors disabled) — verified by: HUMAN on device

## Tests to add or update
- `CardVisibilityRepositoryTest`, `CourseCardTest`, `HorizonCardTest`, `DashboardScreenTest`.

## Risks and edge cases
- A device that temporarily reports registration failure (`Error` state) must not offer hiding; only `Unavailable` (sensor missing) does.
