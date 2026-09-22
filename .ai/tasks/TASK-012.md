# TASK-012 — Add foreground display behavior settings

## Status

READY_FOR_DESIGN

## Goal

Extend the existing Compose Settings destination with the remaining foreground display preferences that are supported by Smart Flight’s original application: keeping the screen awake, selecting portrait or sensor-based orientation, and allowing a larger offline-map zoom range.

## Context

TASK-010 added configurable measurement units and TASK-011 added About. The rewrite’s Settings screen currently exposes only the five unit selectors, although the original app also exposed global display behavior under Settings. These preferences are useful during an offline flight: a user can prevent the display from sleeping, choose whether device rotation changes the dashboard orientation, and opt into the legacy map’s larger zoom range.

This task restores those user-visible foreground settings in the rewrite’s single-activity Compose architecture. It does not introduce the original app’s background location service, notification, card-hiding workflow, or legacy settings activity.

## Original Application

The original Settings screen is defined by `app/src/main/res/xml/app_preferences_layout.xml` and is opened from `MainActivity` through `Navigation.goToSettings()`.

The relevant preferences are:

- `KEEP_SCREEN_PREFERENCE_KEY`, default `false`, titled `Keep screen always on`.
- `FORCE_PORTRAIT_MODE_PREFERENCE_KEY`, default `true`, titled `Force portrait orientation mode`.
- `FORCE_ZOOM_PREFERENCE_KEY`, default `false`, titled `Force bigger map zoom`, with a warning that tiles beyond the normal map zoom may be unavailable or grey.

`MainActivityPresenter.kt` applies the screen-awake and orientation preferences when the activity view is attached. `MainActivity.addKeepScreenOnFlag()` and `clearKeepScreenOnFlag()` modify `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON`; `setOrientation()` changes the requested activity orientation. `BaseMapView.kt` uses the zoom preference to set the map maximum from the normal level 6 to level 9. `MapCardViewPresenter.kt` also uses the preference to suppress the normal maximum-zoom tip.

The same original settings screen also contains notification/background-service and unit preferences. Notification behavior depends on `services/location/LocationService.kt`, a foreground notification, and background lifecycle behavior; it is not part of this task. Unit preferences were already rewritten by TASK-010.

The original dashboard visual reference is `/home/ai-dev/smart-flight/promo/promo.png` (1024 × 500). Its dark-purple dashboard and purple-card treatment should remain visually consistent when the Settings screen is extended; the screenshot is a continuity reference, not a requirement to reproduce the legacy XML layout.

## Current Application

The rewrite uses one `MainActivity` and Compose state. `app/src/main/java/kniezrec/com/flightinfo/ui/settings/UnitSettingsScreen.kt` currently renders a scrollable, centered Settings column with five unit rows and single-choice dialogs. `MainActivity.kt` switches to this screen through `showUnitSettings` while retaining dashboard controllers and state.

The rewrite already has the necessary platform entry points in principle: `Window`/`WindowManager.LayoutParams` for the keep-awake flag, `requestedOrientation`/`ActivityInfo` for orientation, and the osmdroid `MapView` in `ui/gnss/MapCard.kt`. `MapSessionRules` currently hard-codes `DEFAULT_ZOOM = 3.0`, `FOLLOW_ZOOM = 6.0`, and the map view hard-codes `maxZoomLevel = 6.0`.

No model or persistence exists yet for these three preferences. The current map must remain offline and must continue using the packaged `assets/osmdroid.zip` archive.

## Functional Requirements

- Add a `Display` or equivalent Settings section containing:
  - **Keep screen always on**, default off.
  - **Portrait orientation**, default on. When off, allow sensor-based orientation.
  - **Larger map zoom**, default off.
- Persist all three values across activity recreation and later launches. Invalid or missing stored values must resolve to the documented defaults.
- Toggling Keep screen always on must apply or clear `FLAG_KEEP_SCREEN_ON` for the current activity without restarting the app.
- Toggling Portrait orientation must apply portrait or sensor-based orientation using the Android activity API. The Settings screen must survive the resulting recreation and retain the selected value.
- Toggling Larger map zoom must update the active offline map’s allowed maximum zoom to 9 when enabled and 6 when disabled. It must not download tiles or use a network provider.
- The map’s normal initial/follow zoom behavior must remain unchanged; this preference changes the maximum allowed zoom range, not the default viewport zoom.
- When larger zoom is enabled, the app must not show the normal “maximum zoom reached” warning that exists for the standard range. If the user disables the preference, the standard maximum and warning behavior must be restored according to the existing map implementation.
- Settings changes must not reset permissions, unit preferences, route selections, sensor/location observations, map position, or About state beyond normal Android activity recreation required by orientation.
- Settings remains accessible only from the authorized dashboard through the existing Settings action. Permission onboarding must not gain display controls.

## Technical Requirements

- Follow the existing single-activity Compose architecture and extend the existing Settings surface; do not add a second activity, PreferenceFragment, XML preference screen, navigation framework, or feature module.
- Reuse the existing `SharedPreferences`-based persistence convention used by `UnitPreferencesStore`, with namespaced keys that do not collide with legacy keys or unit keys. Store booleans defensively and expose a small testable display-preferences model/store.
- Use Android platform APIs for window flags and requested orientation. Do not add a permissions, sensor, map, or settings dependency.
- Keep orientation application lifecycle-safe: do not repeatedly force recreation or create an orientation loop, and ensure persisted state is reread after recreation.
- Pass the zoom preference into the existing map implementation rather than duplicating map/session rules. The osmdroid `MapView` must remain configured with `setUseDataConnection(false)` and the packaged archive.
- If the map is already visible when the preference changes, update its maximum zoom in place where supported and clamp/reconcile an out-of-range current zoom safely when disabling the option.
- Keep all new visible text, summaries, warnings, and accessibility descriptions in resources. Use semantic switch roles and minimum 48 dp touch targets.
- Preserve RTL behavior, safe-drawing insets, the existing responsive 600 dp Settings content width, and the current five unit selectors.

## UI Requirements

- Keep the existing Settings top app bar, leading Navigate up control, centered readable content column, scrolling behavior, and Smart Flight purple/dark-purple theme.
- Place the three new controls in a clearly labeled section separate from Units. A switch or equivalent two-state control is appropriate; the current value must be understandable without relying on color.
- Each control must show a localized label and a concise current-state summary such as On/Off or Portrait/Sensor. The larger-zoom row must communicate that extra zoom may show unavailable/grey map areas, matching the meaning of the legacy warning.
- Controls must remain usable in portrait, landscape, RTL, and large font scales. Long summaries must wrap rather than clip or overlap the switch.
- The screen must provide accessible state and action semantics for every control, and the larger-zoom warning must be available to assistive technology.
- Do not add controls for notification, background tracking, card visibility, or unrelated legacy preferences in this task.

## Acceptance Criteria

- [ ] Settings shows a separate display-behavior section with Keep screen always on, Portrait orientation, and Larger map zoom controls, while all existing unit controls remain available.
- [ ] Fresh install defaults are Keep screen always on off, Portrait orientation on, and Larger map zoom off.
- [ ] Each setting persists across activity recreation and a later app launch; malformed/missing values fall back independently to its documented default.
- [ ] Enabling Keep screen always on sets the current activity’s keep-screen-on flag; disabling it clears the flag without affecting other settings.
- [ ] Portrait orientation on requests portrait orientation; turning it off requests sensor-based orientation. The selected value remains after the orientation-triggered recreation.
- [ ] With larger map zoom off, the active offline map uses maximum zoom 6; with it on, the maximum is 9. Changing the setting while the map is visible updates the map safely without network access.
- [ ] The map’s default and first-fix/recenter zoom values remain unchanged by the larger-zoom preference.
- [ ] The standard maximum-zoom warning is suppressed while larger zoom is enabled and remains available under the existing standard-range behavior when it is disabled.
- [ ] Settings controls have localized labels/summaries, switch semantics, non-color-only state, and at least 48 dp touch targets; the layout remains usable at large font scale, landscape, and RTL.
- [ ] Permission onboarding does not expose these settings, and changing them does not alter location permission state, units, route state, sensor/location observation, or map offline behavior.
- [ ] Focused unit/Compose tests cover default and malformed persistence, each toggle’s side effect, orientation recreation/persistence, map maximum-zoom selection, warning suppression, and accessibility/state presentation where the configured test environment supports it.
- [ ] Existing project checks continue to pass, and no new dependency, permission, background service, notification, network request, XML preference screen, or second activity is added.

## Implementation Plan

1. Inventory the existing Settings state flow, activity lifecycle, map instance ownership, and unit preference persistence conventions.
2. Define a display-preferences model/store with namespaced keys, independent defaults, and focused persistence tests.
3. Extend the Compose Settings screen with the display section, resource-backed summaries/warnings, accessible switches, and responsive layout.
4. Wire screen-awake and orientation changes through `MainActivity` using lifecycle-safe callbacks and reread persisted values after recreation.
5. Thread the larger-zoom value into the existing osmdroid map/session implementation, preserving offline configuration, default zooms, route overlays, and warning behavior.
6. Add focused unit and Compose/instrumentation coverage, then run configured checks in an Android SDK environment.

## Files / Components Likely Affected

- `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/settings/UnitSettingsScreen.kt` or a neighboring display-settings composable
- New display-preferences model/store under an appropriate existing package
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/MapCard.kt`
- `app/src/main/java/kniezrec/com/flightinfo/map/MapState.kt`
- `app/src/main/res/values/strings.xml`
- Focused tests under `app/src/test` and `app/src/androidTest`

This is guidance; unrelated feature controllers and legacy files must not be modified.

## Reusable Existing Libraries / Components

- Existing Compose Material 3 `Scaffold`, `Switch`, `Card`/surface styling, scrolling Settings layout, and theme tokens.
- Existing `UnitPreferencesStore` persistence pattern and Settings navigation state in `MainActivity`.
- Android `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON`, `ActivityInfo.SCREEN_ORIENTATION_PORTRAIT`, and `ActivityInfo.SCREEN_ORIENTATION_SENSOR`.
- Existing osmdroid `MapView`, `MapSessionRules`, `MapCard`, `MapArchiveRepository`, and packaged `assets/osmdroid.zip`.
- Legacy behavioral references: `app/src/main/res/xml/app_preferences_layout.xml`, `MainActivityPresenter.kt`, `FlightAppPreferences.kt`, `BaseMapView.kt`, `MapCardView.kt`, and `MapCardViewPresenter.kt`.

## Risks and Edge Cases

- Orientation changes recreate the activity and can otherwise reset in-memory route/map/controller state; persist only the preference and verify the existing state restoration behavior rather than introducing an unrelated state migration.
- Some devices or window modes may ignore sensor orientation requests; the app must remain stable and show the selected preference rather than claiming a platform guarantee.
- Clearing keep-screen-on must not clear flags owned by another feature; only the Smart Flight flag should be managed.
- Disabling larger zoom while the current map zoom is above 6 may require clamping to 6 before changing the maximum to avoid an invalid viewport.
- The offline archive may not contain tiles at zoom 9; the feature is an opt-in range and must present the legacy warning without attempting network fallback.
- Settings may be opened while map/sensor callbacks are active. Opening or closing Settings must not duplicate observers or stop foreground observations.
- Android back and process recreation must preserve the existing Settings/About precedence and not expose display settings on permission onboarding.

## Open Questions

None. The original defaults and zoom levels are directly observable in the legacy preference XML and map implementation; notification/background behavior is explicitly deferred because it requires a separate lifecycle/service specification.
