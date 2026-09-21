# TASK-004 — Live compass course card design specification

## Scope and relationship to existing UI

Add exactly one foreground-only `Course` card as the third card on the fine-location dashboard, immediately after `GNSS status` and `Flight parameters`. It presents the current magnetic/device heading when an orientation source is usable and an optional, separately identified bearing from the same current foreground GPS-fix session.

TASK-001 onboarding remains the complete granted-screen replacement when fine permission is absent, approximate-only, denied, or revoked. TASK-002 GNSS content and recovery actions and TASK-003 flight-parameter states remain unchanged. The Course card is never visible in onboarding and it must not keep compass or bearing observation active outside the eligible foreground dashboard.

## Inputs and distinctions

### Required behavior from TASK-004

- The primary value is a magnetic/device compass heading: normalized to integer `0`–`359`, where `0` is north. It is not true north, GPS course, or aircraft track.
- The corresponding eight-point abbreviation is `N` for `338–359` and `0–22`, `NE` `23–67`, `E` `68–112`, `SE` `113–157`, `S` `158–202`, `SW` `203–247`, `W` `248–292`, and `NW` `293–337`.
- A newly foregrounded eligible session starts in waiting, with no retained course or bearing. A compass reading becomes available only after a valid current-session orientation callback.
- GPS bearing is supplementary and comes only from an explicitly bearing-bearing current foreground location fix. A no-bearing current fix clears its value. Valid bearing uses the same `0`–`359` convention.
- When heading hardware is absent/unusable, show `Compass unavailable` and an explanation; do not register sensors. When supported hardware cannot register or subsequently fails, show `Unable to read compass` with `Try again`.
- Error retry clears course and bearing, re-evaluates availability, and operates only while the dashboard is foregrounded. All foreground/session reset conditions clear both values before new callbacks.

### Observed legacy behavior

- The permission-gated legacy dashboard places its course card near the start of the card stack. Its fixed-height purple surface shows a large numeric course, a cyan cardinal abbreviation, a centered rotating plane with N/E/S/W letters, and `From GPS: N°` after a bearing-equipped fix.
- The legacy course card obtains compass and GPS callbacks separately. It rotates a vector plane for each course update and overlays a sensor-missing prompt that lets users hide the card permanently.
- Legacy colors and dimensions match the rewrite's existing dashboard lineage: `#484685` page, `#5B5999` cards, `#D9D9ED` light text, cyan action/accent, 10 dp card radius, 4 dp elevation, and 12 dp card margins.

### Design decisions and intentional deviations

- Give the card a `Course` title and explicit waiting/unavailable/error states. The legacy unlabelled surface initially leaves ambiguous whether 0° is data; this design never presents 0°/N until measured.
- Retain the recognizable plane-and-compass cue, but make numeric degree and cardinal text the primary, always-readable course representation. The illustration is supportive, never the sole direction carrier.
- Replace legacy `From GPS` wording with `GPS bearing`. This is shorter, clearer, and makes the distinction from the device compass explicit.
- Do not use the legacy blur/hide-card overlay, persistence, service binding, or its continuous 200 ms rotation animation. Hiding avoids truthful unavailable feedback, while continuous visual motion is unnecessary and can be distracting.
- Use a content-expanding layout rather than legacy fixed geometry so translated text, RTL, landscape, and 200% font scale remain usable.
- Do not prescribe smoothing. A direct valid reading is the baseline; any later deterministic bounded filtering remains behaviorally identical in its displayed range/mapping and should be documented and tested in code.

## Visual system

Use the established TASK-002/003 dashboard shell without alteration:

- Page `#484685`; centered 56 dp-minimum `Smart Flight` header in `#D9D9ED`, 20 sp medium.
- One centered, scrollable column with 12 dp page gutters and 600 dp maximum width. The outer dashboard owns scrolling.
- Course card: `#5B5999`, 10 dp rounded corners, 4 dp elevation, 24 dp logical start/end and 20 dp vertical content padding. It has a 160 dp minimum height only where that supports centered static states and grows freely for content.
- Standard title: `#D9D9ED`, 22 sp / 28 sp, medium. Supporting labels and body: `#D9D9ED`, 18 sp / 25 sp. Primary heading is 40 sp / approximately 48 sp, medium, in the same light text token; cardinal abbreviation is 18 sp / 25 sp medium in the established cyan accent. The primary size preserves the legacy instrument hierarchy without creating an inaccessible new type system.
- `Try again` uses the current cyan text-action treatment: 48 dp minimum target, normal pressed feedback, and 2 dp cyan keyboard-focus outline. No other action appears in this card.
- The optional plane artwork is the legacy `plane_icon.xml` vector adapted only if Compose can render it directly. It stays `#D9D9ED`; no raster asset, icon library, or custom compass image is needed. The surrounding N/E/S/W letters are optional context only and must not compete with the textual heading.

## Screen structure

```text
Smart Flight shell (outer vertical page scroll)
├── centered 56 dp header: Smart Flight
└── centered column, max 600 dp
    ├── existing GNSS status card
    ├── existing Flight parameters card
    └── Course card
        ├── waiting: title + centered waiting message
        ├── available: title
        │   ├── primary compass/course reading: 123° + NE
        │   ├── plane/compass direction visual
        │   └── GPS bearing                         287° | —
        ├── unavailable: title + explanatory body
        └── error: title + explanatory body + Try again
```

The preceding Flight parameters card keeps its existing 12 dp bottom spacing; the Course card follows it without an additional doubled gap and has a 12 dp bottom page margin. It is the sole new card and has no inner scrolling region.

## Course-card states

### Waiting for current-session compass heading

- Render the `Course` title above `Waiting for compass heading…`; center the group horizontally and vertically within the 160 dp-minimum card, with 12 dp between title and body.
- Show no degree, abbreviation, visual orientation, or GPS-bearing row. In particular, do not expose a placeholder `0°`, `N`, or stale secondary bearing.
- This applies before the first valid orientation callback of every foreground session and immediately after pause/resume, permission loss, settings return, GNSS/location-service/hardware unavailability, location/sensor registration failure, and retry. Waiting may remain indefinitely on a device that has a sensor but delivers no reading.

### Available magnetic/device course

- Start with the left-aligned `Course` title. Below it, leave 16 dp before the primary content.
- On compact portrait widths, use a vertically stacked primary reading followed by the direction visual, then the GPS-bearing row. When width is comfortable and font scale is ordinary, the primary reading and 72 dp visual may share a responsive logical-start/logical-end row; neither may force the value or cardinal label to truncate. The layout switches to the stack when their minimum readable widths no longer fit.
- The primary reading is a paired, merged value: the localized integer degree plus degree symbol, for example `123°`, followed by cardinal abbreviation `NE`. Keep the pair visually adjacent (8 dp gap) and wrap them together where possible. It represents the device's magnetic heading.
- Direction visual: rotate the plane around its center to match the same displayed normalized heading. If cardinal letters are retained, place N/E/S/W outside the visual as low-emphasis static orientation references. Do not counter-rotate the text. The visual cannot replace `123° NE`.
- Beneath the primary group, present a full-width 48 dp-minimum label/value row: `GPS bearing` at logical start and the optional localized normalized degree at logical end. Until a current-session bearing-bearing fix is received—and after a current fix without bearing—show visible `—`.
- No GPS bearing, including a valid one, substitutes for an unavailable compass heading. The available layout is only entered after a valid compass heading.

Illustrative available content only:

```text
Course
123°  NE                         [plane pointing 123°]
GPS bearing                                        287°
```

### Compass unavailable

- Center `Compass unavailable` and `This device cannot provide a compass heading.` in the minimum-height card, using the same 12 dp title/body rhythm as other static cards.
- Do not show a bearing row, degree, direction visual, retry control, calibration/hide instruction, or last measurement. The explicit state is stable while the platform reports no usable source.

### Compass read error

- Center `Unable to read compass` and `Smart Flight could not start the compass.`; show `Try again` 12 dp below the body.
- Keep the card's center alignment and content-growing behavior. The error replaces every available/waiting subelement and clears the secondary bearing rather than leaving it to imply a substitute course.
- `Try again` retries only when the dashboard is active/foregrounded. If conditions now show absent hardware, transition to Compass unavailable; otherwise clear values and return to waiting while registration is attempted. No spinner or repeated automatic retry is shown.

## Interaction, lifecycle, and motion

- The Course card itself has a single direct interaction: `Try again` in the compass read-error state. Existing GNSS actions retain their own locations and semantics; do not duplicate location-settings or GNSS retry controls here.
- A heading update changes the degree, cardinal label, and plane direction as one atomic presentation update. GPS-bearing updates affect only the secondary row while a compass course is available.
- Static-state changes and first successful heading may use the rewrite's brief 150–200 ms crossfade/size transition. Plane rotation may use a short transition of at most 200 ms only when system animation removal/reduction is not requested; rapid sensor events should update to the latest target without queued decorative spins. No continuous animation, compass sweep, calibration animation, or loading indicator.
- On every teardown/restart, clear UI course and bearing synchronously before listener teardown/re-registration can yield new data. The UI must never visibly carry an old-session value through waiting, unavailable, error, or onboarding.
- Changes in display rotation are reflected in the next current-session heading; the visual and text update together. The visual uses the device's visible top in portrait and landscape, not a portrait-only interpretation.

## Accessibility and semantics

- Traversal order is title, then static state body/action or primary compass-course value, direction visual (decorative/cleared semantics), then GPS-bearing row. Do not leave removed-state semantics in the tree.
- Merge the primary degree/cardinal pair into one focus target. Its spoken wording must identify the source, for example `Compass heading, 123 degrees, northeast`—not merely `123 degrees NE`.
- Merge the GPS row into one focus target, for example `GPS bearing, 287 degrees`; when absent, `GPS bearing, unavailable`. The visual em dash must use an explicit resource-backed spoken unavailable value.
- Mark routine heading and bearing updates as non-live. Announce only the one-time transition from waiting to an available heading politely, for example `Compass heading available`. Static unavailable/error state changes may be polite announcements once; never announce each sensor callback.
- The plane/dial has no independent actionable meaning and should be excluded from TalkBack focus, or have a single redundant description only if the visual cannot be cleared. Do not expose individual N/E/S/W labels as separate traversal stops.
- `Try again` includes its visible action and an outcome hint such as `Retries compass`. Support TalkBack, keyboard, switch access, RTL, magnification, touch exploration, system gesture insets, and 200% font scale. Never rely on cyan color, plane orientation, or motion alone to communicate direction or failure.

## Responsive layout

- Portrait: sequential GNSS, Flight parameters, and Course cards in the one-column dashboard. The Course available state can use its balanced reading/visual row if both stay readable.
- Narrow widths, 200% font, or localized long strings: stack primary value/cardinal, then visual, then the GPS row; card height grows. The GPS value may wrap under its label within the row rather than overlap, clip, or horizontally scroll.
- Landscape, tablets, and expanded windows: retain the same single centered 600 dp column. Do not place dashboard cards side by side. A wider Course card may use the primary/visual row but does not become a full-width instrument panel.
- Short screens, expanded GNSS satellite lists, keyboard, or large text use outer-page scrolling. No max height is imposed on the Course card; all state text and `Try again` remain reachable.
- Use logical start/end alignment and directional plane rotation semantics that remain correct in RTL. Numeric degrees are localized through resources/locale-aware formatting; cardinal abbreviations remain their defined compass tokens unless localized resources provide an equivalent convention.

## Resource and implementation-facing copy

All user-visible strings, spoken templates, action hints, and degree/value templates are Android resources. Add resource-backed copy for:

- `Course`; `Waiting for compass heading…`; `Compass unavailable`; `This device cannot provide a compass heading.`; `Unable to read compass`; `Smart Flight could not start the compass.`; `Try again`; `Retries compass`.
- `GPS bearing`; visible unavailable marker `—`; spoken unavailable value `unavailable`.
- A localized integer-degree value template, a primary compass-heading spoken template, a GPS-bearing spoken template, and optional polite `Compass heading available` announcement. Use locale-aware integer number formatting before applying the degree template.

Compose receives presentation-safe state, never Android `Location` or sensor framework types. It distinguishes waiting, available heading plus nullable bearing, unavailable hardware, and retryable read failure. The platform/controller layer owns sensor choice, display-rotation compensation, normalization, sensor registration, location-fix bearing validity, foreground lifecycle, and reset behavior. It continues to use the existing single foreground location path; no second GPS listener, service, background observation, permission, notification, network request, or persistence is introduced.

## Verification checklist

- Fine permission with enabled location services, GNSS hardware, and supported orientation input renders exactly GNSS, Flight parameters, then Course in the centered dashboard column.
- A new foreground session renders `Course` / `Waiting for compass heading…` with no old heading, cardinal, visual orientation, or GPS bearing. A valid heading then renders the correctly normalized degree/cardinal and matching plane direction.
- Test every specified cardinal boundary (0, 22, 23, 67, 68, 112, 113, 157, 158, 202, 203, 247, 248, 292, 293, 337, 338, 359) and values requiring normalization.
- GPS bearing is initially `—`; an explicit valid-bearing current fix updates it; the next current no-bearing fix returns it to `—` without altering a valid compass heading. Bearing never appears as primary course or in unavailable/error layouts.
- Absent orientation hardware renders the stable unavailable state with no action and no sensor listener. Registration/read failure renders the error and accessible `Try again`; retry clears values before safely reevaluating.
- Pause/resume, recreation/rotation, permission loss, system-settings return, location service disablement, absent GNSS hardware, observer failure, and retry reset values and do not duplicate sensor/location listeners. Existing GNSS/flight states/actions still behave unchanged.
- TalkBack reads meaningful paired primary and GPS values, does not announce every update, has explicit unavailable speech, and does not stop on decorative dial components. Keyboard/switch focus reaches retry and its visible focus indicator.
- Portrait, landscape, expanded windows, RTL, large text (200%), gesture insets, keyboard, and long static copy keep all content reachable without clipping/overlap.
- No hide-card control, calibration UX, true-north claim, map, route/city lookup, pressure/horizon instrument, background service/notification, persistence, network use, additional permission, raster asset, or third-party sensor/location dependency appears.

## Inspected references

- Authoritative task: `.ai/tasks/TASK-004.md`.
- Current rewrite: `MainActivity.kt`; `ui/gnss/GnssStatusScreen.kt`; `ui/gnss/FlightParametersCard.kt`; `flight/FlightParametersState.kt`; `flight/FlightParametersController.kt`; `flight/AndroidFlightLocationPlatform.kt`; `ui/theme/Color.kt`, `Type.kt`, and `Theme.kt`; `res/values/strings.xml`; and `ui/gnss/GnssStatusScreenTest.kt`.
- Legacy behavior and placement: `cards/adapter/CardViewContainer.kt`; `cards/course/CourseCardView.kt`; `cards/course/CourseCardViewPresenter.kt`; `services/SensorService.kt`; and `avionic/calculators/CourseCalculator.kt`.
- Legacy visual resources: `res/layout/course_card_layout.xml`, `res/drawable/plane_icon.xml`, `res/values/colors.xml`, and `res/values/dimens.xml`. `promo/promo.png` is present at 1024 × 500 but cannot be rendered in this workspace sandbox; the inspectable XML/resources were used as the visual source of truth.

## Open questions

None. TASK-004 explicitly defines the course as a foreground magnetic/device-heading instrument with an optional current-session GPS-bearing readout. True-north correction, GPS-track calculation, calibration UX, filtering policy, and user-controlled card visibility are outside this design increment.
