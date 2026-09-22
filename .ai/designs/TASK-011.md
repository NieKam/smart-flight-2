# TASK-011 — About and app-information design

## Purpose and scope

Add a discoverable About action to the authorized Smart Flight dashboard and a
Compose-native app-information surface. The surface identifies the app,
shows the installed version, provides feedback and store-rating handoffs, and
contains a readable safety disclaimer and rewrite-accurate open-source
attribution. It is informational only: opening or dismissing it must not alter
permissions, GNSS/sensor observation, route or map state, unit preferences, or
dashboard lifecycle.

This document defines the UI and interaction contract for TASK-011. It does
not add a second activity, navigation framework, XML dialog, legacy settings,
new permission, network request, analytics, or third-party link/dialog
dependency.

## Inputs inspected

### Authoritative task

- `.ai/tasks/TASK-011.md`

### Rewrite implementation and theme

- `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/theme/Theme.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/theme/Color.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/permission/PermissionColors.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/AndroidManifest.xml`
- existing launcher resources under `app/src/main/res/mipmap-*` and
  `app/src/main/res/drawable/`
- `.ai/designs/TASK-010.md` for the established dashboard/settings design
  conventions

### Original application behavior and visual reference

- `app/src/main/java/kniezrec/com/flightinfo/common/DialogUtils.kt`
- `app/src/main/java/kniezrec/com/flightinfo/MainActivityPresenter.kt`
- `app/src/main/res/menu/app_menu.xml`
- `app/src/main/res/layout/about_app_dialog_layout.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-pl/strings.xml`
- `/home/ai-dev/smart-flight/promo/promo.png` (1024 × 500 dashboard visual
  reference)

## Behavior classification

### Observed legacy behavior

- The main menu exposes Settings and About; About is available from the main
  dashboard flow, not the permission view.
- About is a titled, scrollable Material dialog with the launcher icon. Its
  content order is Version, rating link, feedback link, then disclaimer and
  open-source attribution.
- The legacy version text combines `versionName` and `versionCode` as
  `versionName (versionCode)`.
- Rating uses a `market://details?id=...` link and feedback uses a `mailto:`
  link resolved by Android. The legacy implementation does not describe safe
  handling for absent handlers.
- The legacy screenshot and colors establish a dark-purple card language,
  lavender/light text, and cyan action emphasis.

### Required behavior from TASK-011

- An authorized user must find and activate a clearly labeled About action
  from the dashboard. Permission onboarding must not expose it.
- About opens in the existing single activity and does not restart or stop
  observations or lose current dashboard state.
- The installed package version is read at runtime, with defensive handling
  for API differences, absent/malformed metadata, and test/fake package
  contexts.
- The surface contains Smart Flight identity, a Version label and value,
  Send feedback, Rate in Google Play, a safety disclaimer, and rewrite-
  appropriate open-source attribution. All visible text is resource-backed.
- Feedback attempts a mail-capable Android handler using the configured
  feedback address. Rating prefers the market URI and falls back to the web
  store URI when appropriate. Missing handlers or launch failures keep About
  open and show a localized accessible failure message.
- About has an explicit dismiss control and system Back dismisses About before
  the activity can exit. Content scrolls at short heights, in landscape, at
  large font scales, and in RTL.

## Design decisions

### Entry point and dashboard header

Extend the existing authorized dashboard header so Settings and About are
peer actions. Keep the centered `Smart Flight` title and the current
dark-purple dashboard shell. At ordinary widths, place two labeled actions in
the logical trailing area in this order: `Settings`, then `About` (About may be
the second action nearest the trailing edge if that is the natural reading
order for the locale). Each action is a semantic button with a minimum 48dp ×
48dp touch target, visible text, and a focus/pressed state. Do not make About
an icon-only overflow item: the task specifically requires a clearly
labeled/discoverable action.

At compact widths, allow the action group to wrap or use a labeled Material 3
overflow/menu affordance containing both `Settings` and `About`, provided the
menu itself has an accessible `More options` label and About remains one
activation away. The title must not collide with actions. The compact choice is
a responsive layout decision, not a change in available actions.

About is only composed in the authorized dashboard branch. It is independent
of the existing `showUnitSettings` state; the dashboard and its controller
state remain alive underneath the surface.

### About surface choice

Use a Material 3 `AlertDialog`-style modal surface, or the equivalent custom
Material 3 dialog surface, rather than a second destination. This retains the
legacy mental model, provides standard modal focus/back behavior, and fits the
task's informational scope without introducing navigation. Make the dialog a
contained, responsive surface rather than reproducing the old XML layout.

Suggested geometry:

- Width: Material 3 dialog width constraints, with a readable maximum around
  560dp and horizontal margins that remain usable on narrow portrait windows.
- Height: content-driven but bounded by available height; reserve system safe
  insets and keep the dismiss action visible outside the scroll region.
- Outer content padding: 24dp on regular widths, reducing to 20dp only when
  needed for a narrow window.
- Vertical rhythm: 16dp between identity and version, 20dp before the action
  group, 12dp between action rows, 24dp before the information text, and
  24dp before the bottom actions.
- Minimum interactive height: 48dp for each action and the final dismiss
  button. The scroll viewport owns only the long information content, not the
  dismiss action.

The dialog uses the existing Smart Flight dark-purple/purple/cyan tokens,
with a slightly elevated surface against the dashboard. Prefer the theme's
Material 3 `surface`/`onSurface` roles and existing `cardPurple`/`actionCyan`
language rather than new hard-coded colors. Cyan is an accent, not the sole
indicator of an action; labels, button semantics, and optional leading icons
must also identify actions. Preserve readable contrast for body text and
disabled/error states.

### Surface hierarchy and content

Present these sections in this order:

1. **Identity** — launcher icon in a 56dp–72dp square with an accessible
   decorative/identity description as appropriate, followed by the localized
   `Smart Flight` app name. The icon and title form one identity block; do not
   use the icon as a clickable control.
2. **Version** — a small/medium label `Version` and a clearly readable value.
   Prefer `versionName (versionCode)` when both runtime values are valid;
   otherwise show the valid value alone. If neither can be read, show a
   localized `Version unavailable` fallback, never a blank, exception, `null`,
   or legacy hard-coded value.
3. **External actions** — two full-width or comfortably wide text/button
   rows, each with a leading familiar icon where available and a visible label:
   `Send feedback` and `Rate in Google Play`. The full row is actionable. Give
   each a content description/state description that names the external
   purpose, and expose button/link semantics rather than relying on styled
   text or color.
4. **Information** — a scrollable body containing a concise safety disclaimer
   equivalent in meaning to the legacy warning (not an aviation-certified
   instrument, estimates may be inaccurate, and users remain responsible for
   safe decisions), followed by an `Open-source attribution` heading and only
   libraries/assets actually used by the rewrite. Keep the wording clear and
   avoid legacy typos, stale dependency names, or unsupported certification or
   legal claims.
5. **Dismissal** — a right-aligned or logical-end `OK`/`Close` text button in
   the fixed dialog action area. It is the primary way to close the surface and
   is reachable without scrolling.

The information body is a single logical reading flow for TalkBack and RTL.
Use headings and paragraph spacing, not a dense bullet wall. Long attribution
lines wrap naturally. Do not use HTML links or unlabelled colored text.

### External handoff and failure states

The UI should expose a small, deterministic launch result to the dialog:

- **Success/handoff:** launch the resolved mail or market/browser intent and
  leave the About surface according to normal Android activity handoff. The
  dialog must not send mail or rate on the user's behalf.
- **No handler / failed launch:** retain About open and show an inline error
  message near the relevant action, or a dialog snackbar if that is the
  established host pattern. The message is localized, announced as a live
  region, and explains the next useful fact, e.g. `No email app is available`
  or `Unable to open Google Play`. Do not throw or silently do nothing.
- **Retry:** the action remains enabled so the user can retry after installing
  or enabling a handler. Do not add a new product flow or automatically open
  application settings.

Construct `mailto:` with the resource-backed configured address and no
untrusted subject/body injection. Construct the store market URI from the
current package identity; if it cannot resolve, try the HTTPS store listing
with a browser-capable handler. Handler resolution and launch exceptions are
platform states, not fatal UI states. Preserve the dialog's scroll position
and any current error until the next action or dismissal.

### Modal and focus behavior

- Opening About captures modal focus; initial focus should land on the dialog
  title/identity or first meaningful content, not an invisible control.
- Back, outside dismissal if supported by the chosen Material surface, and the
  explicit dismiss button all close About. Back must be consumed by About
  before it can exit the activity.
- After dismissal, return focus to the dashboard About action where the
  platform makes this practical. Settings back handling must remain unchanged.
- Opening and closing must not recreate controllers or issue a second start/
  stop transition for GNSS, location, pressure, compass/horizon, nearby-city,
  map, or route observation.

## State and content matrix

| State | Visible treatment | Interaction |
|---|---|---|
| About closed | Dashboard header exposes About only in authorized branch | Activate About to open modal |
| Loaded metadata | Identity, `Version`, formatted installed value, actions, scrollable information | All actions available |
| Version name only / code only | Show the valid runtime value with no empty parentheses | No special user action |
| No version metadata | Localized `Version unavailable` value; rest of About remains usable | External actions remain available |
| Mail handler unavailable or launch fails | Feedback action remains visible; localized inline/live error near it | Retry or dismiss |
| Market and browser unavailable or launch fails | Rating action remains visible; localized inline/live error near it | Retry or dismiss |
| Long content / large font / short window | Scrollable information viewport; identity/actions/dismissal remain reachable | Scroll content independently; dismiss without reaching bottom |
| Permission onboarding | No About entry and no About surface | Permission-related actions only |

Loading is not a required user-visible state: package metadata is local and
should be read before rendering the loaded surface. If a platform adapter
needs asynchronous setup, keep the identity and a non-blocking version
fallback visible rather than showing an indefinite spinner.

## Responsive behavior

### Portrait and narrow windows

Use a single column. Stack identity, version, actions, and information. The
information body scrolls vertically. Action labels may wrap to two lines while
retaining a 48dp minimum height; do not truncate or ellipsize safety text.
Keep the title, version, and dismiss action outside the scroll viewport when
possible so the user always understands the surface and can close it.

### Landscape and wide windows

Keep the dialog centered with a readable maximum width. Do not expand body
text across the entire screen. A two-column identity/action arrangement is
permitted only when it preserves the specified reading order and still leaves
the long information section with a single readable measure; the default
implementation should remain a simple single column. The dialog must remain
bounded by available height and scroll rather than extend behind system bars.

### Large font scale, RTL, and accessibility

- Let typography and buttons grow naturally; avoid fixed-height text boxes.
- Use logical start/end alignment, `Text` wrapping, and RTL-safe icon placement.
- Preserve a minimum 48dp target and visible keyboard/focus indication.
- Provide semantic headings/labels for Version, disclaimer, attribution, and
  each action. Expose the selected/failure state through semantics or a live
  region, not color alone.
- The launcher icon may be marked decorative when the adjacent app name is
  announced as the identity; if not decorative, use a localized meaningful
  description without repeating the name unnecessarily.
- Ensure the long safety text is selectable/readable by assistive technology
  as one coherent region, and that failure messages are announced once.

## Resource and copy contract

Add or update resource-backed strings for the About title/label, Version,
unknown-version fallback, Send feedback, Rate in Google Play, feedback/store
failure messages, disclaimer, attribution heading/body, dismiss action, and
external-action accessibility descriptions. Keep existing localized resources
consistent where translations exist; do not put visible copy in Kotlin.

The feedback address is configuration/data, not visible copy. The store URL
must be derived from the installed package identity. The attribution list must
be verified against rewrite dependencies/resources before implementation;
`MPAndroidChart`, Material Dialogs, LeakCanary, and other names appearing only
in the legacy text must not be copied into the rewrite's attribution.

## Intentional deviations from the original application

- Replace the legacy menu-only About entry with a visible, labeled dashboard
  action (with a responsive labeled menu fallback only at compact widths) to
  improve discoverability and accessibility.
- Replace the third-party/XML custom dialog with a Material 3 Compose modal
  surface in the existing single activity.
- Keep the identity, actions, and dismiss control available while only the
  long body scrolls, avoiding the legacy layout's risk of hiding the OK action
  at large content sizes.
- Use semantic buttons and explicit error states instead of HTML-style links
  whose meaning depends on color and movement-method behavior.
- Use rewrite-accurate attribution and corrected safety wording; legacy
  dependency claims and typos are historical references, not product copy.
- Make version and external-intent failures recoverable. The legacy code could
  throw or fail silently when package metadata or handlers were unavailable.

These deviations preserve the legacy information hierarchy and dark-purple /
cyan character while meeting the rewrite's Compose, accessibility,
responsive, and defensive-runtime requirements.

## Verification expectations for implementation

Focused tests should cover:

- version formatting with both values, partial values, malformed/absent
  values, and API-specific version-code access;
- mailto and market/web URI construction, handler resolution, launch failure,
  and the fact that failure leaves About open;
- authorized-only visibility, open/close behavior, system Back precedence,
  and preservation of dashboard state;
- key content order, resource-backed labels, scrollability, semantic action
  labels, failure announcement, and large-text/RTL-safe layout where the
  configured Compose test environment supports them.

The implementation should also verify that existing unit settings, dashboard
observation, route/map behavior, permission onboarding, and configured project
checks remain unchanged.

## Open questions

None. The task explicitly permits either a modal dialog or contained
informational destination; this design selects the Material 3 modal because it
best matches the existing behavior while preserving single-activity state.
The exact rewrite attribution entries and configured feedback address should
be confirmed from the implementation's actual dependency/resource inventory
when coding, as required by the task; this is validation of existing project
facts rather than a new product decision.
