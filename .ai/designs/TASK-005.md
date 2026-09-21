# TASK-005 — Live attitude horizon card design specification

## Scope and product intent

Add one foreground-only `Horizon` card directly after `Course` on the precise-location dashboard. It communicates the device's display-relative pitch and roll as an informational, calibrated artificial horizon. It is not aircraft-certified telemetry, a GPS value, a compass replacement, or a location-service-dependent reading.

Fine-location onboarding remains the complete UI when fine location is absent, approximate-only, denied, or revoked. The Horizon card is intentionally gated by that established dashboard product decision, although its sensor data has no location dependency. It must never observe sensors in onboarding or while the dashboard is not foregrounded.

## Reference, requirements, and deliberate decisions

### Observed legacy behavior

- The legacy dashboard constructs Horizon immediately after Course. Its 160 dp purple surface has a center aircraft reference, symmetric side pitch ticks, a darkened lower-half ground cue, and a cyan all-caps calibration control.
- Its first pitch sample becomes the level baseline; tapping its visible control makes the next pitch sample a baseline. The original implementation also has an undocumented long press that resets to zero, an overlay/hide-card flow, a bound sensor service, fixed pixel translation, and inconsistent orientation naming.

### Required behavior from TASK-005

- A single Horizon card appears after Course only on the foreground, fine-location dashboard. It starts every foreground/retry/configuration session waiting for a current sample and does not retain calibration or an attitude image.
- The first valid current sample sets the pitch reference. Later relative pitch changes move the instrument vertically and roll rotates it; both responses are bounded. `Calibrate` returns to waiting and makes the next valid sample the new reference.
- The visual is display-relative in all four rotations. A rotation/configuration change resets to waiting and requires a fresh reference/sample.
- Unsupported hardware is a stable unavailable state without retry. Register/read failure is an error state with a safe foreground-only retry. No hide preference, long-press reset, service, persistence, background operation, location fix, or additional permission is introduced.

### Design decisions and intentional deviations

- Retain the legacy sky/ground, tick, and aircraft-reference visual intent, but use a Compose-resizable instrument rather than its fixed 160 dp, fixed-pixel transform layout. The card may grow beyond 160 dp for large text or constrained widths.
- A titled card and textual summary make measured state understandable even when the drawing is unavailable to a user. The legacy instrument had no title or verbal reading.
- The central aircraft reference remains fixed while the sky/ground horizon and tick field move/rotate behind it. This more clearly conveys attitude and avoids moving the reference cue itself. This is an intentional visual interpretation of the task's allowed “horizon/aircraft” response, not a copy of the legacy plane translation.
- Calibration is a normal accessible text action with a 48 dp target; the undocumented long-press zero reset and legacy hide/blur overlay are omitted. No decorative continuous animation is used.
- Exact degree labels are included in the textual summary (rounded to whole degrees for display) because the task permits pitch/roll wording and requires state not be carried by graphics alone. They describe relative calibrated pitch and display-relative roll, not a claim of aircraft attitude accuracy.

## Shared visual system

Continue the existing dashboard unchanged: `#484685` page background, centered 56 dp minimum `Smart Flight` header in `#D9D9ED`, edge-to-edge safe insets, and one vertically scrollable centered column with 12 dp page gutters and a 600 dp maximum width. The preceding Course card supplies its 12 dp bottom gap; Horizon occupies the next card position and leaves 12 dp below itself.

Horizon uses the established card surface: `#5B5999`, 10 dp rounded corners, 4 dp elevation, logical start/end padding 24 dp and vertical padding 20 dp. Its minimum height is 160 dp only for compact/static states; content must grow rather than clip. Standard title is `#D9D9ED`, 22 sp / 28 sp, medium. Supporting and summary text is `#D9D9ED`, 18 sp / 25 sp. Cyan uses the established `#6CF0FF` action/accent token; text/action contrast must be verified on the card surface.

## Screen structure

```text
Smart Flight shell (outer vertical page scroll)
├── existing GNSS status card
├── existing Flight parameters card
├── existing Course card
└── Horizon card
    ├── waiting / recalibrating: title + centered state message
    ├── available: title
    │   ├── concise textual attitude summary
    │   ├── bounded attitude instrument
    │   │   ├── clipped sky / ground fields divided by horizon
    │   │   ├── symmetric pitch-scale tick cues
    │   │   └── fixed centered aircraft reference
    │   └── Calibrate text action
    ├── unavailable: title + explanatory body
    └── error: title + explanatory body + Try again text action
```

The card has no nested scroll container, app bar action, menu, hide control, or independent location/GNSS status. It follows the same static-state centering pattern as Course and GNSS.

## State designs

### Initial waiting and recalibrating

- Center `Horizon` above `Waiting for attitude data…`, with 12 dp between them. Use the minimum-height card and no instrument, zero/level placeholder, pitch/roll summary, or action.
- The same visible copy applies immediately after a new foreground session, retry, configuration/display rotation, permission loss followed by return, and tap on `Calibrate`. The state must not imply whether it is initial or recalibration before a measurement exists.
- Announce the transition to this waiting state only when it is caused by calibration; ordinary lifecycle/recomposition waiting is not announced. Suggested resource-backed polite text: `Horizon recalibrating. Waiting for attitude data.`

### Available attitude

- Place the left-aligned `Horizon` title first. Show the merged, single-focus textual summary 12 dp below it, followed by 12 dp to the instrument. Keep the instrument centered horizontally.
- Summary copy expresses calibrated pitch and display-relative roll with localized signed/absolute whole-degree values and direction words. Recommended visible patterns are `Pitch: 12° up · Roll: 8° right`, `Pitch: 7° down · Roll: level`, and `Pitch: level · Roll: 15° left`. The matching TalkBack template says `Horizon. Pitch 12 degrees up. Roll 8 degrees right.` Direction wording must follow the pure mapping supplied by the feature state; the UI must not infer/invert axes itself.
- `level` is used only for a valid available sample whose rounded display value is zero. It is never a waiting placeholder.
- The visual is a compact instrument, normally about 176 dp high (allow 160–200 dp based on available width) and full usable card width. Preserve roughly a 2.4:1 to 3:1 width-to-height drawing area; do not exceed available height simply to preserve an aspect ratio.
- Draw inside a clipped rounded inner viewport (6 dp corners), with a subdued blue-violet sky in the upper field and a visibly darker, desaturated purple ground in the lower field. Both differ clearly from the outer card. A thin high-contrast horizon line separates them.
- Transform the horizon/sky-ground/tick layer around viewport center: roll rotates it by the bounded mapped roll, and relative pitch translates it vertically by the bounded mapped pitch offset. Overscan sky and ground beyond the viewport before clipping so extreme values never reveal an empty edge. The exact sign convention comes from the tested presentation mapping; it must match the readable summary.
- Add five to seven mirrored horizontal tick marks on each logical side of the viewport, with a longer central/reference tick. Tick color is low-emphasis `#D9D9ED` at about 65–75% alpha. They move with the horizon layer, remain unlabeled, and are decorative support rather than a separate semantic target.
- Overlay a fixed centered aircraft reference in near-white `#D9D9ED`: a small center dot plus wings extending toward logical start/end, modeled on the legacy plane vector or a Compose Canvas equivalent. It does not rotate, translate, receive focus, or require an icon-library/raster asset.
- Below the instrument, after 12 dp, center the cyan `Calibrate` action. Its row is at least 48 dp high and 48 dp wide; it has 12 dp top/bottom content room and remains reachable at large text. The available card commonly measures approximately 300–330 dp including padding/action but remains content-driven.
- Tapping Calibrate immediately removes the summary/instrument/action and enters the waiting layout. The next valid sample returns to available with the pitch indicator re-centered and a polite `Horizon calibrated` announcement. It must not alter GNSS, Flight parameters, Course, or activity state.

### Horizon unavailable

- Center `Horizon unavailable` and `This device cannot provide attitude data.` using the 12 dp title/body rhythm. The card has no attitude visual, last reading, calibration control, hide preference, or retry.
- This is a stable hardware-capability state. Announce its title once politely when it transitions in, not on every recomposition.

### Read error

- Center `Unable to read horizon` and `Smart Flight could not start the attitude sensor.`; show `Try again` 12 dp after the body using the established cyan text-action style.
- Replace all prior attitude contents; never retain a visual, summary, or Calibrate target behind the error. Announce the error title once politely when entered.
- Retry clears presentation/calibration state before platform availability is reevaluated. When foreground eligible, it returns to waiting while registration is attempted; when hardware is absent it shows unavailable. It is not a retry affordance while backgrounded or outside the fine-location dashboard.

## Interaction, mapping, and motion handoff

- Keep Compose presentation state framework-independent: `Waiting`, `Available(presentation-safe relative pitch, display-relative roll)`, `Unavailable`, and `Error`. UI never receives raw `SensorEvent` values and all displayed/action/accessibility copy comes from resources.
- Reject non-finite samples before they reach `Available`. The feature state/controller owns calibration reference, stale-callback/session rejection, display-rotation reset, and a deterministic bounded mapping; the drawing consumes the mapped values rather than the legacy `SCALE = 4`/pixel range.
- Recommended visual limits, to make the design testable without tying it to pixels: clamp relative pitch used for drawing to ±30° and map it linearly to at most ±35% of the instrument viewport height; clamp visual roll to ±45°. The textual summary may report the clamped presentation-safe value rather than an unbounded raw reading. If the implementation chooses different values, they must remain documented, pure, bounded, and test-covered; no raw extreme value may place artwork outside its visual meaning.
- Sensor updates may use a short 120–180 ms latest-value transition for horizon transform only when Android’s remove/reduce-motion setting permits. Coalesce to the newest target; never queue rotations or play a continuous decorative animation. With reduced motion, update directly. Static-state replacement may use the existing 150–200 ms crossfade/size transition with the same reduced-motion rule.
- A display rotation/configuration reset clears the visual before the next callback; it must not rotate a stale image into the new orientation. Calibration resets similarly. The controller/source, not the visual, selects display-relative axes.
- Horizon shares the single orientation source/listener with Course. The UI design does not add a second sensor owner, foreground service, notification, permission, network call, persistence, or relationship to a GPS fix/location-services setting.

## Accessibility and semantics

- Traversal order in available state: title; merged textual attitude summary; decorative instrument excluded from the semantic tree; Calibrate button. In static states: title, body, then Try again when present. The card itself is not a focusable button.
- The summary is the complete non-visual equivalent of the instrument and identifies calibrated/display-relative context. It is non-live during routine updates so TalkBack does not announce sensor movement. Do not expose tick marks, sky/ground, plane components, or canvas subparts as independent focus stops.
- State transition announcements use a single polite live region: calibration completion, calibration-entered waiting, unavailable, and error. Initial waiting and every ordinary live-value update are silent. Ensure the live node is not recreated such that it repeats announcements on recomposition.
- `Calibrate` has resource-backed visible label plus semantic hint: `Sets the current pitch as level.` It uses button role, minimum 48 dp target, pressed/ripple feedback, and a visible 2 dp cyan focus outline with 4 dp rounded corners for keyboard/switch access. It has no long-click action.
- `Try again` has the analogous resource-backed hint `Retries the attitude sensor.` Treat the visible em dash/degree and words in summaries as localized resources/templates, with locale-aware number formatting.
- Do not rely on horizon color split, rotation, vertical motion, cyan, or animation alone. Support TalkBack, touch exploration, keyboard, switch access, magnification, RTL, gesture navigation, display cutouts, and 200% font scale.

## Responsive behavior

- Portrait phones: use the sequential one-column dashboard. A normal available Horizon card has title, one or two summary lines, centered wide instrument, then centered Calibrate action.
- Narrow windows, 200% font, or long localization: summary can wrap to two or more centered/locale-appropriate lines; increase card height. The instrument may shrink to its 160 dp lower target only after allowing text/action room. Never overlay the text/action over the Canvas or truncate their focus target.
- Landscape, tablets, and expanded windows: retain the centered 600 dp single card column; do not form a landscape cockpit or place cards side-by-side. Use the wider card to enlarge the instrument only up to the 200 dp intended visual height, reserving room for readable text. The outer dashboard scroll makes all content reachable in short heights or with an open keyboard.
- Use logical start/end for textual alignment and symmetric shape/ticks. The aircraft reference and horizon geometry remain visually symmetric in RTL; direction terms and localized numeric templates must remain semantically correct according to the mapped physical direction, not be accidentally mirrored by layout direction.

## Resource-backed copy

Add Android string resources (names are illustrative) for all visible, action, and spoken content:

- `Horizon`; `Waiting for attitude data…`; `Horizon unavailable`; `This device cannot provide attitude data.`; `Unable to read horizon`; `Smart Flight could not start the attitude sensor.`; `Try again`; `Calibrate`.
- `Sets the current pitch as level.` and `Retries the attitude sensor.`
- Pitch/roll visible and spoken templates, including `Pitch: %1$s`, `Roll: %1$s`, `level`, `up`, `down`, `left`, `right`, localized degree values, and the merged attitude spoken summary.
- Polite state announcements: `Horizon recalibrating. Waiting for attitude data.`, `Horizon calibrated`, plus unavailable/error titles as appropriate.

The controller and UI must use wording consistent with the defined platform axes. Do not hard-code user copy, raw sensor floats, or the legacy all-caps `RESET` label.

## Design verification checklist

- Fine-location foreground dashboard shows exactly GNSS, Flight parameters, Course, then one Horizon card; onboarding/approximate/denied/revoked states show no Horizon card or attitude observation.
- A fresh foreground session and recalibration show `Horizon` / `Waiting for attitude data…` with no level graphic, old image, summary, action, or stale calibration. First valid current callback establishes level and enables available UI.
- Controlled current samples show bounded, directionally matching vertical horizon shift/roll, fixed aircraft reference, symmetric ticks, and a matching text summary. Invalid/non-finite/extreme values cannot produce empty/escaped artwork or unbounded textual output.
- Calibrate changes only Horizon to waiting; its next valid reading centers pitch and announces completion. No long press, hide-card, or persistence behavior is discoverable.
- All four display rotations clear the prior image/reference and then show a fresh display-relative result. Course remains current while Horizon is active and the session owns no more than one underlying orientation listener.
- Unavailable has no retry/action; registration/read failure has accessible Try again and clears its old UI before retry. Retry is inert/safely deferred outside eligibility.
- Horizon remains available when GNSS/location services are off if the fine-permission dashboard is foregrounded, but stops and clears on pause, permission loss, destruction, settings return, retry replacement, and repeated lifecycle changes.
- TalkBack receives state transitions once, never raw update chatter; keyboard/switch can focus and activate both actions with visible focus. Portrait, landscape, RTL, 200% font scale, insets, magnification, and keyboard preserve readable reachable content without clipping or overlap.

## Inspected references

- Authoritative task: `.ai/tasks/TASK-005.md`.
- Rewrite dashboard and interaction baseline: `MainActivity.kt`, `ui/gnss/GnssStatusScreen.kt`, `ui/gnss/CourseCard.kt`, `ui/gnss/FlightParametersCard.kt`, `course/CourseState.kt`, `course/AndroidCourseOrientationPlatform.kt`, `ui/permission/PermissionColors.kt`, `ui/theme/Color.kt`, `ui/theme/Theme.kt`, and `res/values/strings.xml`.
- Legacy Horizon behavior and placement: `cards/adapter/CardViewContainer.kt`, `cards/horizon/HorizonCardView.kt`, and `cards/horizon/HorizonCardViewPresenter.kt`.
- Legacy Horizon visual sources: `res/layout/horizon_card_layout.xml`, `res/drawable/fake_plane_instrument_icon.xml`, `res/drawable/scale_left_icon.xml`, and `res/drawable/scale_right_icon.xml`. `promo/promo.png` is present (1024 × 500) but could not be rendered in this environment; the inspected layout/vectors are the visual source of truth.

## Open questions

None. The recommended ±30° pitch / ±45° roll visual bounds are a design handoff recommendation, not a new product requirement; the developer may choose another tested pure bounded mapping that preserves the documented visual and accessibility behavior.
