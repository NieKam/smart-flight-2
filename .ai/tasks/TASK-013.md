# TASK-013 — Add optional background flight monitoring notification

## Status

READY_FOR_DESIGN

## Goal

Allow an authorized Smart Flight user to keep location/GNSS monitoring alive when the dashboard is temporarily backgrounded, and provide an optional system notification while the app is waiting for its first usable position.

## Context

The rewritten app currently observes GNSS, location, pressure, orientation, and route-related inputs only while `MainActivity` is in the foreground. Its `onPause`/`onStop` lifecycle path stops those observations, so leaving the dashboard interrupts the flight-monitoring session. The original application kept a bound location service available beyond the activity lifecycle and used a configurable notification to tell the user that it was still waiting for a GPS fix.

This task restores that specific user-visible capability using the rewrite’s modern Kotlin/Compose architecture. It must not turn the existing dashboard into a network-backed tracker or reproduce the legacy service graph literally. The feature is useful during a flight when the user briefly opens another app, locks the screen, or returns to Smart Flight from the notification.

## Original Application

The original preference screen (`/home/ai-dev/smart-flight/app/src/main/res/xml/app_preferences_layout.xml`) contains a `Show notification` checkbox with a default value of `true`. `FlightAppPreferences.canShowNotification()` reads that value from the legacy shared-preferences file.

`app/src/main/java/kniezrec/com/flightinfo/services/location/LocationService.kt` owns location and satellite callbacks, starts updates when bound, and reacts to app foreground/background lifecycle broadcasts. When the app goes to the background and no location has yet been obtained, it shows a notification if the preference allows it. The notification opens `MainActivity`; dismissing it is handled by `common/NotificationBroadcastReceiver.kt` and requests that GPS updates stop. Returning to the app clears the notification and restarts updates. If a location was already obtained, the legacy service stops updates instead of showing the waiting notification.

The legacy notification uses the strings `app_background_notification_title` (`Smart Flight is waiting for GPS signal`), `app_background_notification_content` (`Tap to back to the app`), and the `Location` notification channel. The legacy manifest declares `LocationService` and `NotificationBroadcastReceiver`, but its `WRITE_EXTERNAL_STORAGE`, old local-broadcast, and pre-modern service implementation are not to be copied.

Relevant legacy references:

- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/services/location/LocationService.kt`
- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/common/NotificationBroadcastReceiver.kt`
- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/common/AppVisibilityManager.kt`
- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/settings/FlightAppPreferences.kt`
- `/home/ai-dev/smart-flight/app/src/main/res/xml/app_preferences_layout.xml`
- `/home/ai-dev/smart-flight/app/src/main/res/values/strings.xml`

The original visual reference is `/home/ai-dev/smart-flight/promo/promo.png` (1024 × 500). It shows the same dark-purple Smart Flight dashboard language; the system notification itself is platform-rendered and does not need to reproduce the promo layout.

## Current Application

`MainActivity.kt` owns the rewrite’s foreground controllers and sets `isForeground = false` in `onPause`, stopping GNSS, pressure, route, and horizon observation. `FlightParametersController` owns the current Android location listener and feeds `FlightParametersState`, map state, nearby-city state, course GPS bearing, and route calculations through the activity callback path. There is no Android `Service`, notification receiver, notification channel, background preference, or `POST_NOTIFICATIONS` declaration.

The authorized dashboard is entered only after precise location permission. Settings is already reachable through `UnitSettingsScreen` and currently contains the unit controls plus the display controls from TASK-012. The new notification control belongs in the existing authorized Settings surface; permission onboarding must remain unchanged.

Existing foreground behavior and state models are in:

- `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- `app/src/main/java/kniezrec/com/flightinfo/flight/FlightParametersController.kt`
- `app/src/main/java/kniezrec/com/flightinfo/flight/AndroidFlightLocationPlatform.kt`
- `app/src/main/java/kniezrec/com/flightinfo/gnss/GnssStatusController.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/settings/UnitSettingsScreen.kt`
- `app/src/main/AndroidManifest.xml`

## Functional Requirements

- Add a persisted authorized-dashboard setting named **Show background notification**, defaulting to enabled to match the original application.
- When precise location is granted and the dashboard session is active, start the background monitoring session while the activity is visible so Android’s foreground-service start restrictions are satisfied.
- When the activity is backgrounded without finishing, keep the location/GNSS observation session alive through a location foreground service. The service must publish a user-visible notification promptly after it starts.
- The notification must be shown only when all of these conditions hold: fine location remains granted, background monitoring is active, no usable location fix has been received for the current session, and the setting is enabled. Its title/body must communicate that Smart Flight is waiting for a GPS signal and provide an action to return to the app.
- A notification tap must reopen the existing `MainActivity` and return the user to the dashboard without creating a duplicate monitoring session.
- When the activity returns to the foreground, clear the waiting notification and resume/reuse the existing foreground observation path. Do not display stale “waiting” UI after a usable fix has already been received.
- When a usable location fix is received while backgrounded, stop the waiting notification and stop background updates according to the legacy behavior; returning to the app must be able to start a fresh foreground observation session.
- Swiping away the notification must stop the background location/GNSS session for the current run. It must not revoke permission, change the persisted setting, or prevent the user from starting a new session by reopening the app.
- Turning the setting off must prevent future background waiting notifications and stop an already-running background monitoring session when the change is applied. Turning it on must take effect for the next authorized monitoring session without requiring an app reinstall.
- If the user denies Android notification permission, the app must not crash or repeatedly prompt. Location monitoring may continue while permitted, but no notification may be claimed as displayed; the setting remains available for the user to change.
- If fine location is revoked, approximate-only, or device location services are disabled, stop background monitoring and do not show the notification. Existing permission/location-services screens remain the source of user remediation.
- Process/activity recreation must not register duplicate location or GNSS callbacks, create duplicate notifications, or leave a stale service binding.

## Technical Requirements

- Use an Android location foreground service (declared with the location foreground-service type) and the existing platform location/GNSS APIs. Start it from the visible authorized activity and call `startForeground` within the platform-required startup window.
- Add only the platform permissions required for this feature: `FOREGROUND_SERVICE`, the location foreground-service type permission required by the target API, and `POST_NOTIFICATIONS` for Android 13+. Do not add background-location permission; this task must not request `ACCESS_BACKGROUND_LOCATION`.
- Keep all sensor/location-to-dashboard state derivation in the existing controllers or small testable service/session adapters. Do not duplicate the flight-parameter calculations in the service. The service must expose or forward the same `FlightLocationFix`/GNSS information needed by the existing foreground path.
- Use the existing `AndroidFlightLocationPlatform`, GNSS platform abstraction, `FlightParametersController`, and lifecycle state as reuse points. Introduce only the smallest service/session boundary needed to keep observation alive when the activity is not visible.
- Use a namespaced `SharedPreferences` key and the existing preference-store convention for the notification setting. Missing or malformed values must independently resolve to the enabled default.
- Use a stable notification channel ID and notification ID. Create the channel idempotently, use immutable pending intents, and make the notification tap target the existing launcher activity.
- Handle Android API-level differences for notification permission and foreground-service startup explicitly. A notification permission denial is a supported degraded state, not a fatal service error.
- Keep the implementation offline. Do not add a network provider, analytics, remote sync, wake-lock, map download, or third-party service library.
- Preserve TASK-012 display settings, unit settings, route/map state, and the existing foreground-only UI lifecycle. The notification is a system surface, not a new Compose dashboard card.
- All notification titles, content, Settings labels/summaries, permission rationale text, and accessibility descriptions must be resource-backed.

## UI Requirements

- Add a separate row in the existing Settings display/general section labeled **Show background notification** with an accessible two-state control and a concise On/Off summary.
- Explain in the row summary or supporting text that the notification appears while Smart Flight is waiting for a GPS position after the app leaves the foreground. Do not imply that the notification guarantees a fix or continuous service availability.
- Keep the row visually consistent with the existing purple/dark-purple Compose Settings surface, responsive column width, 48 dp minimum touch target, RTL behavior, and large-font wrapping.
- Do not expose this control on permission onboarding or when the user has not reached the authorized dashboard.
- The notification must use the app identity and a clear waiting-for-GPS message; it must not expose raw coordinates, route details, or misleading aviation status.

## Acceptance Criteria

- [ ] An authorized user sees **Show background notification** in Settings, with an On/Off state, and a fresh install defaults to On.
- [ ] The setting persists across activity recreation and a later launch; malformed or missing storage falls back to On without changing the other display/unit settings.
- [ ] With fine location granted, the setting enabled, and no usable fix yet, leaving the dashboard starts/retains one location foreground-service session and presents a waiting-for-GPS notification that opens Smart Flight when tapped.
- [ ] Returning to Smart Flight clears the waiting notification, does not create duplicate callbacks/services, and resumes the existing foreground observation path.
- [ ] Receiving a usable location fix while the app is backgrounded removes the waiting notification and stops background updates according to the defined session behavior.
- [ ] Swiping away the notification stops background monitoring for that run; reopening Smart Flight can start a new authorized foreground session.
- [ ] Disabling the setting prevents a waiting notification and stops an active background session; enabling it applies to a subsequent authorized session.
- [ ] On Android 13+ with notification permission denied, the service/session fails gracefully without a crash, notification spam, or false in-app claim that a notification is visible.
- [ ] Revoked/approximate-only location permission or disabled location services stops background monitoring and produces no waiting notification.
- [ ] Activity recreation, repeated foreground/background transitions, and notification taps do not leak duplicate location/GNSS listeners, services, receivers, or notifications.
- [ ] The feature works without network access and does not add `ACCESS_BACKGROUND_LOCATION`, map downloads, or background sensor services unrelated to location/GNSS monitoring.
- [ ] Focused tests cover preference defaults/invalid values, session start/stop and duplicate prevention, notification eligibility, fix/dismissal/foreground transitions, permission/service failure paths, and Settings semantics where the configured test environment supports them.
- [ ] Existing project checks continue to pass.

## Implementation Plan

1. Inventory the current activity lifecycle, location/GNSS controller ownership, display preference store, and notification-capable platform APIs; define the service/session contract before changing lifecycle behavior.
2. Add the namespaced notification preference model/store and the resource-backed Settings row, including persistence and Compose semantics tests.
3. Implement the minimal foreground location service/session adapter, notification channel/content, launcher pending intent, and notification-dismiss handling.
4. Move or bridge the existing location/GNSS observation ownership so foreground and background transitions share one session without duplicate callbacks or duplicated calculations.
5. Wire authorized activity lifecycle, preference changes, permission/service availability, first-fix completion, foreground return, and notification dismissal to the session.
6. Add manifest declarations and API-level handling for foreground location and Android 13+ notification permission without requesting background location.
7. Add focused unit and instrumentation/Compose coverage, then run the configured checks in an Android SDK environment.

## Files / Components Likely Affected

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- `app/src/main/java/kniezrec/com/flightinfo/flight/FlightParametersController.kt`
- `app/src/main/java/kniezrec/com/flightinfo/flight/AndroidFlightLocationPlatform.kt`
- `app/src/main/java/kniezrec/com/flightinfo/gnss/GnssStatusController.kt` and/or its platform adapter
- `app/src/main/java/kniezrec/com/flightinfo/ui/settings/UnitSettingsScreen.kt`
- New notification preference/store and foreground location service/session components under existing packages
- `app/src/main/res/values/strings.xml`
- Focused tests under `app/src/test` and `app/src/androidTest`

This list is guidance; do not modify unrelated map, route, unit, About, or display-setting behavior.

## Reusable Existing Libraries / Components

- Rewrite: `FlightParametersController`, `AndroidFlightLocationPlatform`, GNSS platform/controller, `LocationPermissionStateController`, `DisplayPreferencesStore`, and existing Compose Material 3 Settings controls.
- Android platform: `Service`, `NotificationManager`, `NotificationChannel`, `NotificationCompat` if already available through the current AndroidX stack, immutable `PendingIntent`, and foreground-service APIs.
- Legacy behavioral references: `LocationService`, `NotificationBroadcastReceiver`, `AppVisibilityManager`, `FlightAppPreferences`, and notification strings.
- Do not reuse the legacy `LocalBroadcastManager`, `WRITE_EXTERNAL_STORAGE`, `jsi-1.0.0.jar`, `trove4j-2.0.2.jar`, or legacy service classes by copying them into the rewrite.

## Risks and Edge Cases

- Android foreground-service and notification-permission rules vary by API level. The implementation must start from a visible activity, handle startup failure, and avoid assuming that a notification can always be shown.
- `POST_NOTIFICATIONS` denial can make a foreground service less visible to the user; the app must avoid pretending the notification exists and must not repeatedly request permission.
- A service and activity can both observe the same platform source accidentally. One authoritative session and idempotent registration/unregistration are required.
- A notification tap, activity recreation, configuration change, or Settings overlay can produce repeated lifecycle events. These must not reset a valid fix or multiply callbacks.
- A fix may arrive between the background transition and notification publication. Re-check eligibility before publishing and cancel the notification immediately when a fix arrives.
- Permission revocation and location-service disablement can occur while the app is backgrounded. Stop safely and release callbacks without crashing.
- Swipe dismissal is user intent to stop the current background session, not a permanent preference change.
- This task does not promise indefinite operation after the operating system kills the process; durable reboot/startup tracking and battery-policy exemptions are outside scope.

## Open Questions

None. The original default, waiting-notification behavior, dismissal behavior, and preference location are directly observable in the legacy implementation; Android platform constraints define the required graceful degradation for notification permission and foreground-service startup.

## Out of Scope

- Requesting `ACCESS_BACKGROUND_LOCATION` or tracking location after the user has not explicitly entered an authorized monitoring session.
- Boot receivers, reboot persistence, widgets, alarms, geofencing, battery-optimization exemption prompts, analytics, remote upload, or network location.
- Background compass, horizon, pressure, nearby-city lookup, route computation, or map rendering; only the existing location/GNSS data session needed by current controllers is in scope.
- Notification actions beyond opening the app and handling system dismissal.
- Reintroducing legacy card visibility controls, notification service architecture, local broadcasts, XML Preference screens, or a second activity.
- Any changes to the original project.
