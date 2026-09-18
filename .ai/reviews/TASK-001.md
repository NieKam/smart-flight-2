# TASK-001 Review

## Iteration

4

## Status

CHANGES_REQUESTED

## Summary

Iteration 3 layout and testability changes are correctly implemented. The authorization scope remains blocked: the app declares and requests coarse location as well as fine location although both the task and design require fine location only. The small Activity and Compose architecture is appropriate for this app; no ViewModel, repository, DI, or Flow layer is warranted.

## Previous Findings Verification

### Fine-location-only scope and Android 12+ request compatibility

Not fixed. ACCESS_COARSE_LOCATION remains declared and is requested alongside ACCESS_FINE_LOCATION with RequestMultiplePermissions. This is Android 12+ compatible but does not meet the explicit fine-only requirements. The task and design owner must resolve this contradiction before implementation can be approved.

### Durable request history

Fixed. LocationPermissionStateController owns state derivation over a durable request-history abstraction and current platform checks. The recreated-controller test verifies that recorded history is observed through the same durable-store contract, and MainActivity supplies the SharedPreferences implementation before launch.

### Settings-return lifecycle coverage

Fixed. The controller test changes the platform grant between reads and verifies Granted. MainActivity.onResume re-reads that controller, so the lifecycle boundary is small and independently testable.

### Wide-window card cap

Fixed in source. widthIn(max = 600.dp) now precedes fillMaxWidth(), allowing the centered parent to constrain the card. The new Compose test measures the 600 dp cap under a 700 dp parent. It was inspected but could not run without an Android SDK.

### Accessible action contrast and focus

Remains fixed. The action uses a higher-contrast cyan, has a contrast assertion, and renders a 2 dp focus outline.

## Requirements

Verified by source inspection:

- The sample greeting is replaced by a localized Smart Flight shell with one permission or granted state card and no unsupported dashboard, map, route, satellite, telemetry, location-update, or background-service behavior.
- The current fine-location grant, rather than the Activity Result callback value, drives state. The state refreshes after a result and on every resume.
- Requestable denial retains Grant permission. A recorded request plus no rationale maps to Open settings, which targets this app application-details page.
- Fine location is declared and WRITE_EXTERNAL_STORAGE is absent.
- The fine-only declaration/request criterion is not met; see Finding 1.

## Design

The implementation follows the specified dark-purple shell, 56 dp centered header, single purple card, exact localized copy, 10 dp corners, 4 dp elevation, 12 dp exterior spacing, 160 dp minimum height, 48 dp action target, safe drawing insets, scrolling, and 180 ms crossfade. The accessible light-lavender body alternative is allowed by the design. The corrected card modifier order implements the 600 dp expanded-window cap. System dialog and settings handoff could not be visually verified without a device or emulator.

## Architecture

The rewrite is an initial Activity-rooted Compose application with no existing ViewModels, repositories, use cases, dependency injection, or Flow/StateFlow screen-state convention. For this one platform permission flow, MainActivity appropriately owns the Activity Result API, settings intent, lifecycle callback, and Android-backed preferences/permission APIs. PermissionOnboardingScreen is event-driven and renders the supplied state; permission policy and durable-history boundary are outside the composable and independently testable through LocationPermissionStateController. This is proportionate modern Android architecture for the scope, so no architectural change is requested.

## Findings

### Finding 1

- Severity: BLOCKING
- Location: app/src/main/AndroidManifest.xml; app/src/main/java/kniezrec/com/flightinfo/LocationPermissions.kt (locationPermissionRequest); MainActivity.requestLocationPermission
- Problem: The app declares and requests ACCESS_COARSE_LOCATION together with fine location.
- Why it matters: TASK-001 says Request fine location only, and the design says Request only Android fine location permission. Coarse location is an additional authorization, even if Android 12+ needs it as a companion request for a functioning precise-location prompt. That platform constraint conflicts with, rather than silently overrides, the written requirement.
- Required change: Obtain an explicit task/design-owner decision. If fine-only scope remains authoritative, remove coarse declaration/request and update its test. If Android 12+ support authorizes a companion coarse request, update the task/design to state that narrow exception while retaining fine location as the only feature gate. Do not change the implementation further until that product requirement is resolved.

## Tests

Inspected:

- LocationPermissionStateTest meaningfully covers granted, first-launch, ordinary-denial, and settings-required reducer branches.
- LocationPermissionStateControllerTest meaningfully covers recreated-controller history and a later changed platform grant.
- LocationPermissionsTest meaningfully checks contrast; its fine-plus-coarse assertion reflects Finding 1.
- PermissionOnboardingScreenTest meaningfully checks the 600 dp cap but requires Android instrumentation.

./gradlew :app:testDebugUnitTest was run and failed during configuration because no Android SDK is configured through ANDROID_HOME or local.properties. No Android build, unit-test, instrumentation-test, emulator, or device result is claimed. git diff --check origin/main...HEAD passed.

## Verification

Reviewed the task, design, Iteration 3 review, current Kotlin and Compose source, manifest, resources, unit and instrumentation tests, project conventions, and the complete origin/main...HEAD diff. The latest implementation commit ebf20f3 was specifically inspected. The worktree was clean before this review artifact update.

## Recommendation

CHANGES_REQUESTED
