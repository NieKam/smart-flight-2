# TASK-014 — Authoritative background-monitoring lifecycle design

## 1. Design intent and scope

TASK-014 is a lifecycle and ownership correction to the TASK-013 monitoring
implementation. It has no new Compose screen, dashboard card, tracking mode, or
user-facing monitoring workflow. The visible design goal is continuity: the
existing Smart Flight dashboard and Settings surface must look and behave the
same while one monitoring session safely moves between the visible activity and
the foreground service.

The platform notification is the only user-visible surface added by this flow.
It is a temporary waiting affordance, not a second dashboard. The implementation
must expose it only when the existing TASK-013 contract says it is eligible.

## 2. Requirements translated into user experience

The following behavior is authoritative for the design:

| Situation | User-visible result |
| --- | --- |
| Authorized dashboard becomes visible | Dashboard immediately resumes its existing foreground observation path. The background-capable service/session is already started or reconciled before the activity can leave the foreground. No transient notification is shown merely because the activity resumed. |
| Authorized activity leaves the foreground, no usable fix exists, preference is On, and notification posting is permitted | One low-importance Location notification is shown with the existing resource-backed waiting title/body. It does not display coordinates, route details, satellite values, or sensor values. |
| Activity leaves the foreground but a usable fix already exists | No waiting notification is shown. The current background observation is ended according to TASK-013. |
| A usable fix arrives while backgrounded | The waiting notification is cancelled promptly and the background observation ends. A late callback from that run must not alter a later foreground session. |
| User taps the notification | The existing dashboard is brought to the foreground. The waiting notification is cancelled, the foreground path is restored, and the dashboard state is not reset or duplicated. |
| User swipes the notification away | Only the current background run stops. The persisted “Show background notification” preference and location permissions remain unchanged. A later authorized foreground session may start normally. |
| Preference is turned Off | The setting remains Off, the current background run is released, and the stable notification ID is cancelled. No new notification is posted until the user turns the preference On again. |
| Fine permission is revoked, permission becomes approximate-only, location services are disabled, registration fails, or service teardown occurs | Callbacks and the notification are released safely. The dashboard uses its existing permission/location/GNSS states and actions; no crash, stale waiting notification, or new product UI is introduced. |
| Android 13+ notification permission is denied | Monitoring remains a supported degraded state. Do not prompt repeatedly, claim that a waiting notification is visible, or show a replacement in-app banner/card. |
| Activity recreation or repeated foreground/background transitions | The user sees the same dashboard and Settings state. There is at most one visible waiting notification with the stable ID; no duplicate cards, flashes of a reset dashboard, or duplicate service indication occur. |

The waiting notification continues to use the current rewrite resources and
meaning:

- title: `Smart Flight is waiting for GPS signal`
- body/action meaning: `Tap to return to Smart Flight`
- channel label: `Location`

The notification tap opens the existing `MainActivity`. Dismissal is a stop
action only; it is not a preference toggle and must not request a permission.

## 3. Existing visible surfaces to preserve

### Dashboard

The dashboard remains the existing `GnssStatusScreen` composition:

1. A compact app header with centered `Smart Flight` branding and Settings/About
   actions, collapsing actions into the existing overflow treatment on narrow
   widths.
2. A vertically scrollable card column in the established order: GNSS status,
   Flight parameters, Course, Horizon, Nearby city, Route, and offline Map.
3. Existing loading, waiting, available, unavailable, error, retry, route-picker,
   and map states.

Preserve the current visual rhythm: approximately 12dp horizontal content
padding, 12dp card separation/bottom spacing, a 600dp maximum card width, and a
minimum 56dp header height. Preserve the existing card rounded corners,
elevation, text hierarchy, action placement, map controls, route overlays, and
offline-map behavior. A monitoring transition must not recompose into duplicate
cards or clear route/map state.

The visual language remains the existing dark-purple page background, purple
cards, cyan actions, and light lavender text. The legacy palette reference is
`purple_dark` (#484685), `purple_main` (#5b5999), `cyan_main` (#25e5fe),
`text_color_dark` (#a1a0c4), and `text_color_light` (#d9d9ed). These values are
reference evidence, not a request to add a new palette or replace the current
theme implementation.

### Settings

The existing overlay Settings screen remains in place so the AndroidView-backed
map and dashboard composition are not unnecessarily destroyed. Preserve:

- Top app bar with Back affordance and the `Settings` title.
- Scrollable, centered content column with a 600dp maximum width.
- Existing Display, Monitoring, and Units section order.
- Existing “Show background notification” row under Monitoring.
- On/Off switch semantics, default On, explanatory summary, divider, and full-row
  click target.
- Authorized-dashboard-only visibility; it must not appear on permission
  onboarding or as a new dashboard control.

The switch row should retain its current minimum 64dp height and merged
accessibility semantics: label, current On/Off state, and explanation are
announced as one switch. Turning it Off reconciles the service immediately, but
the row itself must not show a transient error or imply that permissions changed.

## 4. Layout and responsive behavior

No new layout is needed for TASK-014. The implementation should preserve the
following behavior while lifecycle ownership changes:

- Portrait: retain the single full-width scroll column and current card order.
- Landscape: retain the same scrollable hierarchy and card widths; do not create a
  second monitoring/dashboard pane. Respect the existing display preference for
  portrait versus sensor orientation.
- Narrow widths below the existing header breakpoint: keep the compact “More
  options” action so Settings/About remain reachable without crowding the title.
- Wide widths: keep cards centered with the existing max width rather than
  stretching text and controls across the whole display.
- Insets: retain edge-to-edge handling, safe drawing padding, and existing map
  viewport behavior.

The notification is an Android system surface and must remain legible at the
system’s supported font scale. Use the existing small plane/app icon and
resource-backed strings; do not put dynamic flight data into its title/body.

## 5. Monitoring state model as a visible-state contract

The internal lifecycle may use a session generation/token and an authoritative
owner, but the UI should be driven by stable outcomes rather than implementation
details. The conceptual states are:

| Conceptual state | Dashboard | Notification |
| --- | --- | --- |
| Foreground observing | Existing live GNSS/location cards | Cancelled |
| Background waiting | Activity not visible | Posted only when preference, precise permission, location services, and notification posting eligibility all allow it |
| Background fixed / stopped | On next foreground return, reconcile without reset | Cancelled |
| Ineligible or failed | Existing permission/location/GNSS state handles the visible explanation when the activity is visible | Cancelled; no stale notification |
| Dismissed current run | Unchanged while backgrounded; later foreground can restart | Cancelled and not immediately reposted for the dismissed run |

The service/session boundary must be invisible in the dashboard. Location fixes
and GNSS status events must enter the existing controller path exactly once, so
the same cards update whether the event originated while the activity is visible
or while the service owns the active session. A generation change invalidates all
late callbacks before they can update a card, show/cancel a notification, or set
the next session’s “fixed” state.

## 6. Interaction and transition details

### Foreground return and notification tap

Treat both as an idempotent foreground reconciliation. Clear the waiting
notification, mark the activity visible, attach the existing foreground
controllers to the authoritative session, and retain already-known usable-fix
state. Repeated taps/resumes must not create a second service, registration,
notification, or observer.

### Background transition

The service/session must be started or reconciled from the authorized visible
lifecycle. Leaving the foreground transfers visibility to the already-running
authoritative session; it must not stop callbacks first and then attempt a new
background start. If the waiting conditions are met, update the one stable
notification in place.

### Fix and eligibility loss

A fix cancels waiting immediately. Permission/provider/setting loss, callback
registration failure, service start failure, and destruction all take the same
safe visible outcome: unregister exactly what registered, cancel the stable
notification, and leave the activity able to recover on a later authorized
resume. Partial registration must never leave the other callback or a stale
notification alive.

### Dismissal

The notification delete action targets the current session only. It must not
rewrite preferences, revoke location access, or create a new notification loop.
The next eligible foreground session may reconcile normally.

## 7. Accessibility and communication

- Keep the Settings switch’s merged row semantics, explicit `Role.Switch`, and
  On/Off state description.
- Keep existing content descriptions and action hints for Settings, About,
  retries, map controls, route controls, and permission/location actions.
- Preserve polite live-region behavior for changing GNSS status; lifecycle events
  must not repeatedly announce the same state because of duplicate callbacks.
- Maintain at least 48dp touch targets for header actions, Settings rows, and
  notification-related entry points controlled by Android.
- Do not expose precise location, route information, or raw sensors in the
  notification, accessibility text for the notification, or a new custom
  surface.
- Use the existing string resources rather than concatenating state text in the
  UI. The waiting notification remains meaningful when read without the app
  context.

## 8. Loading, empty, and error behavior

TASK-014 does not add monitoring-specific Compose loading or error cards. Keep
the existing dashboard states unchanged:

- GNSS waiting continues to communicate that a signal/position is pending.
- Location services disabled continues to offer the existing system-settings
  action while visible.
- GNSS/registration errors continue to use the existing retry action.
- Permission onboarding and permission-needed states remain the existing screens.
- Offline map failure remains the existing map-unavailable state and retry path.

When the activity is not visible, failures are handled silently at the platform
session level by cleanup. On return, the existing controllers reconcile and
render their normal waiting/error/permission state; no new snackbar, dialog, or
dashboard card is introduced by this task.

## 9. Observed legacy behavior versus design decisions

### Observed from the original application

- `LocationService` owns location work and shows a waiting notification when the
  app backgrounds before a location is obtained.
- The notification opens `MainActivity`; dismissal requests a GPS stop.
- `AppVisibilityManager` supplies foreground/background signals.
- The notification preference defaults to enabled.
- The legacy dashboard is a dark-purple, vertically scrolling card stack with
  map and flight-information cards. Its legacy card metrics include roughly
  160dp card height, 12dp card margin, 10dp corner radius, and 4dp elevation.

### Required by TASK-014/TASK-013

- Preserve the existing rewrite’s Settings row, notification meaning, stable
  notification/channel identity, permissions, dashboard hierarchy, map/route
  state, and foreground calculations.
- Use one authoritative callback owner, retain usable-fix state across handoff,
  make teardown and reconciliation idempotent, and reject stale callbacks.
- Handle approximate-only permission, notification permission denial, provider
  changes, registration failures, recreation, and repeated transitions without a
  crash or stale notification.

### Design decisions introduced here

- No new in-app monitoring indicator: the task is intentionally invisible in the
  dashboard and avoids duplicating the platform notification.
- Notification content is treated as a stable state announcement, not a live
  telemetry surface. This improves privacy and keeps wording consistent across
  lifecycle transitions.
- Foreground return and notification tap share one reconciliation outcome. This
  reduces visual flicker and makes repeated lifecycle events indistinguishable
  to the user.
- The rewrite’s accessible Compose Settings row and responsive header are the
  source of truth where they improve on the legacy preference screen. Legacy
  fixed card dimensions and toolbar details are reference characteristics only,
  not constraints to reproduce literally.

## 10. Implementation-facing acceptance checklist

The Developer should verify the visible contract alongside the lifecycle tests:

- No new Compose screen, card, dashboard control, or unrelated resource is
  introduced.
- Existing dashboard card order, map placement, route overlays, visual tokens,
  and foreground calculations are unchanged.
- Settings still shows the authorized-only notification switch, default On,
  with its current explanation and accessibility state.
- Waiting notification appears only for an eligible background-without-fix run,
  uses the existing title/body/channel, opens the dashboard, and is dismissible.
- A fix, foreground return, tap, preference Off, permission/provider loss,
  registration failure, notification denial, service teardown, and recreation
  leave no stale notification or duplicated visible state.
- At most one location callback registration, one GNSS registration, one service
  session, and one stable notification ID are active for a run.
- Focused tests cover the above transitions and stale-generation rejection;
  visual/instrumentation checks cover notification tap/dismissal where the
  configured Android environment supports them.

## 11. Inspected references

Current rewrite:

- `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- `app/src/main/java/kniezrec/com/flightinfo/monitoring/BackgroundMonitoringBridge.kt`
- `app/src/main/java/kniezrec/com/flightinfo/monitoring/BackgroundMonitoringSession.kt`
- `app/src/main/java/kniezrec/com/flightinfo/monitoring/LocationGnssMonitoringSession.kt`
- `app/src/main/java/kniezrec/com/flightinfo/monitoring/LocationForegroundService.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/settings/UnitSettingsScreen.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/theme/Color.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/theme/Theme.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/AndroidManifest.xml`
- `app/src/test/java/kniezrec/com/flightinfo/monitoring/BackgroundMonitoringTest.kt`
- `app/src/test/java/kniezrec/com/flightinfo/monitoring/LocationGnssMonitoringSessionTest.kt`

Original application:

- `app/src/main/java/kniezrec/com/flightinfo/services/location/LocationService.kt`
- `app/src/main/java/kniezrec/com/flightinfo/common/NotificationBroadcastReceiver.kt`
- `app/src/main/java/kniezrec/com/flightinfo/common/AppVisibilityManager.kt`
- `app/src/main/java/kniezrec/com/flightinfo/settings/FlightAppPreferences.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/layout/content_main.xml`
- `app/src/main/res/layout/satellites_card_layout.xml`
- `app/src/main/res/layout/map_card_layout.xml`
- `app/src/main/res/xml/app_preferences_layout.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/main/res/values/strings.xml`
- `promo/promo.png` (1024×500 dashboard visual reference; visual inspection was
  attempted, while source dimensions and surrounding resources were verified)

## 12. Unresolved questions

None. TASK-014 and the TASK-013 contract define the required visible behavior.
If Android platform restrictions make a particular notification/foreground
service transition impossible on a supported API level, escalate that concrete
platform constraint to the Architect rather than changing the product behavior
or adding a substitute UI surface.
