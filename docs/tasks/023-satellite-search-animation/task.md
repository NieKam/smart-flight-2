# TASK-023 — Original searching-for-GPS Lottie animation and alternating "Move device closer to the window" tip

## Goal
While waiting for satellites, show the original app's Lottie animation (`loading.json`) and alternate the waiting text with the original tip every 10 seconds.

## Context
- Parity finding 19 (MISSING, LOW): original `~/smart-flight/app/src/main/java/kniezrec/com/flightinfo/cards/satellites/NoSatellitesFoundView.kt:23-35` alternates `wait_for_gps` ("Waiting for GPS signal…") and `gps_tip` ("Move device closer to the window") every 10 s, with a Lottie animation `~/smart-flight/app/src/main/assets/loading.json` (`layout/no_satellites_layout.xml:9-31`). Rewrite: static text in the GNSS card waiting state.
- Human decision: use the original Lottie file; adding `lottie-compose` is allowed. Check the current version rather than assuming it.

## Dependencies
- TASK-022.

## Original app reference
- `cards/satellites/NoSatellitesFoundView.kt`, `layout/no_satellites_layout.xml` (size, loop and placement of the `LottieAnimationView`), `assets/loading.json`, strings `wait_for_gps`, `gps_tip` (+ `values-pl`).

## Scope
- Dependency: `com.airbnb.android:lottie-compose` in the version catalog. Look up the current stable version on Maven Central / the Lottie GitHub releases, check its release notes for the minimum Compose version and that it works with the project's Compose BOM, and record version + source in the PR description.
- Copy `~/smart-flight/app/src/main/assets/loading.json` unchanged into the app (`app/src/main/res/raw/loading.json` with `LottieCompositionSpec.RawRes`, or `assets/` with `LottieCompositionSpec.Asset`; pick one).
- Waiting state of the GNSS card: `LottieAnimation` looping forever (`iterations = LottieConstants.IterateForever`), sized and placed as in `no_satellites_layout.xml`, plus text alternating every 10 s between the waiting text and the tip (`gps_tip` string added).
- Keep the animation's own colors (they are the original's). Text colors from palette tokens (TASK-018): waiting text/tip `labelText` or `valueText` as in the original layout.
- Alternation driven by `LaunchedEffect` with `delay(10_000)`; stops when leaving the state.
- Respect the system "remove animations" setting: when the animator duration scale is 0, show the first frame (static), no looping.
- The live region must not re-announce every 10 s (only the first text is polite-announced); the animation is decorative (no content description).

## Out of scope
- Other GNSS states.

## Requirements
Required:
- Original `loading.json` animation plays while waiting; text alternates every 10 s.
- Only `lottie-compose` is added (no other new third-party dependency).

## Acceptance criteria
- [ ] Compose test with `mainClock.autoAdvance = false` and `mainClock.advanceTimeBy(10_000)`: text switches to the tip and back — verified by: CI unit test
- [ ] Compose test: waiting state contains the animation node (test tag) and it is absent in other states — verified by: CI unit test
- [ ] `loading.json` is byte-identical to the original file — verified by: code review (checksum in the PR description)
- [ ] `lottie-compose` version, source and Compose compatibility recorded in the PR — verified by: code review
- [ ] Animation plays smoothly and looks like the original — verified by: HUMAN on device

## Tests to add or update
- `GnssStatusCardTest` waiting-state cases.

## Risks and edge cases
- Infinite animations in Compose tests: disable auto-advance to avoid test hangs; under Robolectric the composition may load asynchronously — assert on the container node, not on rendered frames.
- APK size grows by the Lottie library; acceptable (human decision).
