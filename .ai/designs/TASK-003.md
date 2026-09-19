# TASK-003 — Live GPS flight-parameters card design specification

## Scope and relationship to existing UI

Add one foreground-only `Flight parameters` card directly after the existing GNSS-status card on the fine-location dashboard. It presents current GPS-derived ground speed, vertical speed, and altitude in fixed metric units.

TASK-001 onboarding remains the entire granted-screen replacement whenever fine permission is missing, approximate-only, denied, or revoked. Android permission and location-settings UI remains platform-owned. TASK-002 retains its existing states, actions, and visual behavior unchanged.

## Inputs and distinctions

### Required behavior from TASK-003

- The card is visible with the fine-permission dashboard and is second in the page order, below GNSS status.
- Before a valid current-session location fix, it says `Waiting for GPS position…` and exposes no readings.
- Once at least one usable field is received, show rows in this exact order: `Speed`, `Vertical speed`, `Altitude`.
- Speed is current platform ground speed in km/h to one decimal; altitude is current GPS altitude in m to one decimal; vertical speed is the elapsed-realtime altitude rate in m/s to one decimal with a `+` or `−` sign.
- A first usable altitude sample has no vertical rate and shows `—`. Missing speed or altitude affects only that row; no zero, stale, `NaN`, or infinity substitute is shown.
- Any new foreground observation session or unavailable/error/permission/settings-return condition clears all flight data and returns the card to waiting.

### Observed legacy behavior

- `FlightParametersCardView` follows `SatellitesCardView` in the permission-gated legacy dashboard.
- Its 160 dp purple card has simple left-aligned label/value pairs in the order Speed, Vertical speed, Altitude, Pressure. Labels are muted lavender 18 sp; values are light lavender 18 sp; their initial value is a dash.
- Legacy metric helpers format speed, altitude, and vertical speed to one decimal, and vertical speed uses a leading sign. The legacy presenter also has a separate pressure sensor path.

### Design decisions and intentional deviations

- Add a card title and a distinct waiting state. The legacy layout has neither, so it can look like all fields are unavailable even before GPS acquisition. A title gives this surface clear identity and centered waiting copy makes the normal acquisition state unambiguous.
- Omit the legacy Pressure row entirely. It is explicitly out of scope and must not imply a barometer dependency.
- Use a content-growing card rather than legacy fixed 160 dp row geometry. This preserves readable, touch/accessibility-safe layout at 200% font scale.
- Keep values visually stronger than labels using the current light text token. Do not revive legacy muted label text, because the rewrite already chose the accessible light token for this purple surface.
- Use text (`Waiting for GPS position…` and `—`) rather than color, motion, or a zero value to communicate absence.

## Visual system

Use the established TASK-002 dashboard shell without alteration:

- Page: `#484685`; top header is centered `Smart Flight`, 56 dp minimum height, `#D9D9ED`, 20 sp medium.
- Cards: `#5B5999`, 10 dp rounded corners, 4 dp elevation, 12 dp horizontal page margin, and centered single column capped at 600 dp.
- Between cards: 12 dp. The GNSS card keeps its existing 12 dp vertical placement; the flight card follows with a 12 dp gap and has a 12 dp bottom page margin.
- Flight-card padding: 24 dp start/end and 20 dp top/bottom. Minimum height is 160 dp only for the waiting presentation; data content may grow naturally.
- Title: `#D9D9ED`, 22 sp / 28 sp, medium. Body/labels/values: `#D9D9ED`, 18 sp / 25 sp. Numeric values use medium weight; labels normal weight. This creates hierarchy without a new color.
- No icons, charts, maps, gauges, compass, status dots, or pressure affordance are used. Units are part of the rendered value, not separate decorative badges.

## Screen structure

```text
Smart Flight shell (outer vertical page scroll)
├── centered 56 dp header: Smart Flight
└── centered column, max 600 dp
    ├── existing GNSS status card
    └── Flight parameters card
        ├── waiting: title + centered waiting message
        └── readings: title
            ├── Speed             123.4 km/h | —
            ├── Vertical speed      +1.2 m/s | —
            └── Altitude          1234.5 m  | —
```

The page owns scrolling. Neither card nor the three-row reading group is independently scrollable.

## Flight-parameters states

### Waiting / no current-session reading

- Render title `Flight parameters` above body `Waiting for GPS position…`.
- Center the title/body group both horizontally and vertically in the 160 dp minimum card, matching TASK-002's waiting-state balance. Keep 12 dp between title and body.
- Render neither the row labels nor placeholder values in this state. This is important: it tells the user that acquisition is in progress, rather than suggesting three measured values are missing.
- This state applies on initial display and after every session reset, including pause/resume, permission loss, location-services disabled, absent GNSS hardware, observer registration/security failure, retry, and return from location settings. It remains no-data even if the companion GNSS card gives a disabled, unavailable, or error explanation.

### Readings / partial readings

- Change to the readings layout only after a current-session fix supplies at least one valid displayable field (speed and/or altitude).
- Title is top-aligned and left-aligned at the logical start edge. Leave 16 dp below it before the first row.
- Each row is a full-width logical-start/logical-end pair, with a 48 dp minimum row height. Label is aligned to the logical start; value is aligned to the logical end and end-aligned within its available width. Use `Row` with baseline/vertical-center alignment and a weighted label so long localized numbers cannot overlap labels.
- Leave 4 dp between adjacent row containers (the 48 dp target includes each row's own vertical space). Do not draw dividers; the limited set of rows and generous rhythm are sufficient.
- Values are one line where possible; on narrow widths or at large fonts, allow the value to wrap below its label within the same row rather than clip, overlap, or horizontally scroll. Preserve logical start/end alignment in RTL.
- Valid field values update in place. If a later current fix lacks a field, display `—` for that field rather than retaining an older measurement. Vertical speed is `—` until a usable pair of altitude samples has a positive elapsed interval; it is also `—` whenever it cannot be calculated safely.

Examples of intended readings content (illustrative only):

```text
Speed             86.4 km/h
Vertical speed      −0.7 m/s
Altitude          412.8 m
```

Use the true Unicode minus sign in localized display output when the value is negative; positive values must include `+`. Locale-aware numeric formatting supplies decimal punctuation, while the localized unit-bearing format resource controls presentation.

## Interaction and state transitions

- The flight card has no direct actions in this task. The existing GNSS card remains the sole location-settings and retry action surface.
- A successful location update swaps waiting for readings with the same 150–200 ms crossfade/size transition used by TASK-002. Preserve/reduce to no perceptible animation when system remove-animations is enabled. Do not animate continuously as fixes arrive.
- Clearing a session immediately returns the flight card to waiting before an observer restart; do not crossfade through stale readings.
- While the card is waiting due to an availability or registration problem, the GNSS card communicates the cause and action. Avoid duplicating warnings or an action in the flight card, which would create competing recovery controls.

## Accessibility and semantics

- Card traversal order: card title, then waiting message **or** Speed row, Vertical speed row, Altitude row. No hidden prior readings may remain exposed when waiting.
- Expose each readings row as one merged TalkBack element combining label and value, e.g. `Speed, 86.4 kilometres per hour`; `Vertical speed, unavailable`; `Altitude, 412.8 metres`. The visible compact units remain `km/h`, `m/s`, and `m`; use resource-backed expanded accessibility strings where needed for natural speech.
- Announce the one-time transition from waiting to readings politely, such as `Flight parameters available`; do not live-announce each routine numeric refresh. The waiting message itself must be readable on focus.
- The em dash must have an explicit accessible meaning (`unavailable`), never be the only spoken content. Do not rely on color, sound, or motion for either waiting or unavailable status.
- Support TalkBack, switch access, keyboard navigation, RTL, magnification, gesture insets, and 200% font scale. The card expands vertically and the outer page scroll makes every item reachable.
- Maintain the current palette's readable contrast; labels are not dimmed below the rewrite's `#D9D9ED` text token.

## Responsive layout

- Portrait: GNSS status card then flight parameters card in one 12 dp-gutter column beneath the header.
- Landscape, tablets, and expanded windows: retain a single centered 600 dp maximum-width column. Do not create side-by-side cards or an empty secondary pane; the sequential operational scan remains clearer.
- Short heights, expanded satellite reports, and 200% text: the outer dashboard scrolls. Do not impose a maximum height on either card that would hide content.
- Narrow/large-text value rows may reflow value below label; all rows retain logical direction, safe margins, and at least 48 dp height.

## Resource and implementation-facing copy

All visible and accessibility strings are Android resources. Add resource-backed copy for:

- `Flight parameters`
- `Waiting for GPS position…`
- `Speed`
- `Vertical speed`
- `Altitude`
- unavailable spoken value (`unavailable`)
- an optional single polite availability announcement (`Flight parameters available`)
- locale-aware value templates for km/h, m/s with mandatory sign, and m.

The UI receives presentation-safe state, not Android `Location`. It must distinguish: no current-session usable reading (waiting), each individual missing field (em dash), and fully/partially available readings. Formatting and the altitude-history calculation belong outside composables so they can be deterministically tested.

## Verification checklist

- Fine permission with enabled services/hardware displays the existing GNSS card followed by the centered, titled flight card.
- Initial, resumed, settings-return, permission-loss, disabled/unavailable, and registration-error sessions show only the flight waiting title/message—never stale values.
- A speed/altitude fix shows correctly locale-formatted readings, fixed metric units, and `—` vertical speed on the first altitude sample.
- A later valid positive-interval altitude sample shows a signed one-decimal vertical speed; invalid/repeated/out-of-order timing does not display invalid output.
- A partial fix uses `—` only in the affected row. At least one valid field produces all three labeled rows.
- GNSS recovery actions remain only in the first card; no duplicate flight-card action is introduced.
- Portrait, landscape, expanded width, RTL, 200% font scale, TalkBack, keyboard/switch access, and gesture insets preserve readable, reachable content.
- No pressure, settings/unit picker, map, chart, route, network, background indication, or fabricated zero value appears.

## Inspected references

- Authoritative task: `.ai/tasks/TASK-003.md`.
- Current rewrite: `MainActivity.kt`, `ui/gnss/GnssStatusScreen.kt`, `ui/permission/PermissionOnboardingScreen.kt`, `ui/theme/Color.kt`, `ui/theme/Type.kt`, and `res/values/strings.xml`.
- Legacy dashboard/card behavior: `cards/adapter/CardViewContainer.kt`, `cards/gps/FlightParametersCardView.kt`, `cards/gps/FlightParametersCardViewPresenter.kt`, `res/layout/flight_parameters_card_layout.xml`, `avionic/Speed.kt`, `avionic/calculators/Altitude.kt`, and `avionic/calculators/VerticalSpeedCalculator.kt`.
- Legacy visual resources: `res/values/colors.xml`, `res/values/dimens.xml`, and `res/values/styles.xml`. `promo/promo.png` is present, but no renderable task-specific screenshot is available in this workspace; inspectable layout/resources were used as the authoritative visual reference.

## Open questions

None. Fixed metric units, omission of pressure, and no flight-card recovery action are deliberate constraints of TASK-003.
