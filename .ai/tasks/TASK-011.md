# TASK-011 — Add the About and app-information dialog

## Status

READY_FOR_DESIGN

## Goal

Add a discoverable About action for authorized Smart Flight users and a Compose-native app-information surface that provides the application version, feedback contact, store-rating action, safety disclaimer, and relevant open-source attribution.

## Context

The rewritten application now has the core foreground dashboard and a Settings destination for display units, but it has no About entry point. The original Smart Flight application exposes About from the main menu and presents app identity, version information, feedback, rating, and usage disclaimers in a scrollable dialog. This task restores that user-visible capability without bringing the legacy activity, XML dialog, MVP presenter, or obsolete dependency list into the rewrite.

The About surface is informational. It must not change permissions, sensor observation, route/map state, unit preferences, or dashboard lifecycle.

## Original Application

The legacy main activity inflates `res/menu/app_menu.xml`, which contains `Settings` and `About`. Selecting About calls `MainActivityPresenter.onAboutClicked()` and then `DialogUtils.showAboutDialog()`.

`app/src/main/java/kniezrec/com/flightinfo/common/DialogUtils.kt` creates a titled, scrollable Material Dialog with the application icon. It obtains the installed package version and displays it as `versionName (versionCode)`. The custom content is defined by `app/src/main/res/layout/about_app_dialog_layout.xml` and contains, in order:

1. A `Version` label and installed version value.
2. A `Rate in Google Play` link.
3. A `Send Feedback` mail link.
4. A long disclaimer and open-source attribution paragraph.

The source strings are in `app/src/main/res/values/strings.xml`: `about`, `version`, `rate`, `contact`, and `about_description`. The legacy link handling uses `MovementCheck` and opens a mail client or Google Play through Android link resolution. The legacy content also mentions old libraries such as MPAndroidChart, Material Dialogs, and LeakCanary; those names are historical evidence only and must not be presented as dependencies of the rewrite unless they are actually present and relevant.

The legacy dashboard visual reference is `/home/ai-dev/smart-flight/promo/promo.png` (1024 × 500). It shows the dark-purple dashboard/card language used by the app. The screenshot is a visual reference for continuity, not a requirement to reproduce the old XML dialog pixel-for-pixel.

## Current Application

The rewrite uses one Compose activity. `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt` currently renders the authorized dashboard through `GnssStatusScreen` and opens `UnitSettingsScreen` through the existing `showUnitSettings` state. Permission onboarding is rendered separately through `PermissionOnboardingScreen`.

`app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt` already provides a dashboard header and a labeled Settings action. The header is the appropriate place for a second clearly labeled About action, or an equivalent discoverable app-bar/menu action chosen by Design. About does not need to be available from the permission-onboarding screen.

The rewrite already has Compose Material 3, the app name resource, launcher icon resources, and the existing dark-purple/cyan theme. It has no About composable, About state, version presentation, external-link action, or About-related strings.

## Functional Requirements

- Provide a clearly discoverable **About** action from the authorized dashboard.
- Opening About must show an informational surface without leaving the single activity or resetting any dashboard state.
- Display the installed application version using the package metadata available at runtime. The visible version must be the current installed version, not a hard-coded legacy value.
- Display the existing app name, a `Version` label, a safety disclaimer equivalent in meaning to the legacy disclaimer, and an open-source attribution section appropriate to the rewrite.
- Provide a **Send feedback** action that attempts to open a mail-capable Android handler using the configured feedback address.
- Provide a **Rate in Google Play** action that attempts to open the application’s store listing. Prefer the market URI and provide a browser/web fallback when the market handler is unavailable.
- If no mail handler or store/browser handler is available, keep the About surface open and show a localized, accessible failure message. The app must not crash.
- Provide a clear **OK**, **Close**, or equivalent dismiss action, and support Android/system back dismissal.
- Keep the About content scrollable when it exceeds the available height.
- About must be available only as an authorized-dashboard action; permission onboarding must continue to show only its permission-related actions.

## Technical Requirements

- Follow the existing single-activity Compose architecture. Do not add a second activity, navigation framework, legacy `PreferenceFragment`, XML dialog layout, or new feature module for this task.
- Use the existing Compose Material 3 dialog or a full-screen/contained informational destination selected by Design. The chosen surface must preserve the current back behavior and not interfere with the existing Settings back handling.
- Read version metadata from the Android package manager at runtime through a small testable boundary or equivalent platform adapter. Handle Android API differences in `PackageInfo` version-code access and absent/malformed metadata defensively.
- Use Android intents for `mailto:` and the store URL. Resolve/check handlers before launching where practical; catch launch failures and report them in the dialog rather than crashing.
- Keep all user-visible text, link labels, errors, accessibility descriptions, and disclaimer text in Android resources. Preserve RTL behavior and use semantic link/button roles rather than relying on color alone.
- Use the rewrite’s existing theme, launcher icon, typography, and color tokens. Do not add a third-party dialog, hyperlink, browser, or analytics dependency.
- Do not expose or implement the legacy global settings in this task: notification preferences, keep-screen-awake, forced portrait mode, bigger map zoom, or card visibility controls remain out of scope.
- Do not change permission declarations, sensor/location observers, foreground lifecycle, map archive handling, route persistence, unit preferences, or dashboard card calculations.

## UI Requirements

- The authorized dashboard must expose a visible and accessible **About** label/action alongside the existing Settings entry or through a clearly labeled app-bar/menu affordance. It must meet the existing 48 dp touch-target expectations.
- Present the app name and launcher icon or another clear Smart Flight identity marker at the top of the About surface.
- Present content in this order: app identity, version, feedback action, rating action, then disclaimer/open-source information, unless a responsive layout requires a harmless equivalent grouping.
- The content surface must use the established Smart Flight dark-purple/purple/cyan visual language while remaining consistent with the current Material 3 theme. Do not reproduce the legacy XML layout literally.
- Feedback and rating actions must be visually identifiable as actions and accessible without color-only cues. Their destination or intent purpose must be available in their content description/state description.
- Long disclaimer and attribution text must wrap, scroll, and remain readable in portrait, landscape, large font scales, and RTL layouts. No text may be clipped or hidden behind system bars.
- The dismiss action must be reachable at normal touch size, and system back must dismiss About before exiting the activity.

## Acceptance Criteria

- [ ] An authorized user can find and activate an About action from the dashboard.
- [ ] About opens without restarting observation or losing the current dashboard’s route, map, nearby-city, sensor, or unit state.
- [ ] The About surface displays Smart Flight identity, a `Version` label, and the installed package version obtained at runtime.
- [ ] The surface contains feedback, rating, safety-disclaimer, and rewrite-appropriate open-source-attribution content with resource-backed visible text.
- [ ] Activating Send feedback launches a mail-capable handler with the configured feedback address when one exists; when none exists, an accessible localized failure message is shown and the app remains stable.
- [ ] Activating Rate in Google Play launches the store listing when a market handler exists and uses a browser/web fallback when appropriate; unavailable handlers show an accessible failure message without crashing.
- [ ] About has an explicit dismiss action and Android/system back dismisses it; system back does not exit the activity while About is open.
- [ ] About content remains scrollable and readable at large font scale, in landscape, and in RTL; actions have accessible labels and non-color-only affordances.
- [ ] About is not shown as an action on permission onboarding, and opening/closing it does not alter permission state.
- [ ] Focused tests cover version formatting/metadata fallback, intent construction/handler failure, About visibility and dismissal, and key content/accessibility where the configured Compose test environment supports them.
- [ ] Existing unit settings, dashboard observations, route/map behavior, permission behavior, and configured project checks remain unchanged and passing.
- [ ] No new dependency, permission, background service, notification, network request, XML layout, second activity, or legacy preference is added.

## Implementation Plan

1. Inventory the rewrite’s package metadata, theme, launcher icon, resource conventions, and current authorized dashboard header/Settings action.
2. Define resource-backed About content, including a concise rewrite-accurate disclaimer and attribution list; omit legacy libraries that are not dependencies of the rewrite.
3. Add a small runtime metadata/link-launch boundary with deterministic handling for missing version values and unavailable external handlers.
4. Add the Compose About surface with identity, version, actions, scrollable content, localized error feedback, and accessible dismissal/back behavior.
5. Add the authorized dashboard About entry without composing it during permission onboarding, and verify that opening/closing About leaves existing state and observers intact.
6. Add focused unit and Compose/instrumentation coverage, then run the configured checks in an Android SDK environment.

## Files / Components Likely Affected

- `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt`
- New About composable/state and, if useful, a small platform adapter under `app/src/main/java/kniezrec/com/flightinfo/ui/` or an appropriate existing package
- `app/src/main/res/values/strings.xml` and localized resource files if present
- Focused unit tests and Compose/instrumentation tests under the existing test packages

This list is guidance. Do not modify unrelated feature controllers or resources unless required for the About flow.

## Reusable Existing Libraries / Components

- Existing Compose Material 3 `Scaffold`, `Card`, `AlertDialog`/surface primitives, theme, safe-drawing handling, and Snackbar host.
- Existing `R.string.app_name`, launcher icon resources, and package identity/version metadata.
- Android platform `PackageManager`, `Intent`, `Uri`, and `resolveActivity`; no external link or dialog library is needed.
- Legacy behavioral references: `MainActivity.kt`, `MainActivityPresenter.kt`, `DialogUtils.showAboutDialog()`, `about_app_dialog_layout.xml`, `MovementCheck.kt`, and the legacy About strings.
- Legacy visual reference: `/home/ai-dev/smart-flight/promo/promo.png`; use it only to maintain dashboard visual continuity.

## Risks and Edge Cases

- `versionName` may be null or unavailable in a test/fake package context; show a localized unknown-version fallback rather than blank text or an exception.
- Android package metadata APIs differ by API level; version-code access must not trigger a runtime failure on supported devices.
- Devices may have no email app, no Play Store, or no browser. Intent failures are expected states and must be recoverable while keeping About open.
- A mailto or market intent must not silently send mail, install anything, or change app settings; the user remains in control of the external handoff.
- The legacy disclaimer contains typos and outdated dependency claims. Rewrite the wording for clarity and list only dependencies actually used by the current application; do not invent certification or legal claims beyond the documented safety warning.
- A modal surface can be too short for the disclaimer at large font scales; scrolling and safe insets must be verified.
- Opening About while a callback is pending must not duplicate or stop GNSS, sensor, location, nearby-city, map, or route observation.
- Do not conflate About’s app-details identity with the permission onboarding action that opens Android application settings.

## Open Questions

None. The legacy app establishes the required About content categories and the rewrite has a single authorized dashboard entry point. Design may choose a modal dialog versus a contained informational destination, provided the acceptance criteria and existing single-activity behavior are preserved.
