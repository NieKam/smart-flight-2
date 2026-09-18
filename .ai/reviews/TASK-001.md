# TASK-001 Review

## Iteration

3

## Status

CHANGES_REQUESTED

## Summary

The implementation retains the correct minimal, platform-derived permission onboarding and addresses the durable request-history and accessible-action issues raised in Iteration 1. It still expands the authorization requested by TASK-001 from fine location to fine plus coarse location. The wide-window layout cap remains ineffective because the modifier order supplies `widthIn` with an already exact width. The core reducer tests are meaningful, but the tests named for relaunch and settings return do not exercise either integration boundary.

## Previous Findings Verification

### Android 12+ request compatibility and fine-location-only scope

Not fixed as scoped. `RequestMultiplePermissions` with fine and coarse location is Android 12+ compatible, but `ACCESS_COARSE_LOCATION` is now both declared and requested. TASK-001 explicitly says to request fine location only, and the design repeats that restriction. The implementation cannot be approved while it silently expands that scope; the requirements owner must resolve the conflict between the written fine-only scope and the Android 12+ companion-permission behavior, then the implementation must match the resolved requirement.

### Durable request history

Fixed in implementation. `requestLocationPermission()` persists the fact that a request was launched before opening the platform dialog, `onCreate()` restores it, and `refreshPermissionState()` continues to derive the displayed state from the current fine-location grant and platform rationale result. The test coverage remains incomplete; see Finding 3.

### Action contrast and keyboard focus

Fixed. The action uses the higher-contrast `#6CF0FF` color, the contrast calculation test checks it against the card color at 4.5:1 or higher, and the focused action draws a 2 dp cyan outline.

### Wide-window card cap

Not fixed. No implementation change occurred after the Iteration 2 review. `fillMaxWidth()` still precedes `widthIn(max = 600.dp)`; therefore the latter receives exact parent-width constraints and cannot reduce a wider card.

## Requirements

Verified by inspection:

- The sample greeting is replaced by a localized Smart Flight permission screen containing one state card and no unsupported dashboard, map, route, satellite, telemetry, location-update, or background-service functionality.
- The current fine-location grant determines the granted entry state. A permission result and every activity resume refresh the platform-derived state.
- Requestable denial retains `Grant permission`; an already-requested, no-rationale denial derives `Open settings`; the settings action targets this application's details page and reports a failed handoff with a snackbar.
- `ACCESS_FINE_LOCATION` is declared and `WRITE_EXTERNAL_STORAGE` is absent.
- The acceptance criterion requiring only fine-location permission is not met because coarse location is also declared and requested (Finding 1).

## Design

The header, single-card structure, localized copy, fixed purple/cyan palette, 10 dp card corners, 4 dp elevation, 12 dp outer spacing, 160 dp minimum card height, 48 dp minimum action target, safe-area treatment, scrolling, crossfade, and minimal granted state follow the design. The body color was intentionally moved to the accessible light-lavender token, which is permitted by the design's contrast guidance.

The expanded-window 600 dp maximum width requirement is not satisfied (Finding 2). Other responsive and accessibility behavior was inspected in source but not run on a device or emulator.

## Architecture

The existing rewrite has no ViewModels, repositories, use cases, DI setup, or Flow/StateFlow screen-state pattern; it is an initial Compose application rooted in `MainActivity`. For this small, platform-bound onboarding flow, keeping Activity Result registration, permission checks, settings intent construction, and the one persisted request-history flag in `MainActivity` is consistent with that established structure. A ViewModel or repository is not required merely to add one.

The UI/state boundary is otherwise appropriate: `PermissionOnboardingScreen` renders the supplied state and emits callbacks, while the pure `locationPermissionState` reducer is independently testable. The remaining deficiency is verification of the Activity/preferences lifecycle boundary rather than an unjustified architectural abstraction (Finding 3).

## Findings

### Finding 1

- Severity: BLOCKING
- Location: `app/src/main/AndroidManifest.xml`; `app/src/main/java/kniezrec/com/flightinfo/LocationPermissions.kt` (`locationPermissionRequest`); `MainActivity.requestLocationPermission`
- Problem: The application declares and requests `ACCESS_COARSE_LOCATION` together with `ACCESS_FINE_LOCATION`.
- Why it matters: TASK-001's technical requirement says “Request fine location only,” and the design says “Request only Android fine location permission.” Adding coarse access changes the authorization scope without a task or design decision, even though fine remains the state gate.
- Required change: Resolve the documented conflict with the task/design owner before changing the behavior further. If the scope remains fine-only, remove coarse declaration and request from the implementation. If Android 12+ support requires coarse as a companion request, obtain an explicit task/design update authorizing that exception and retain fine location as the only feature gate. Do not leave the scope expansion implicit.

### Finding 2

- Severity: NON_BLOCKING
- Location: `app/src/main/java/kniezrec/com/flightinfo/PermissionOnboardingScreen.kt`, `PermissionStateCard` card modifier
- Problem: `fillMaxWidth()` runs before `widthIn(max = 600.dp)`. `fillMaxWidth()` supplies the inner modifier an exact available width, so the downstream size range must honor that exact incoming constraint rather than reducing the card to 600 dp.
- Why it matters: On landscape and expanded windows the card fills the available content width instead of observing the design's 600 dp cap and centered presentation.
- Required change: Apply `widthIn(max = 600.dp)` before `fillMaxWidth()` so the fill operation is constrained to 600 dp, keep the enclosing `Box` centering, and add a Compose layout test that verifies the cap on a width greater than 624 dp.

### Finding 3

- Severity: NON_BLOCKING
- Location: `app/src/test/java/kniezrec/com/flightinfo/LocationPermissionStateTest.kt`, `persistedRequestHistoryKeepsPermanentDenialInSettingsStateAfterRelaunch` and `grantInSettingsShowsGrantedStateWhenTheActivityResumes`
- Problem: Both tests call the same pure reducer with inputs already covered by the preceding tests. They do not write/read `SharedPreferences`, recreate the activity, invoke `onResume`, observe an Activity Result callback, or validate a changed platform grant.
- Why it matters: The two most failure-prone lifecycle requirements—durable request history and state refresh after returning from settings—remain unverified despite test names that imply end-to-end coverage.
- Required change: Add controlled integration coverage for the preferences/recreation and resume boundaries (instrumented tests are already supported by the project), or extract a narrowly scoped, injectable permission-state source and unit-test persistence plus a changed fine-location grant. Keep the existing reducer tests, but rename or remove tests whose names claim behavior they do not exercise.

## Tests

Inspected:

- `LocationPermissionStateTest`: four reducer-branch tests are meaningful; the two lifecycle-named tests duplicate reducer inputs and are not lifecycle/persistence tests.
- `LocationPermissionsTest`: the contrast assertion is meaningful. The fine-and-coarse request assertion accurately tests the current implementation but codifies the unresolved scope expansion.
- Existing sample unit and instrumentation tests do not cover TASK-001 behavior.

Ran `./gradlew :app:testDebugUnitTest`; Gradle failed during task configuration because no Android SDK location is configured (`ANDROID_HOME` or `local.properties` `sdk.dir`). No Android build, lint, unit-test, instrumentation-test, emulator, or device result is claimed. `git diff --check origin/main...HEAD` passed.

## Verification

Reviewed `.ai/tasks/TASK-001.md`, `.ai/designs/TASK-001.md`, the Iteration 2 review, all current TASK-001 Kotlin/Compose, manifest, strings, tests, project Gradle conventions, and `origin/main...HEAD`. The post-Iteration-2 Git history contains only the reviewer-configuration update; no application implementation change addresses the outstanding findings.

## Recommendation

CHANGES_REQUESTED
