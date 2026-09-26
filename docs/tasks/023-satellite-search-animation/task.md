# TASK-023 — Searching-for-GPS animation and alternating "Move device closer to the window" tip

## Goal
While waiting for satellites, show an animation and alternate the waiting text with the original tip every 10 seconds.

## Context
- Parity finding 19 (MISSING, LOW): original `~/smart-flight/app/src/main/java/kniezrec/com/flightinfo/cards/satellites/NoSatellitesFoundView.kt:23-35` alternates `wait_for_gps` ("Waiting for GPS signal…") and `gps_tip` ("Move device closer to the window") every 10 s, with a Lottie animation `assets/loading.json` (`layout/no_satellites_layout.xml:9-31`). Rewrite: static text in the GNSS card waiting state.
- Planner decision: implement the animation with Compose (e.g. pulsing/rotating satellite or radar-sweep drawn in `Canvas` using theme colors) instead of adding the Lottie dependency. Reason: one small animation does not justify a new library; "every abstraction must justify its existence". If the human prefers the exact original animation, porting `loading.json` with `lottie-compose` is a drop-in alternative (see README open questions).

## Dependencies
- TASK-022.

## Original app reference
- `cards/satellites/NoSatellitesFoundView.kt`, `layout/no_satellites_layout.xml`, `assets/loading.json`, strings `wait_for_gps`, `gps_tip` (+ `values-pl`).

## Scope
- Waiting state of the GNSS card: animation + text alternating every 10 s between the waiting text and the tip (`gps_tip` string added).
- Alternation driven by `LaunchedEffect` with `delay(10_000)`; stops when leaving the state; respects "remove animations" accessibility setting (static when animator duration scale is 0).
- The live region must not re-announce every 10 s (only the first text is polite-announced).

## Out of scope
- Other GNSS states.

## Requirements
Required:
- Text alternates every 10 s; animation visible while waiting; no new third-party dependency unless the human chooses Lottie.

## Acceptance criteria
- [ ] Compose test with `mainClock.advanceTimeBy(10_000)`: text switches to the tip and back — verified by: CI unit test
- [ ] No Lottie dependency added (unless decided otherwise) — verified by: code review
- [ ] Animation looks smooth and on-brand — verified by: HUMAN on device

## Tests to add or update
- `GnssStatusCardTest` waiting-state cases.

## Risks and edge cases
- Infinite animations in Compose tests: disable auto-advance or use `mainClock.autoAdvance = false` to avoid test hangs.
