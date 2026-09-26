# TASK-024 — Compass, horizon, Settings and About available without location permission

## Goal
Users who have not granted location keep the features that never needed it: show the permission card at the top, then the Course and Horizon cards; Settings and About are always reachable.

## Context
- Parity finding 7 (REGRESSION, MEDIUM), verified in the rewrite: only `PermissionOnboardingScreen` is rendered when permission is not `Granted` (`MainActivity.kt:176,328-344` before TASK-016); `AboutDialog` gated on `Granted` (`:346`); Settings only reachable inside the `Granted` branch (`:180`).
- Original: `~/smart-flight/app/src/main/java/kniezrec/com/flightinfo/cards/adapter/CardViewContainer.kt:46-64,90-101` adds the permission card first, then only cards whose `needsLocationPermission()` is false (Course, Horizon) until permission is granted; the toolbar menu (Settings, About) is always available (`res/menu/app_menu.xml`).
- After TASK-016 the dashboard consists of card containers with their own ViewModels; the permission state lives in `LocationPermissionViewModel` (TASK-015).

## Dependencies
- TASK-016.

## Original app reference
- `cards/adapter/CardViewContainer.kt`, `cards/permission/PermissionCardView.kt`, `layout/permission_view.xml`, `menu/app_menu.xml`.

## Scope
- Dashboard when not granted: header (with Settings/About), permission card (existing `PermissionOnboardingScreen` content adapted as a card, all its states: requestable, rationale, settings-required), Course card, Horizon card. Location-dependent cards are not composed (their ViewModels are not created, so no location collection starts).
- On grant: the remaining cards appear (animated insertion optional), location collection and service start as today.
- About and Settings overlays work in both states.
- Course card without location: heading works; GPS bearing line absent (no fixes).

## Out of scope
- Card order (TASK-034). POST_NOTIFICATIONS (TASK-025).

## Requirements
Required:
- No location API is touched while permission is not granted (no repository collection, no service start).
- Behavior change vs current rewrite: list in PR; update TASK-004 characterization scenario 1.

## Acceptance criteria
- [ ] Robolectric test: permission denied → permission card, Course and Horizon cards visible; Settings and About open — verified by: CI unit test
- [ ] Robolectric test: denied → no `LocationManager` listener, no service start; grant → both happen — verified by: CI unit test
- [ ] Onboarding states still rendered correctly (`PermissionOnboardingScreenTest`) — verified by: CI unit test
- [ ] Deny permission on a fresh install, compass works, Settings opens — verified by: HUMAN on device

## Tests to add or update
- Characterization scenario 1; new `DashboardPermissionTest`.

## Risks and edge cases
- Permission revoked while app is in background: on resume the location cards disappear and collection stops (as today's refresh does).
