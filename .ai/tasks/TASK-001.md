# TASK-001 — Add location-permission onboarding

## Status

READY_FOR_DESIGN

## Goal

Replace the placeholder Compose greeting with a location-permission onboarding state that lets a new user grant the precise/fine-location access required before Smart Flight can provide its flight dashboard.

## Context

The original app gates all location-dependent dashboard cards behind fine-location permission. Smart Flight must work offline during a flight and rely on device GNSS/location sensors to establish the best available position as quickly as possible; approximate/coarse-only location is not sufficient for that purpose. This is the smallest complete, user-visible first iteration: it establishes the entry point and prerequisite for satellite, flight-data, location, route, and map features without prematurely implementing any of them. The rewrite currently contains only the Android Studio Compose sample and declares no location permission.

## Original Application

`app/src/main/java/kniezrec/com/flightinfo/cards/adapter/CardViewContainer.kt` checks fine-location permission when it creates the dashboard. If permission is missing, it places `PermissionCardView` first and omits every location-dependent card. On grant it rebuilds the card list; on permanent denial it changes the action to open the app's system settings.

`cards/permission/PermissionCardView.kt` and `PermissionManager.kt` implement the request/settings behavior. The legacy manifest declares only `ACCESS_FINE_LOCATION` for location (its `WRITE_EXTERNAL_STORAGE` permission serves the old offline-map implementation and is not part of this task). `res/layout/permission_view.xml` shows a centered explanation and a text action. The surrounding legacy dashboard uses a dark purple background (`#484685`), purple cards (`#5b5999`), muted lavender text (`#a1a0c4`), bright cyan actions (`#25e5fe`), 12 dp card margins, 10 dp corners, and 18 sp body text (`res/values/colors.xml`, `dimens.xml`, and `styles.xml`).

## Current Application

`app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt` renders only `Hello Android!` in a Material 3 `Scaffold`. `app/src/main/AndroidManifest.xml` does not declare location permission. The project already has Compose Material 3 and `androidx.activity:activity-compose`; no location, map, or dashboard implementation exists.

## Functional Requirements

- Declare `android.permission.ACCESS_FINE_LOCATION` in the rewrite manifest.
- On launch, show a location-permission onboarding screen when fine location has not been granted.
- Explain that Smart Flight needs location permission to enable its flight-information cards.
- Provide a visible, accessible **Grant permission** action that invokes the Android runtime fine-location permission prompt.
- If the user grants permission, immediately remove the onboarding state and show the normal granted entry state; this iteration must not add satellite, map, route, or telemetry cards.
- If permission is denied but can be requested again, retain the onboarding state and allow another request.
- If Android reports that permission is permanently denied, replace the action with **Open settings** and open this app's details page in system settings.
- Re-evaluate permission when the activity resumes, so returning from system settings updates the UI.

## Technical Requirements

- Use the existing Compose and Activity Compose dependencies, including the Activity Result permission API; do not add a location SDK or map library for this task.
- Keep permission state lifecycle-aware and derive it from the platform permission result/current grant rather than assuming the button outcome.
- Fine location is required and is the only authorization that may satisfy this task's location prerequisite. Coarse/approximate-only permission must keep the user in onboarding and must never produce the granted entry state.
- On Android 12+ (API 31+), declare and request ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION together when required by the Android permission model to present its precise/approximate location choice. This companion coarse request is solely a platform prompt requirement: it does not weaken the accuracy requirement, and only a granted ACCESS_FINE_LOCATION result is success. Do not add legacy external-storage permission.
- Preserve edge-to-edge handling and existing package/application identity.
- Do not start location updates, read GNSS status, or implement a background service in this iteration.

## UI Requirements

- Replace the sample greeting with a simple Smart Flight app shell and permission card/state inspired by the legacy permission view.
- Use the legacy visual language as the reference: dark-purple page background, purple rounded surface/card, centered muted-lavender explanatory copy, and cyan text action.
- Use comfortable card spacing comparable to the legacy 12 dp outer margin and 10 dp rounding; adapt naturally to portrait and landscape Compose layouts.
- The screen must remain useful without location permission and must not present empty or nonfunctional flight cards.

## Acceptance Criteria

- [ ] A fresh install displays a Smart Flight location-permission explanation and a **Grant permission** action instead of `Hello Android!`.
- [ ] Tapping **Grant permission** opens Android's fine-location runtime permission prompt.
- [ ] Granting fine location changes the screen to the granted entry state without restarting the app.
- [ ] On Android 12+, the request presents Android's precise/approximate choice by requesting fine and coarse together when required; selecting approximate-only location keeps the app in the actionable onboarding state.
- [ ] Denying permission keeps an actionable onboarding screen.
- [ ] A permanently denied permission presents **Open settings**, which opens this app's system-settings details screen.
- [ ] Returning from system settings refreshes the state: granting permission there removes the onboarding state.
- [ ] The manifest declares `ACCESS_FINE_LOCATION` and does not introduce `WRITE_EXTERNAL_STORAGE`.
- [ ] The project continues to build and pass its configured checks.

## Implementation Plan

1. Add the fine-location permission declaration and the Android 12+ companion coarse declaration needed to present the system's precise/approximate choice; keep fine location as the sole success gate.
2. Replace the sample `Greeting` content with a Compose screen that models the permission-entry state and a minimal granted state.
3. Connect the grant action to the Activity Result runtime-permission launcher and handle grant, ordinary denial, and permanent denial.
4. Refresh permission state on resume and wire the settings action to the app-details settings intent.
5. Apply the legacy purple/cyan visual references using Compose theme or screen-local tokens, then add focused UI/behavior tests where practical.

## Files / Components Likely Affected

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- New Compose screen/state files under `app/src/main/java/kniezrec/com/flightinfo/`
- `app/src/main/java/kniezrec/com/flightinfo/ui/theme/Color.kt` and/or `Theme.kt`
- Relevant unit or Compose UI tests

## Reusable Existing Libraries / Components

- Rewrite: Compose Material 3 and `androidx.activity:activity-compose` already provide the UI and Activity Result integration needed.
- Legacy behavior reference: `PermissionCardView`, `PermissionManager`, and `CardViewContainer`.
- Legacy visual reference: `permission_view.xml` and the colors, dimensions, and styles resources.
- Do not use the legacy `osmdroid-android`, bundled `osmdroid.zip`, `jsi`, or `trove4j` libraries in this task; they support later map/city features.

## Risks and Edge Cases

- Android permission behavior differs by API level and user choice; do not infer permanent denial solely from one denial callback.
- On Android 12+, fine and coarse must be requested together to present the system's precise/approximate choice. Treat an approximate-only result as insufficient and continue onboarding until fine location is granted.
- The app may resume after the user changes the permission in settings, so the displayed state must be refreshed.
- Device/emulator configurations without usable GPS are outside this task; permission onboarding must still work independently of provider availability.
- Avoid making a promise that location is already being collected; this iteration only establishes authorization.

## Open Questions

None. The original implementation establishes fine location as the prerequisite, and the granted state is intentionally minimal until the first telemetry card is specified.
