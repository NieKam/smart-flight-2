# TASK-027 — Compass rose: N/E/S/W letters around a rotating airplane, animated

## Goal
Restore the Course card's recognizable instrument: a rose with the letters N, E, S, W around an airplane silhouette that rotates to the heading with a short animation taking the shortest path.

## Context
- Parity finding 6 (VISUAL_MISMATCH, MEDIUM): original `~/smart-flight/app/src/main/res/layout/course_card_layout.xml:48-94` — airplane `plane_icon` (#d9d9ed) with letters N/W/S/E (`CompassLetter` style: 28sp, `text_color_dark`) around it; rotates with a 200 ms linear animation (`cards/course/CourseCardView.kt:66-82`) by the heading (`CourseCardViewPresenter.kt:107`: `rotatePlane(course.azimuth)`), letters fixed (north up). Left side: cyan abbreviation (e.g. "W") and large "271°"; GPS bearing line below. Screenshot `~/smart-flight/promo/screen-1.png`.
- Rewrite: a 72dp outline circle with a three-line arrow, no letters, no animation (`CourseCard.kt:234-260` before moves); rotation jumps, including across 359°→0°.
- Architecture review K4 (KEEP): the card stays stateless (animation state is UI-only).

## Dependencies
- TASK-026, TASK-018.

## Original app reference
- `layout/course_card_layout.xml`, `res/drawable/plane_icon.xml`, `cards/course/CourseCardView.kt`, `values/styles.xml` (`CompassLetter`), `values/dimens.xml` (`compass_letter`, `compass_letter_font_size`), `promo/screen-1.png`.

## Scope
- Import `plane_icon.xml` as a vector drawable (or draw an equivalent path) tinted with the value text token.
- Layout per screenshot: left column (cardinal abbreviation in accent, heading in large text, GPS bearing line), right: rose with N/E/S/W in label color around the plane. Adapt to narrow widths (stack vertically below ~360dp).
- Animation: `animateFloatAsState` (200 ms, linear) on an unwrapped target angle computed by a pure `shortestRotationTarget(current, newHeading)` so 359→1 rotates +2°, not −358°.
- Accessibility: the rose is decorative (content description on the heading text only, as today).

## Out of scope
- Sensor logic.

## Requirements
Required:
- Letters N/E/S/W fixed; plane rotates by heading; shortest-path 200 ms animation.

## Acceptance criteria
- [ ] `shortestRotationTarget` tests (0→350 = −10, 350→10 = +20, repeated wraps accumulate) — verified by: CI unit test
- [ ] Compose test: letters N/E/S/W present; heading text and abbreviation shown — verified by: CI unit test
- [ ] Matches `promo/screen-1.png` course card — verified by: HUMAN on device

## Tests to add or update
- `CompassRotationTest`, `CourseCardTest`.

## Risks and edge cases
- Heavy recomposition at sensor rate: keep rotation in `graphicsLayer { rotationZ = … }` to avoid relayout.
