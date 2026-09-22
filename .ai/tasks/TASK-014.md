# TASK-014 — Complete authoritative background-monitoring lifecycle

## Status

READY_FOR_DESIGN

## Goal

Complete the TASK-013 background-monitoring implementation so one authoritative, lifecycle-safe location/GNSS session is shared across the visible dashboard and the location foreground service, with focused coverage for its required transitions and failure paths.

## Context

TASK-013 added the optional **Show background notification** setting and a location foreground service intended to keep monitoring alive while the authorized dashboard is backgrounded before a usable position is received. The current implementation is on `main`, but the TASK-013 review artifacts identify blocking correctness gaps: the service/session ownership and activity handoff are not fully authoritative, and the focused tests do not exercise the required lifecycle and failure behavior.

This task is a bounded completion/fix for TASK-013. It must preserve TASK-013’s existing user-visible contract, settings behavior, notification copy, permissions, and foreground dashboard behavior. It is not a new background-tracking product feature.

## Original Application

The original implementation keeps location work in `services/location/LocationService.kt`. It shows a waiting notification when the app leaves the foreground before a location has been obtained, clears the notification when the app returns, and stops updates when the notification is dismissed. `common/NotificationBroadcastReceiver.kt` handles notification dismissal, while `common/AppVisibilityManager.kt` supplies the foreground/background signal. `FlightAppPreferences.kt` reads the notification preference, which defaults to enabled in `res/xml/app_preferences_layout.xml`.

The legacy behavior is reference evidence for the already-defined TASK-013 contract: background waiting is conditional, a first usable fix ends the waiting state, tapping the notification returns to `MainActivity`, and dismissal stops the current run. The legacy service and local-broadcast architecture must not be copied.

Relevant original files:

- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/services/location/LocationService.kt`
- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/common/NotificationBroadcastReceiver.kt`
- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/common/AppVisibilityManager.kt`
- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/settings/FlightAppPreferences.kt`
- `/home/ai-dev/smart-flight/app/src/main/res/xml/app_preferences_layout.xml`
- `/home/ai-dev/smart-flight/app/src/main/res/values/strings.xml`
- `/home/ai-dev/smart-flight/promo/promo.png` (1024 × 500 dashboard visual reference)

## Current Application

The rewrite contains the TASK-013 components:

- `MainActivity.kt` starts the service from `onResume`, marks the activity visible/backgrounded through `BackgroundMonitoringBridge`, and currently stops the foreground controllers in `onPause`.
- `monitoring/LocationForegroundService.kt` creates the stable notification/channel, checks fine permission and location services, owns a `LocationGnssMonitoringSession`, and periodically/provider-change checks eligibility.
- `monitoring/LocationGnssMonitoringSession.kt` wraps `AndroidFlightLocationPlatform` and `AndroidGnssStatusPlatform` and guards callbacks with a generation value.
- `monitoring/BackgroundMonitoringBridge.kt` forwards location/GNSS events to the activity controllers and tracks visibility/fix state.
- `monitoring/BackgroundMonitoringSession.kt` contains a small abstract state model.
- `monitoring/BackgroundNotificationPreferences.kt` and `ui/settings/UnitSettingsScreen.kt` implement the persisted authorized-only notification setting.

The current review findings show that the service/activity boundary must be made one authoritative session rather than leaving callback ownership or state handoff ambiguous. The focused monitoring tests currently cover preference persistence, a small abstract state machine, and low-level callback registration, but not the implemented bridge/service lifecycle, notification eligibility, transitions, or failure cleanup.

## Functional Requirements

- Preserve all TASK-013 user-visible behavior and copy: the authorized Settings control defaults On; the waiting notification is conditional; tapping it returns to the existing dashboard; swiping it away stops only the current background run; a usable fix cancels waiting; and permission/service/settings changes do not crash the app.
- Start or reconcile the single background-capable monitoring session while the authorized activity is visible, satisfying Android foreground-service start restrictions. Do not wait until `onPause` to create the service.
- Transfer or retain the same session when the activity becomes backgrounded. The transition must not stop the session first and then attempt a background service start.
- Ensure exactly one authoritative owner registers location and GNSS callbacks for a session. The service/session boundary must forward the same location fixes and GNSS status events used by the existing `FlightParametersController` and `GnssStatusController`, without a second parallel raw listener.
- Preserve the current usable-fix state across the activity/service handoff. A fix received in the background must cancel waiting notification behavior and stop background observation according to TASK-013, while a later foreground session can start cleanly.
- Make foreground return, notification tap, activity recreation, repeated resume/pause transitions, setting changes, service restart, and teardown idempotent. None may produce duplicate callbacks, services, notifications, or stale session mutations.
- Use a session generation/token or equivalent guard so late callbacks from a stopped service/session cannot update the current activity state, publish a notification, or mark a new session fixed.
- When fine permission is revoked or becomes approximate-only, location services are disabled, notification monitoring is turned off, registration fails, or the service is destroyed, unregister all callbacks and cancel the stable notification safely.
- Keep Android 13+ notification-permission denial as a supported degraded state. Do not repeatedly prompt, crash, or claim that a waiting notification is visible when it cannot be posted.
- Keep TASK-013’s notification dismissal semantics: dismissal stops the current background session only and does not change the persisted preference or permission state.

## Technical Requirements

- Reuse the existing `AndroidFlightLocationPlatform`, `AndroidGnssStatusPlatform`, `FlightParametersController`, `GnssStatusController`, `BackgroundMonitoringBridge`, and `LocationGnssMonitoringSession` where their contracts are suitable. Introduce only the smallest testable session/ownership changes needed to establish one callback path.
- Define a clear ownership contract between `MainActivity`, the service, and the shared session boundary. The activity must not independently keep a duplicate location/GNSS registration active while the service owns the background session.
- Keep platform registration and unregistration idempotent and lifecycle-aware. Unregister the exact callbacks that were registered, including partial-registration failure.
- Reconcile eligibility at startup and while backgrounded, including provider changes, permission changes, setting changes, and registration failures. Eligibility loss must release resources and cancel the stable notification ID.
- Keep the existing foreground-service declaration, location permissions, notification channel/ID, immutable pending intents, offline behavior, package identity, and minimum SDK constraints unless a platform-required correction is needed.
- Add focused fake-platform/session/bridge tests for one-registration ownership, visible-to-background handoff, foreground return/tap, usable-fix cancellation, dismissal, setting-off, permission/location-service loss, notification-permission denial, startup/registration failure, recreation, and stale-callback generation guards.
- Add platform-facing or instrumentation coverage for service/notification behavior where the configured environment supports it. Tests must not require network access.
- Do not alter the canonical flight-parameter calculations or add a second controller pipeline in the service. Events must continue through the existing state path.

## UI Requirements

- Do not add a new Compose screen, dashboard card, or system surface.
- Preserve the existing Settings row **Show background notification**, its On/Off semantics, explanation, accessibility behavior, and authorized-dashboard-only visibility.
- Preserve the existing notification title/body meaning and resource-backed strings. The notification must remain a platform surface and must not expose coordinates, route details, or raw sensor values.
- Preserve the existing dashboard visual hierarchy, map placement, controls, card order, and dark-purple/purple/cyan visual language. Lifecycle correction must not cause visible duplicate cards, stale waiting states, or unexpected dashboard resets.

## Acceptance Criteria

- [ ] An authorized visible dashboard starts or reconciles one background monitoring service/session before the activity can background; no service start is deferred exclusively to `onPause`.
- [ ] The visible-to-background transition retains one authoritative location/GNSS registration and does not create a stop/start gap or duplicate listener.
- [ ] Location fixes and GNSS status events received by the service-owned session reach the existing activity/controller state path exactly once.
- [ ] Returning from background or tapping the notification cancels waiting notification state and restores the foreground path without duplicate services, callbacks, or dashboard observers.
- [ ] A usable background fix cancels the waiting notification, stops background observation as specified by TASK-013, and prevents late callbacks from mutating a later session.
- [ ] Swiping away the notification stops only the current background session; the persisted setting and permissions remain unchanged, and a later authorized foreground session can start.
- [ ] Turning the setting off, revoking fine permission, selecting approximate-only permission, disabling location services, or losing required notification/service eligibility releases callbacks and leaves no stale waiting notification.
- [ ] Callback registration failure, partial registration failure, service startup failure, and notification-permission denial are handled without a crash or repeated notification/permission loop.
- [ ] Activity recreation and repeated foreground/background transitions leave at most one active location registration, one GNSS registration, one service session, and one stable notification ID.
- [ ] Focused automated tests cover the lifecycle, eligibility, transition, duplicate-prevention, failure, notification, and stale-callback requirements above; existing tests remain passing where the configured environment supports execution.
- [ ] TASK-013 Settings behavior, notification copy, permissions, dashboard cards, route/map state, unit/display settings, and foreground calculations remain unchanged.
- [ ] No network request, `ACCESS_BACKGROUND_LOCATION`, new tracking feature, legacy service/broadcast architecture, third-party dependency, or unrelated UI is added.

## Implementation Plan

1. Review the current service, bridge, activity lifecycle, platform adapters, and tests; document the single session owner and event handoff contract.
2. Refactor the smallest necessary monitoring boundary so service and foreground activity reconciliation share one authoritative registration and generation state.
3. Start/reconcile the service from the visible authorized lifecycle and implement idempotent foreground/background, tap, dismissal, setting, permission, provider, and teardown transitions.
4. Ensure location/GNSS events and usable-fix state are forwarded through the existing controller path without duplicated calculations or stale callbacks.
5. Add focused fake-platform/session/bridge tests and platform-facing coverage for notification/service behavior where available.
6. Run the configured project checks and verify that TASK-013’s existing Settings and dashboard behavior remains intact.

## Files / Components Likely Affected

- `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- `app/src/main/java/kniezrec/com/flightinfo/monitoring/BackgroundMonitoringBridge.kt`
- `app/src/main/java/kniezrec/com/flightinfo/monitoring/BackgroundMonitoringSession.kt`
- `app/src/main/java/kniezrec/com/flightinfo/monitoring/LocationGnssMonitoringSession.kt`
- `app/src/main/java/kniezrec/com/flightinfo/monitoring/LocationForegroundService.kt`
- `app/src/main/java/kniezrec/com/flightinfo/flight/FlightParametersController.kt` and/or its existing platform boundary, only if required for the shared event handoff
- `app/src/main/java/kniezrec/com/flightinfo/gnss/GnssStatusController.kt` and/or its existing platform boundary, only if required for the shared event handoff
- `app/src/test/java/kniezrec/com/flightinfo/monitoring/BackgroundMonitoringTest.kt`
- `app/src/test/java/kniezrec/com/flightinfo/monitoring/LocationGnssMonitoringSessionTest.kt`
- New focused monitoring tests under the existing test packages

This list is guidance. Do not change unrelated map, route, settings, About, permission, or dashboard components.

## Reusable Existing Libraries / Components

- Rewrite platform/session components listed above; these are the required reuse points for the callback and lifecycle path.
- Android `Service`, foreground-service APIs, `NotificationManager`, `NotificationChannel`, immutable `PendingIntent`, provider-change broadcasts, and lifecycle callbacks already used by TASK-013.
- Existing Compose Material 3 Settings surface and `BackgroundNotificationPreferencesStore`; no new UI or preference library is needed.
- Original behavioral references: `LocationService`, `NotificationBroadcastReceiver`, `AppVisibilityManager`, and `FlightAppPreferences`.
- Existing bundled osmdroid archive and map components are unrelated and must remain unchanged.

## Risks and Edge Cases

- Android may deliver callbacks after unregistering or after a service/activity recreation; generation checks must reject them.
- `onResume`, `onPause`, `onStart`, `onStop`, notification taps, and service callbacks can interleave. State transitions must be idempotent and must not rely on callback ordering.
- A fix can arrive between eligibility evaluation and notification publication; eligibility and fix state must be rechecked before publishing and the notification must be cancelled immediately on fix.
- Location services or fine permission can change while the app is backgrounded and without an activity callback; the service must detect and clean up safely.
- The Android process may be killed or the service may fail to start. This task requires graceful cleanup and a recoverable later foreground session, not indefinite persistence after process death.
- Notification permission denial means the foreground-service platform requirements and user-visible waiting-copy behavior may differ by Android version; do not fake a visible notification in app state.
- Configuration changes must not reset persisted preferences or create a second service/session while the old one is still active.
- The promo image is only a visual continuity reference; no pixel-perfect redesign is part of this task.

## Open Questions

None. The required behavior is defined by TASK-013 and its review findings; this task resolves implementation ownership and coverage without changing product scope.
