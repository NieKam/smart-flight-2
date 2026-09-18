# TASK-001 Review

## Iteration

2

## Status

CHANGES_REQUESTED

## Summary

The implementation fixes the durable request-history and action-accessibility problems from Iteration 1, and changes the Android 12+ request contract so a location prompt can be shown. It still violates the unchanged fine-location-only scope by declaring and requesting coarse location. The wide-window card cap is also ineffective, and the new lifecycle-named tests do not cover the persistence or resume integration they claim to cover.

## Previous Findings Verification

### 1. Android 12+ request compatibility

Partially fixed, not resolved. The implementation uses `RequestMultiplePermissions` for fine and coarse together, which enables the Android 12+ location prompt. However, TASK-001 says to request fine location only, and the design repeats that decision. No task or design update authorizes `ACCESS_COARSE_LOCATION`.

### 2. Durable request history

Fixed in implementation. The request is recorded before launch in `SharedPreferences`, restored in `onCreate`, and current platform grant/rationale still derive the displayed state. The related tests are insufficient; see Finding 3.

### 3. Action contrast and keyboard focus

Fixed. The action uses `#6CF0FF`, the contrast test checks at least 4.5:1 against the card, and a focused action renders a 2 dp cyan outline.

## Full Review

Verified by inspection:

- The greeting is replaced by one localized permission-state card; no unsupported dashboard, map, route, telemetry, location update, or background-service functionality was added.
- State copy, actions, safe insets, scrolling, 48 dp action target, settings intent/error snackbar, current fine-grant check, Activity Result refresh, and resume refresh follow the task/design.
- Fine location is the gate for the granted state; ordinary denial remains requestable and permanent denial can lead to settings when request history is present.
- `ACCESS_FINE_LOCATION` is declared and no external-storage permission was added.

## Findings

### Finding 1

- Severity: BLOCKING
- Location: `AndroidManifest.xml`, `LocationPermissions.kt`, and `MainActivity.requestLocationPermission`.
- Problem: The implementation adds `ACCESS_COARSE_LOCATION` to the manifest and runtime request.
- Why it matters: This conflicts with the task's explicit "Request fine location only" requirement and the design's "Request only Android fine location permission" decision. The platform constraint must be explicitly reconciled rather than silently expanding authorization scope.
- Required change: Obtain and record a task/design decision permitting coarse only as the Android 12+ companion permission while fine remains the gate, then update the task/design; or remove coarse and revise the conflicting prompt requirement.

### Finding 2

- Severity: NON_BLOCKING
- Location: `PermissionOnboardingScreen.kt`.
- Problem: `fillMaxWidth()` occurs before `widthIn(max = 600.dp)`, so the card receives exact parent-width constraints and cannot be reduced to 600 dp on wide windows.
- Required change: Apply `widthIn(max = 600.dp)` before `fillMaxWidth()`, retaining parent centering, and add a layout test or manually verify the wide measurement.

### Finding 3

- Severity: NON_BLOCKING
- Location: `LocationPermissionStateTest.kt`.
- Problem: The tests named for persisted relaunch and settings-return resume only feed already-covered inputs into the pure reducer. They do not exercise preference reload, recreation, `onResume`, settings return, or the Activity Result boundary.
- Required change: Extract/test the preference-backed state source or add controlled instrumentation coverage for persisted request history after recreation and a fine grant observed on resume.

## Tests and Verification

The original reducer tests cover its four branches. The request-array and contrast tests are meaningful, except that the request-array expectation encodes the unresolved scope expansion. No Compose semantics/layout test covers state content, focus, or wide sizing.

Reviewed the task, design, Iteration 1 review, current implementation, and fix commit `5a19538`. `git diff --check origin/main...HEAD` passed. Gradle could not configure `:app:testDebugUnitTest` because no Android SDK location is configured, so no Android build, lint, unit-test, instrumentation-test, emulator, or device result is claimed.

## Recommendation

CHANGES_REQUESTED
