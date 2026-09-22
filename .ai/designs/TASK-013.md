# TASK-013 Design Specification — Optional background flight-monitoring notification

## 1. Scope and intent

Add one authorized-dashboard preference, **Show background notification**, and
the system-surface behavior that accompanies it. When an authorized monitoring
session is temporarily backgrounded before a usable location fix, Smart Flight
may remain alive through a location foreground service and show a concise
waiting-for-GPS notification. The notification opens the existing dashboard;
it is not a new Compose dashboard card or a promise of indefinite tracking.

This document defines the UI, copy, visible states, and lifecycle handoff for
the implementation described by `.ai/tasks/TASK-013.md`. The task remains the
authority for permissions, service behavior, session ownership, and acceptance
criteria.

## 2. Inputs reviewed

### Authoritative task

- `.ai/tasks/TASK-013.md`

### Rewrite

- `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/settings/UnitSettingsScreen.kt`
- `app/src/main/java/kniezrec/com/flightinfo/display/DisplayPreferences.kt`
- `app/src/main/java/kniezrec/com/flightinfo/flight/FlightParametersController.kt`
- `app/src/main/java/kniezrec/com/flightinfo/flight/AndroidFlightLocationPlatform.kt`
- `app/src/main/java/kniezrec/com/flightinfo/gnss/GnssStatusController.kt`
- `app/src/main/java/kniezrec/com/flightinfo/gnss/AndroidGnssStatusPlatform.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/AndroidManifest.xml`
- Existing Settings and controller tests under `app/src/test` and
  `app/src/androidTest`

### Original application

- `/home/ai-dev/smart-flight/app/src/res/xml/app_preferences_layout.xml`
  (legacy preference layout; the actual path in the source tree is
  `/home/ai-dev/smart-flight/app/src/main/res/xml/app_preferences_layout.xml`)
- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/settings/FlightAppPreferences.kt`
- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/services/location/LocationService.kt`
- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/common/NotificationBroadcastReceiver.kt`
- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/common/AppVisibilityManager.kt`
- `/home/ai-dev/smart-flight/app/src/main/res/values/strings.xml`
- `/home/ai-dev/smart-flight/promo/promo.png` (1024 × 500 dashboard visual
  reference)

## 3. Behavior classification

### Observed legacy behavior

- The legacy General preference category contains a checked-by-default
  `Show notification` option.
- When the app backgrounds before a location has been obtained, the location
  service shows a platform notification with the title “Smart Flight is
  waiting for GPS signal” and content “Tap to back to the app”.
- Tapping the notification opens `MainActivity`; returning to the foreground
  clears the notification.
- Swiping the notification away invokes a dismissal receiver that stops GPS
  updates for the current run.
- If a location has already been obtained, the legacy service does not show a
  waiting notification and stops its updates on backgrounding.
- The legacy dashboard and promo reference use a dark-purple Smart Flight
  visual language. The notification itself is platform-rendered and does not
  need to imitate the promo composition.

### Required behavior

- The rewrite exposes a persisted, authorized-only setting named **Show
  background notification**, default On.
- A visible authorized activity starts the one background-capable monitoring
  session, satisfying Android foreground-service startup rules. The session
  remains alive when the activity is backgrounded without finishing, but only
  the existing location/GNSS observation needed by the task crosses that
  boundary.
- A waiting notification is eligible only when fine location remains granted,
  monitoring is active, no usable fix has arrived in the current session, the
  setting is On, location services are enabled, and the platform permits a
  user-visible notification. Approximate-only/revoked location, disabled
  location services, and notification-permission denial produce no displayed
  notification.
- A usable fix cancels the waiting notification and ends background updates
  according to the task’s legacy-compatible session behavior. Foreground
  return can establish a fresh foreground observation session.
- A notification tap returns to the existing `MainActivity`/dashboard and
  must be idempotent; it must not create a second service, listener, or
  monitoring session.
- Dismissal stops only the current background session. It does not alter the
  setting or permission and does not prevent a later foreground session.
- Turning the setting Off immediately stops an active background session and
  prevents future waiting notifications. Turning it On is honored by the next
  authorized session.

### Design decisions introduced here

- Keep the control in the existing Settings destination, but give it a small
  **Monitoring** section between Display and Units. This makes the difference
  between screen/map preferences and a lifecycle/system behavior explicit
  while retaining the task-required existing Settings surface.
- Use a Material 3 switch row with an explicit On/Off summary rather than a
  legacy checkbox or a bespoke card. The whole row is one semantic switch
  action.
- Keep notification text deliberately generic: it communicates waiting for a
  GPS signal and returning to Smart Flight, without coordinates, route data,
  sensor values, or aviation status.
- Treat notification-permission denial as a silent degraded platform state.
  The Settings row continues to represent the user preference; it must not
  claim that a notification is visible or add a repeated prompt loop.

## 4. Settings screen structure

The destination remains the current full-screen Compose Settings overlay:

```text
Scaffold
├── TopAppBar: back/up + “Settings”
└── safe-drawing, vertically scrollable content
    └── centered column, width ≤ 600 dp
        ├── Display heading
        ├── existing display rows
        ├── Monitoring heading
        ├── Show background notification row
        ├── Units heading
        └── existing unit selector rows
```

The existing display and unit controls retain their order, values, dialogs,
and behavior. The new row is absent from permission onboarding and is rendered
only when the user has reached the authorized dashboard branch.

### Row layout

- Reuse the existing Settings content insets: approximately 12–16 dp
  horizontal padding, 24 dp vertical padding, and the 600 dp max-width
  centered column.
- Give the Monitoring heading the same `titleLarge` treatment and vertical
  rhythm as Display and Units. Separate the heading from its row by about
  8–12 dp and from adjacent section headings by about 20–24 dp.
- Give the row a minimum height of 64 dp and at least 12 dp vertical content
  padding. The row’s label/summary area and switch together have a minimum
  48 dp touch target.
- Use a weighted logical-start text column and a logical-end Material 3
  `Switch`. Long summaries wrap; the switch may align toward the top of a
  multi-line row instead of compressing or clipping text.
- Keep the existing low-emphasis divider after the row, consistent with the
  current unit/display list.
- No decorative leading icon is needed. The switch and section heading are
  sufficient affordances and preserve the existing Settings visual hierarchy.

### Row copy and semantics

All strings are resource-backed, including the state summary and accessible
description. Recommended resources are:

| Purpose | Copy |
| --- | --- |
| Label | `Show background notification` |
| On summary | `On` |
| Off summary | `Off` |
| Supporting/accessible explanation | `Shows a notification while Smart Flight is waiting for a GPS position after the app leaves the foreground.` |

The supporting explanation may be included in the merged row content
description/state description rather than displayed as a third line. If the
localized explanation is visually displayed, place it below the label and
state in `bodyMedium`; it must wrap naturally. It must not say “continuous
tracking”, “guaranteed fix”, or imply that a notification is always possible.

The row is one merged semantics node with `Role.Switch`, checked state, and a
localized state description containing On/Off. The switch itself is rendered
non-actionably inside the row if the parent owns the click, avoiding duplicate
TalkBack stops. Either tapping the text or switch toggles the same value.

## 5. Visual treatment

- Use the existing Material 3 theme tokens and Settings surface. Do not create
  a notification-specific purple card in the dashboard.
- Labels use the current Settings `bodyLarge` style; state and supporting copy
  use the current `bodyMedium` style. Section headings use the current
  `titleLarge` style.
- On/off must be communicated by the switch state and text summary, not color
  alone. Use the theme’s primary/on colors and neutral off treatment.
- Preserve the Smart Flight dark-purple dashboard character when the user
  returns from Settings, but let Settings remain the clean responsive Material
  3 surface established by TASK-012.
- No in-app banner, snackbar, dialog, or dashboard card is required when a
  notification is shown, hidden, dismissed, or suppressed. Those are system
  surface/session events and must not disturb flight data or route state.

## 6. System notification design

The notification is a compact system surface, not Compose content.

### Identity and content

- Use the app identity and existing Smart Flight small notification icon
  convention, with a stable channel ID and stable notification ID.
- Channel name and all notification text are resource-backed. The channel is
  created idempotently before/with foreground service startup.
- Title: **Smart Flight is waiting for GPS signal**.
- Body: **Tap to return to Smart Flight** (modernized wording from the legacy
  “Tap to back to the app”; the meaning is unchanged).
- Use an immutable activity `PendingIntent` targeting the existing launcher
  `MainActivity`. Its flags must bring the existing activity to the foreground
  without making a duplicate monitoring session.
- The notification contains no raw coordinates, route endpoints, sensor
  readings, or claim of a successful fix.
- The notification uses the stable channel/ID for update and cancellation;
  do not create one notification per callback or session transition.

### Notification states

| State | User-visible result | Session result |
| --- | --- | --- |
| Eligible background waiting | One ongoing foreground-service notification is published promptly; waiting copy is visible if notification permission allows | One authoritative location/GNSS session remains active |
| Usable fix while backgrounded | Waiting notification is cancelled immediately | Stop background updates as required; retain fix/session fact for foreground handoff |
| Foreground return or notification tap | Waiting notification is cancelled; dashboard is visible | Reuse or transition to the existing foreground observation path, idempotently |
| Swipe dismissal | Notification disappears | Stop current background session only |
| Setting Off | No waiting notification | Stop an active background session when applied |
| Permission/location-service failure | No notification claim and no stale waiting notification | Release callbacks/service safely |
| Android notification permission denied | No user-visible notification; no crash or repeated request | Monitoring may continue while location permission and service conditions allow |

The service’s required foreground notification startup handling must be
separate from the waiting-copy eligibility decision. If Android requires a
foreground-service notification but `POST_NOTIFICATIONS` is denied, follow the
platform-compatible degraded path specified by the task; never show a false
in-app “notification displayed” state.

## 7. Session and lifecycle interaction contract

The implementation should expose a small testable session boundary rather than
having both `MainActivity` and the service register platform callbacks.

### Session states

Use equivalent implementation names as appropriate, but preserve these
observable states:

```text
Inactive
  └─ start from visible authorized activity → ForegroundObserving
ForegroundObserving
  ├─ usable fix → ForegroundFixed
  ├─ activity backgrounded, no fix, setting On → BackgroundWaiting
  └─ invalid permission/services/finish → Inactive
BackgroundWaiting
  ├─ usable fix → BackgroundFixedThenStopped
  ├─ foreground/tap → ForegroundObserving (notification cancelled)
  ├─ swipe dismissal or setting Off → Inactive
  └─ permission/services failure → Inactive
```

The exact state model may be internal, but callbacks and cancellation must be
idempotent. A session token/generation or equivalent guard should ensure that
late location/GNSS callbacks from an old run cannot publish a notification or
mutate the new foreground session.

### Activity flow

1. On a visible authorized dashboard, read the notification preference and
   refresh fine-location/location-service eligibility.
2. Start or reconcile one authoritative foreground session. Starting the
   service from this visible state is the foreground-service startup handoff.
3. On `onPause`/background without finishing, do not independently stop the
   location/GNSS session. Transfer its ownership/state to the service/session
   boundary. If no usable fix exists and the preference is On, publish the
   waiting notification after eligibility is rechecked.
4. On `onResume`, clear the stable notification ID, reconcile permission and
   location-service state, and return ownership to the existing foreground
   controllers without duplicate registration. A notification tap follows
   this same path.
5. On a usable fix, cancel waiting immediately. Do not show stale waiting UI
   after the fix, even if the callback races with background transition or
   notification publication.
6. On finish/destroy or invalid authorization, stop the session and unregister
   all location/GNSS callbacks. Do not leave a stale service binding.

Pressure, orientation, route computation, nearby-city lookup, horizon, and
map rendering remain foreground-only. The service/session forwards only the
existing location/GNSS information needed to keep the current controllers'
state derivation coherent; it does not duplicate flight calculations or add a
second dashboard pipeline.

### Preference changes

- Persist the new value in a dedicated namespaced store, separate from
  `display_behavior_*` and `display_units_*` keys. Missing, wrong-typed, or
  malformed values independently resolve to `true`.
- Update the row immediately after persistence. On Off, cancel any waiting
  notification and stop the active background session. On On, do not start a
  background service merely because the Settings row changed; honor it at the
  next eligible authorized monitoring session as required by the task.
- Read the store on recreation/resume so the row does not revert visually and
  unit/display settings are not overwritten.

## 8. Permission and failure UX

- Permission onboarding remains unchanged. Do not display this preference
  before precise location has been granted.
- Do not request `ACCESS_BACKGROUND_LOCATION`.
- If fine location is revoked or becomes approximate-only, or if location
  services are disabled, stop background monitoring and cancel the stable
  notification ID. Existing permission/location-services screens remain the
  remediation UI; this task adds no new error screen.
- If notification permission is denied on Android 13+, do not repeatedly ask,
  crash, or show an in-app substitute that implies the system notification is
  present. The preference can remain On and the row remains editable.
- Service startup failure is a safe non-monitoring outcome. Release the
  session and preserve the rest of the dashboard/settings state; do not show a
  stale waiting notification.
- Notification cancellation, dismissal, and first-fix completion are silent
  with respect to dashboard Compose state.

## 9. Responsive and accessibility behavior

- Portrait and landscape use the same scrollable Settings structure. The
  content column remains capped at 600 dp so labels do not become excessively
  wide; safe drawing insets remain applied.
- RTL uses logical start/end placement. The switch stays at logical end and
  text aligns to logical start; no left/right-specific layout assumptions.
- Large font scales must wrap the supporting explanation and label without
  clipping. The row may grow beyond 64 dp; the switch remains reachable.
- The row and switch retain a minimum 48 dp interaction target and visible
  Material pressed/focused states.
- Screen-reader output should announce the label, On/Off state, and the
  waiting-for-GPS-after-background explanation once. Do not depend on color,
  notification shade timing, or a transient snackbar to convey state.
- Notification title/body and channel name are localized resources. The
  notification’s tap target has a clear accessible label through its title/body
  and opens the existing dashboard.

## 10. Testing and verification expectations

Focused tests should cover:

- enabled default, independent malformed/missing preference fallback, round
  trip persistence, and preservation of existing display/unit keys;
- Settings row label, On/Off summary, merged switch semantics, explanation,
  minimum touch target, authorized-only visibility, RTL, and large-font
  wrapping where the configured environment supports it;
- session start/stop, idempotent registration, generation/late-callback
  protection, foreground/background transitions, and recreation;
- notification eligibility for no-fix/fix, setting state, fine versus
  approximate/revoked permission, disabled location services, and notification
  permission denial;
- notification tap, first-fix cancellation, swipe dismissal, setting changes,
  stable channel/notification identity, immutable launcher pending intent, and
  no duplicate service/listener/notification;
- manifest/API-level startup handling and absence of
  `ACCESS_BACKGROUND_LOCATION`.

Existing dashboard, map, route, unit, display, permission, and project checks
must continue to pass. The original XML preference screen, local broadcasts,
legacy service classes, storage permission, and legacy dependency artifacts
must not be copied into the rewrite.

## 11. Intentional deviations from the legacy UI

- The legacy checkbox is represented as a Material 3 switch with explicit
  On/Off state, merged row semantics, larger touch target, and resource-backed
  explanation.
- The legacy option sits in a broad General category. The rewrite places it
  in a focused Monitoring section between existing Display and Units sections
  to clarify that it affects background/system behavior. This remains inside
  the existing authorized Settings destination.
- The legacy body says “Tap to back to the app”. The rewrite uses the clearer
  “Tap to return to Smart Flight” while retaining the same action and intent.
- The rewrite does not mirror the legacy service’s local-broadcast/bound-
  service graph. One minimal authoritative session boundary is specified so
  the existing Compose controllers can be reused without duplicate callbacks.
- No waiting status is added to the dashboard. The task explicitly defines
  the notification as a system surface and the modern dashboard should not be
  polluted by a transient background-only card.

## 12. Unresolved questions

None from the product specification. The implementation must still choose the
smallest concrete adapter/session shape compatible with the existing
controllers and the target Android API level, while preserving the explicit
platform degradation rules for foreground-service startup and notification
permission denial.
