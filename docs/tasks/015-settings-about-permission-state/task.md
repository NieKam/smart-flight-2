# TASK-015 — Settings, About and permission state out of the Activity

## Goal
Give the Settings screen a `SettingsViewModel` backed by the settings repositories, make the About version provider injectable, and move location-permission state into a small state holder fed by the Activity. After this task, `MainActivity` holds no settings, about or permission logic beyond Android-bound calls.

## Context
- Architecture review F2 (VIOLATION, HIGH): settings write, side effect and service start in the Activity (`MainActivity.kt:183-202`); permission-driven start/stop orchestration (`:410-424,579-591`) with foreground gating repeated at `:199,324,382,412,596,603`.
- Architecture review F1: `showUnitSettings`, `showAbout` (`MainActivity.kt:106-107`), `permissionState`, `announcementVersion` in the Activity.
- Architecture review F16: `AndroidAppVersionProvider` has a nullable `Context` plus a lambda default (`about/AboutPlatform.kt:26-33`).
- Architecture review DI inventory: `AndroidExternalIntentLauncher(this)` needs an Activity context (A); `LocationPermissionStateController` rationale check (`shouldShowRequestPermissionRationale`) is Activity-bound, the request history is singleton (`PermissionRequestHistoryRepository` from TASK-006).
- Architecture review K1/K2 (KEEP): `FineLocationPermissionPlatform`, `LocationPermissionRequestHistory` interfaces and pure `locationPermissionState` (`permission/LocationPermissionState.kt:14`).
- Architecture review K4 (KEEP): `UnitSettingsScreen`, `AboutDialog`, `PermissionOnboardingScreen` are stateless; keep them so.
- Parity MODERNIZATION (accepted, keep): settings are in-app (switches and radio dialogs) with the full original set; About has equivalent content; keep screen on / force portrait behavior.

## Dependencies
- TASK-006.

## Target conventions (from CLAUDE.md)
ViewModels expose `StateFlow`; Android-bound concerns (permission launcher, rationale, `startActivity`) stay in the Activity; repositories for persisted data.

## Scope
- `SettingsViewModel`: state = units + display + background notification settings; setters call repository `suspend` setters. When "show background notification" is enabled while visible and permission granted, the service start still happens (Activity observes the setting or the VM exposes an effect; keep today's behavior).
- `AboutViewModel` or a plain injected `AppVersionProvider` (`@Singleton`, application context) — choose the simpler; `formatAppVersion` stays pure.
- `LocationPermissionViewModel` (or a plain state holder in the dashboard VM layer): `state: StateFlow<LocationPermissionState>`, `announceChange` flag; `refresh(snapshot)` where the Activity passes a `PermissionSnapshot(fine, coarse, shouldShowRationale)`; `onRequestLaunched()` records history. Uses `LocationPermissionStateController` logic or its pure function (delete the controller if the ViewModel subsumes it; port `LocationPermissionStateControllerTest`).
- `MainActivity`: keep the permission launcher, `openAppSettings`, `openLocationSettings`, external intent launching; remove the settings write lambdas and permission state fields.

## Out of scope
- Showing Settings/About without permission (TASK-024). POST_NOTIFICATIONS request (TASK-025). Overlay saveability (TASK-016).

## Requirements
Required:
- Same behavior: settings changes apply immediately; permission onboarding states (Requestable, rationale, settings-required) identical; About shows the same version text and actions.

## Acceptance criteria
- [ ] `SettingsViewModelTest` (each setter persists and emits) — verified by: CI unit test
- [ ] Permission state tests ported from `LocationPermissionStateControllerTest` — verified by: CI unit test
- [ ] `UnitSettingsScreenTest`, `AboutDialogTest`, `PermissionOnboardingScreenTest` pass — verified by: CI unit test
- [ ] TASK-004 scenarios 1, 2, 6 pass — verified by: CI unit test

## Tests to add or update
- New: `SettingsViewModelTest`, `LocationPermissionViewModelTest`.
- Update/delete: `LocationPermissionStateControllerTest`, `AboutPlatformTest` (if the provider changes).

## Risks and edge cases
- `shouldShowRequestPermissionRationale` must be read from the Activity at refresh time; do not cache it in a singleton.
