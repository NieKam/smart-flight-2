# TASK-025 — Background "waiting for GPS" notification on Android 13+ (POST_NOTIFICATIONS) and plane notification icon

## Goal
Make the background notification actually visible on API 33+ by requesting `POST_NOTIFICATIONS` at the right moment, never keep GPS running in the background without a visible notification, and use a proper monochrome plane small icon.

## Context
- Parity finding 8 (REGRESSION, MEDIUM), verified:
  - `POST_NOTIFICATIONS` is declared (`app/src/main/AndroidManifest.xml:10`) but never requested; the only use is the check in `LocationForegroundService.canPostNotifications()` (`monitoring/LocationForegroundService.kt:183-185`).
  - `reconcile` (`:115-121`, logic preserved by TASK-008) keeps the foreground GPS service running in the background whenever the preference is on, even when notifications cannot be shown.
  - minSdk is 31, so API 33+ is the common case.
  - Original: `~/smart-flight/app/src/main/java/kniezrec/com/flightinfo/services/location/LocationService.kt:187-218,231-259` shows a dismissible notification (title `app_background_notification_title`, content `app_background_notification_content`, small icon `small_plane_icon`) while GPS keeps searching in the background; dismissing stops GPS (`common/NotificationBroadcastReceiver.kt:15-28`). It only shows when no location was obtained yet, permission granted and the preference on.
- Parity finding 24 (third bullet, VISUAL_MISMATCH, LOW): notification small icon is the adaptive `mipmap/ic_launcher` (`LocationForegroundService.kt:235`), which renders as a blob; original used `small_plane_icon` (`res/drawable-v21/small_plane_icon.xml`).
- Parity inventory: background GPS until first fix, notification, dismiss stops GPS — present in the rewrite; keep (stop on first usable fix in background is an accepted MODERNIZATION: original stopped when the app returned).

## Dependencies
- TASK-016, TASK-024 (permission flow UI).

## Original app reference
- `services/location/LocationService.kt`, `common/NotificationBroadcastReceiver.kt`, `res/drawable-v21/small_plane_icon.xml`, strings `app_background_notification_title/content`, `channel_name`.

## Scope
- Request `POST_NOTIFICATIONS` (API 33+) when: location permission has just been granted and the "show notification" setting is on; and when the user turns the setting on in Settings while the permission is missing. Use the Activity Result API in `MainActivity`. Respect "don't ask again": if denied permanently, show an inline hint in Settings next to the switch with a button to the app's notification settings (`Settings.ACTION_APP_NOTIFICATION_SETTINGS`).
- Service rule change: when the Activity is not visible and notifications cannot be posted (permission denied or channel blocked), stop monitoring (same as preference off). Keep all other rules.
- Settings switch shows the effective state (on but not permitted → subtitle "Notifications are blocked").
- Small icon: vector drawable of the original plane silhouette (monochrome, white on transparent) as `ic_stat_plane`; use it in the notification.

## Out of scope
- Changing the notification texts or channel importance (keep the rewrite's LOW importance; the original used DEFAULT — acceptable modernization, mention in PR).

## Requirements
Required:
- On API 33+, after granting both permissions, backgrounding the app before the first fix shows the "waiting" notification; dismissing it stops GPS.
- With notifications denied, backgrounding the app stops GPS.
- Behavior changes vs current rewrite listed in PR.

## Acceptance criteria
- [ ] Service tests: invisible + notifications denied → stop; invisible + allowed + no fix → waiting notification with `ic_stat_plane` — verified by: CI unit test (Robolectric, `@Config(sdk = [33+])`)
- [ ] Activity test: POST_NOTIFICATIONS requested after location grant when setting on; not requested when setting off or API < 33 — verified by: CI unit test (Robolectric permission shadows)
- [ ] Notification visible and dismissible on an Android 13+ device; icon renders as a plane — verified by: HUMAN on device

## Tests to add or update
- `LocationForegroundServiceTest`, `LocationForegroundServiceEligibilityTest`, new `NotificationPermissionFlowTest`, `SettingsViewModelTest`.

## Risks and edge cases
- Requesting two permissions back to back can feel pushy; request notifications right after the location result, with no extra dialog.
- Foreground service notifications on API 34+ may be dismissible by the user even if ongoing; the delete intent already stops monitoring.
