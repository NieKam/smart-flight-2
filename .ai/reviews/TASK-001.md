# TASK-001 Review

## Status

CHANGES_REQUESTED

## Summary

The implementation delivers the intended single-card Compose onboarding, legacy-inspired visual treatment, settings handoff, and a small pure state test suite. However, the current fine-only runtime request is not functional on supported Android 12+ configurations, and the request-history state is lost on a process restart. Both prevent required permission paths from working reliably.

## Requirements

Verified by inspection:

- The manifest declares `ACCESS_FINE_LOCATION` and does not add external-storage permission.
- The sample greeting has been replaced with localized requestable, settings-required, and minimal granted states; no flight, map, route, satellite, telemetry, or location-update functionality was added.
- The UI action uses the Activity Result permission API, and the result/current platform grant is rechecked rather than trusting the callback Boolean.
- Ordinary denial remains requestable in the state reducer, the settings action targets this package's application-details page, and `onResume` rechecks the platform grant.

The Android 12+ prompt requirement and durable permanent-denial path are not satisfied; see Findings 1 and 2.

## Design

The shell, single 160 dp minimum card, centered localized copy, 12 dp outer spacing, 10 dp rounding, 4 dp elevation, fixed purple surfaces, safe insets, scrollable content, 600 dp card cap, and 48 dp action target follow the specification closely. State copy matches the prescribed strings and the settings-failure snackbar is present.

The action accessibility treatment does not fully meet the specification: its supplied cyan has insufficient normal-text contrast on the card and there is no explicit keyboard-focus indicator (Finding 3).

## Findings

### Finding 1

- Severity: BLOCKING
- Location: `app/src/main/AndroidManifest.xml:5`; `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt:37-65`
- Problem: The app targets SDK 37 but declares and launches only `ACCESS_FINE_LOCATION` through `RequestPermission`. On some Android 12 releases, a fine-location-only runtime request is ignored; Android requires fine and coarse location to be requested together to display the location prompt for apps targeting Android 12 or above.
- Why it matters: Tapping **Grant permission** can fail to show the Android permission prompt, so the primary acceptance criterion and grant transition cannot be relied on for the supported `minSdk` 31+ device range. [Android's location runtime-permission guidance](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime) documents this behavior.
- Required change: Reconcile the task's fine-only wording with the Android 12+ platform requirement, then make the request platform-compatible (normally declare/request fine and coarse together in one `RequestMultiplePermissions` call while treating only fine as success). If requesting any permission other than fine remains prohibited, the task requirements must be changed because this implementation cannot reliably meet the prompt criterion on its declared Android range.

### Finding 2

- Severity: BLOCKING
- Location: `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt:34-44, 90-114`
- Problem: `hasRequestedPermission` is retained only in `savedInstanceState`. After a permanent denial, a process death, force-stop, or ordinary app relaunch loses that history. Since `shouldShowRequestPermissionRationale()` is false both before a first request and after permanent denial, the restarted activity maps the permanently denied platform state back to `Requestable`.
- Why it matters: The user is again shown **Grant permission** rather than **Open settings**; a subsequent request can be suppressed by Android. This violates the permanent-denial acceptance criterion and the design requirement to derive the settings-required state from actual request/denial history.
- Required change: Persist actual request history across process recreation (and retain the current-grant check as the source of truth), then rederive the state on creation/resume. Add coverage for a persisted-request + denied + no-rationale relaunch, as well as the return-from-settings grant path.

### Finding 3

- Severity: NON_BLOCKING
- Location: `app/src/main/java/kniezrec/com/flightinfo/PermissionOnboardingScreen.kt:43, 124-136`
- Problem: `#25E5FE` on the `#5B5999` card is approximately 4.11:1 contrast, below the 4.5:1 normal-text target applicable to the 18 sp action label. The `TextButton` also has no explicit cyan 2 dp outline or equivalent visible keyboard-focus treatment.
- Why it matters: This misses the design's contrast-verification and keyboard-focus requirements, reducing readability and operability for users who rely on high contrast or keyboard/switch navigation.
- Required change: Use a sufficiently light accessible cyan (while retaining the visual language) and add a visible focus indicator for the text action; verify both with an accessibility/Compose UI test or documented contrast calculation.

## Tests

`LocationPermissionStateTest` meaningfully covers all four branches of the pure state reducer: granted, first launch, ordinary denial, and permanent denial when request history is supplied. It does not cover the Activity Result launch contract, Android 12+ location-request compatibility, saved/process-recreated history, settings intent, resume refresh, or UI semantics/state content. The additional tests requested in Findings 1 and 2 are needed for the lifecycle behavior that is currently untested.

## Verification

Reviewed `.ai/tasks/TASK-001.md`, `.ai/designs/TASK-001.md`, the current implementation, the TASK-001 commit (`8cbc9e6`), and the relevant legacy permission implementation. `git show --check 8cbc9e6` completed without whitespace errors. An offline `./gradlew testDebugUnitTest` invocation did not yield a Gradle task result in this environment, so no Android build, unit-test, emulator, or device result is claimed.

## Recommendation

CHANGES_REQUESTED
