# TASK-002 - Offline GNSS status card design specification

## Scope

TASK-002 replaces TASK-001's minimal granted confirmation with one foreground-only GNSS status card. It gives useful offline feedback while a precise position is being acquired. It must not imply maps, routing, city lookup, or flight-parameter features.

TASK-001 onboarding remains visible whenever fine location is absent or approximate-only. Android permission and settings screens are platform-owned.

## Visual system

Use the current Smart Flight shell: page #484685, card #5B5999 with 10 dp corners and 4 dp elevation, and the centered 56 dp Smart Flight header in #D9D9ED. The card uses 12 dp outer spacing, 24 dp horizontal and 20 dp vertical inner padding, 160 dp minimum height, and a 600 dp maximum width centered in expanded windows. It grows with text and rows; the outer page scrolls.

Card titles use 22 sp / 28 sp medium #D9D9ED. Body and summary use 18 sp / 25 sp #D9D9ED. Text actions use #6CF0FF, 48 dp minimum targets, pressed feedback, and a visible 2 dp cyan keyboard-focus outline.

The legacy satellite card establishes the purpose and hierarchy, not its MPAndroidChart graph, Lottie animation, or custom sky plot.

## Structure

```text
Smart Flight shell
└── one GNSS card
    ├── title
    ├── state message or satellite summary
    ├── satellite list when available
    └── text action only when disabled or retryable
```

There are no additional dashboard cards, tabs, or menus in this task.

## States

### Waiting

- Title: `GNSS status`.
- Body: `Waiting for GPS signal...`
- No action, timer, error treatment, or required animation.
- Center the title/body group in the minimum-height card.
- This is normal while observation starts, when zero satellites are reported, indoors, or during slow acquisition. It must not imply failure or disabled GPS.

### Status available

- Title: `GNSS status`.
- Summary: localized `Using 1 satellite` or `Using N satellites`, based only on the satellites currently used in the fix.
- After 12 dp, show every satellite in current report-index order as a full-width list.
- Every row is at least 48 dp tall and contains `Satellite N`, then `Used for position` or `Not used for position`.
- The second line is mandatory. A colored marker may supplement it, but color cannot be the only status distinction.
- Signal strength is optional supplementary localized text. It is not a chart and cannot replace the used/not-used label.
- The card grows for the full report. Do not truncate reported satellites.

### Location services disabled

- Title: `Location services are off`.
- Body: `Turn on location services to receive a GNSS signal.`
- Action: `Open location settings`.
- Remove any stale satellite summary/list.

### GNSS unavailable

- Title: `GNSS unavailable`.
- Body: `This device does not provide GNSS hardware.`
- No action and no network suggestion.

### GNSS status error

- Title: `Unable to read GNSS status`.
- Body: `Smart Flight could not start GNSS status.`
- Action: `Try again`.
- Remove stale rows. Retry first re-evaluates availability.

## Behavior and motion

- Fine location is the only success condition. Fine plus coarse may show GNSS status; approximate-only, revoked, denied, or missing fine location returns to TASK-001 onboarding and removes GNSS content.
- A fresh fine grant changes the TASK-001 granted state to waiting with a 150-200 ms crossfade/size transition. Do not animate Android dialogs/settings.
- A non-empty report changes waiting to available. A zero-satellite report changes or remains waiting, never error.
- Before observation and on resume, disabled services or unavailable hardware replace waiting/available states. Returning from settings clears stale data and renders the newly derived state before a callback arrives.
- `Open location settings` opens Android location-source settings. If it cannot open, retain the disabled card and show a short accessible transient failure message.
- A registration/security error presents the error card and announces it once. Try again keeps the error until a new state is derived.
- Respect remove-animations settings. No continuous waiting animation is required.

## Accessibility

- Traversal order: title, message/summary, rows in report order, then action.
- State-title changes caused by availability, error, or the first report are polite live announcements once. Do not repeatedly announce list refreshes.
- Each row exposes complete text such as `Satellite 3, Used for position`; include signal strength when shown.
- Actions expose their visible text with outcome hints: `Opens location settings` and `Retries GNSS status`.
- At 200 percent font scale, rows/card grow without overlap and page scrolling reaches all content. Support RTL, TalkBack, switch access, keyboard focus, and gesture insets.
- Waiting is communicated in text, not through motion or color alone.

## Responsive behavior

- Portrait: one card directly below the header, with 12 dp page edges.
- Landscape and expanded windows: still one centered column capped at 600 dp; do not create empty panes.
- Short viewports and long reports scroll in the outer page. Avoid a nested list scroll unless needed to keep controls reachable and traversal usable.

## Verification checklist

- Fine permission, enabled services, and GNSS hardware first show waiting, then the correct used-in-fix count and accessible rows after a report.
- Zero satellites show waiting.
- Disabled services show Open location settings; unavailable hardware has no action; observation failure shows Try again.
- Approximate-only access shows TASK-001 onboarding, never this card.
- Returning from settings clears stale GNSS content and re-evaluates.
- Portrait, landscape, RTL, 200 percent font scale, TalkBack, focus navigation, and gesture insets remain usable.
- No map, route, city, flight parameter, network, background service, chart, or Lottie UI appears.

## Handoff constraints

- Use the current-minimum-SDK Android GNSS status API, not deprecated GPS status APIs.
- Register only for active visible foreground UI and unregister when inactive. Do not start location updates, services, notifications, or persistence.
- Keep availability/observation and teardown lifecycle-aware and testable. Existing permission and ui feature packages are suitable; no broad architecture migration is needed.
- All app-visible text, including satellite plurals, is resource-backed.
- Preserve TASK-001 shell, permission behavior, and fine-location requirement.

## Inspected references

- TASK-002 specification: `.ai/tasks/TASK-002.md`.
- Current rewrite: MainActivity; TASK-001 permission state/controller/request; permission UI/colors; strings; focused tests.
- Legacy: CardViewContainer, SatellitesCardView, SatellitesCardViewPresenter, NoSatellitesFoundView, LocationProvider, LocationProviderApi24, LocationProviderImpl, LocationService, activity_main.xml, content_main.xml, satellites_card_layout.xml, no_satellites_layout.xml, colors.xml, dimens.xml, and strings.xml.
- Promo image: `promo/promo.png` is available at 1024 by 500. The sandbox image viewer could not render it; inspectable layouts/resources are therefore authoritative.
