# TASK-001 Review

## Iteration

5

## Status

CHANGES_REQUESTED

## Summary

Iteration 4's package organization is correctly applied and the earlier lifecycle, responsive-layout, and accessibility fixes remain present. The sole outstanding blocker is unchanged: the manifest and runtime request include coarse location despite TASK-001 and its approved design explicitly requiring fine location only. This is a requirements conflict caused by Android 12+ precise-location prompting behavior and needs an explicit owner decision before the implementation can be approved.

## Previous Findings Verification

### Fine-location-only scope and Android 12+ request compatibility

Not fixed. `ACCESS_COARSE_LOCATION` remains in the manifest and in `locationPermissionRequest`, which `MainActivity` passes to `RequestMultiplePermissions`. That preserves the Android 12+ precise-location prompt behavior, but it does not satisfy the task's “Request fine location only” technical requirement or the design's equivalent instruction.

### Durable request history

Fixed. `LocationPermissionStateController` derives its state through a platform abstraction and durable history abstraction. `MainActivity` supplies the durable `SharedPreferences` implementation, and the recreated-controller test covers the history boundary.

### Settings-return lifecycle coverage

Fixed. `MainActivity.onResume` refreshes the current state. The controller test changes the platform grant between reads and verifies that the next read is `Granted`.

### Wide-window card cap

Fixed. `widthIn(max = 600.dp)` constrains the card before `fillMaxWidth()`, and the Compose test measures a 600 dp card inside a 700 dp parent.

### Accessible action contrast and focus

Fixed. The action color has a unit contrast assertion of at least 4.5:1 against the card, and the action renders a 2 dp cyan focus outline. The body/title color is `#D9D9ED`, whose calculated contrast against `#5B5999` is approximately 4.53:1.

## Requirements

Verified by source inspection:

- A fresh launch derives the current fine-location grant and renders a localized Smart Flight onboarding card rather than the sample greeting.
- The requestable state exposes `Grant permission`; ordinary denial remains requestable, while a recorded request with no platform rationale becomes `Open settings`.
- The platform result and every activity resume re-read platform permission state; a fine grant produces the minimal granted state without dashboard, map, route, satellite, telemetry, location-update, or service functionality.
- Settings handoff targets this app's application-details page and failure retains the card with an accessible snackbar.
- The manifest declares fine location and does not declare `WRITE_EXTERNAL_STORAGE`.
- The fine-only request/declaration criterion is not met; see Finding 1.

## Design

The source follows the approved shell and card design: fixed legacy-inspired purple/cyan colors, 56 dp centered header, one card, exact localized state copy, 12 dp outer spacing, 10 dp radius, 4 dp elevation, 160 dp minimum height, 48 dp action target, safe-drawing insets, scrolling content, 180 ms transition, and the corrected 600 dp wide-window cap. The system permission dialog and settings screen could not be visually verified without an Android SDK/device.

## Architecture

The project has no established ViewModel, repository/use-case, DI, or Flow/StateFlow screen-state pattern. The current organization is appropriate for that baseline and for this single platform-permission flow:

- `MainActivity` is a narrow framework boundary for the Activity Result API, lifecycle refresh, system-settings intent, Android permission APIs, and `SharedPreferences` implementation.
- `permission/` owns platform-independent presentation-state derivation and the testable request-history/controller boundary.
- `ui/permission/` renders supplied state and emits user events without directly using Android permission APIs.

This separation avoids unnecessary coupling and keeps the state independently testable. No ViewModel, repository, use case, DI, or Flow refactor is justified for the current scope.

## Findings

### Finding 1

- Severity: BLOCKING
- Location: `app/src/main/AndroidManifest.xml`; `app/src/main/java/kniezrec/com/flightinfo/permission/LocationPermissions.kt` (`locationPermissionRequest`); `MainActivity.requestLocationPermission`
- Problem: The app declares and requests `ACCESS_COARSE_LOCATION` alongside `ACCESS_FINE_LOCATION`.
- Why it matters: TASK-001 requires that the app request fine location only, and the design repeats that scope. Android 12+ needs a simultaneous coarse-and-fine request to present its precise-location choice, but that platform constraint conflicts with rather than supersedes the written fine-only requirement.
- Required change: Obtain and record an explicit task/design-owner decision. If fine-only scope remains authoritative, remove coarse declaration/request and adjust its test. If Android 12+ compatibility authorizes a companion coarse request, the owner must update the task and design to document that narrow exception while retaining fine location as the only feature gate. Do not silently choose either outcome in implementation.

## Tests

Inspected:

- `LocationPermissionStateTest` meaningfully covers granted, initial, ordinary-denial, and settings-required derivation branches.
- `LocationPermissionStateControllerTest` meaningfully covers durable-history recreation and a changed platform grant after settings return.
- `LocationPermissionsTest` checks platform request contents and action-color contrast; its coarse-location assertion documents Finding 1.
- `PermissionOnboardingScreenTest` meaningfully checks the 600 dp expanded-window cap, but requires Android instrumentation.

`./gradlew :app:testDebugUnitTest` was attempted and failed during task configuration because no Android SDK location is configured through `ANDROID_HOME` or `local.properties`. No Android build, unit test, instrumentation test, emulator, or device result is claimed. `git diff --check origin/main...HEAD` and `git diff --check` passed.

## Verification

Reviewed the task, design, Iteration 4 review, current implementation, manifest, resources, focused tests, package layout, project conventions, complete `origin/main...HEAD` diff, and the latest organization commit `7a16061`. The working tree was clean before this review artifact update. No application source, tests, or Gradle configuration were modified.

## Recommendation

CHANGES_REQUESTED
