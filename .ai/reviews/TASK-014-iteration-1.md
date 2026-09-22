# TASK-014 — Review iteration 1

## Result

CHANGES_REQUESTED

## Task

Reviewed against `.ai/tasks/TASK-014.md` (TASK-014 — Complete authoritative background-monitoring lifecycle).

## Design

Reviewed against `.ai/designs/TASK-014.md` (TASK-014 — Authoritative background-monitoring lifecycle design).

## Blocking Findings

### 1. Required lifecycle, eligibility, notification, and handoff coverage is still missing

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/test/java/kniezrec/com/flightinfo/monitoring/BackgroundMonitoringTest.kt`, `app/src/test/java/kniezrec/com/flightinfo/monitoring/LocationGnssMonitoringSessionTest.kt`
- location: focused monitoring test coverage
- problem: The implementation adds only a stale location-callback generation test. The focused tests still do not drive the implemented `BackgroundMonitoringBridge`/`LocationForegroundService` behavior for one-registration ownership, visible-to-background handoff, foreground return or notification tap, usable-fix cancellation, dismissal, setting-off, fine/approximate permission and provider loss, notification-permission denial, service/registration/startup failure, recreation, repeated transitions, or stale GNSS callbacks.
- why it violates the task/design: TASK-014 explicitly requires focused fake-platform/session/bridge tests for these lifecycle, eligibility, transition, duplicate-prevention, failure, notification, and stale-generation paths. Without tests at the implemented service/bridge boundary, the required correctness behavior is not covered and the existing tests can pass while the lifecycle wiring is wrong.
- required correction: Add focused tests that exercise the actual bridge/service session contract and assert exactly one location/GNSS registration, correct notification eligibility and cancellation, handoff and return/tap behavior, dismissal semantics, setting and eligibility cleanup, startup/partial-registration failures, notification denial, recreation/repeated transitions, and stale location/GNSS callback rejection. Add platform-facing coverage where the configured environment supports it.

### 2. Turning the notification setting Off does not release the active service/session immediately

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`, `app/src/main/java/kniezrec/com/flightinfo/monitoring/BackgroundMonitoringBridge.kt`
- location: `MainActivity.kt:194-199`, `BackgroundMonitoringBridge.kt:55-61`
- problem: The Settings callback invokes `BackgroundMonitoringBridge.setActivityVisible(isForeground)` when the value becomes false. Because the activity is visible at that point, the bridge calls `LocationForegroundService.reconcile(true, ...)`; the service does not consult the preference for a visible activity and therefore keeps the service-owned location/GNSS session and callbacks active. Enabling the preference again also does not start or reconcile a service if it is not already running.
- why it violates the task/design: TASK-014 requires turning the setting Off to release the current run, unregister callbacks, and cancel the stable notification, and requires setting changes to reconcile immediately. The design likewise specifies immediate release on preference Off and no new notification until it is turned On again.
- required correction: Reconcile the preference change through an operation that stops the current service/session and cancels its notification when disabled, and starts/reconciles the eligible service before the activity can background when enabled. Preserve the persisted preference and permission state.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the TASK-014 Architect task, TASK-014 Designer specification, TASK-013 iteration-3 review context, the current feature-branch diff, `MainActivity`, `BackgroundMonitoringBridge`, `LocationForegroundService`, `LocationGnssMonitoringSession`, `BackgroundMonitoringSession`, the existing flight/GNSS controller ownership contracts, manifest, and focused monitoring tests.

### Tests Verified

Attempted `./gradlew testDebugUnitTest ktlintCheck`. It did not execute because the local environment has no configured Android SDK (`ANDROID_HOME`/`local.properties` `sdk.dir` is missing).

### Build Verified

Not verified. Gradle failed during task dependency resolution before compilation for the missing Android SDK location.

### CI Verified

Not verified. No CI run was available locally.

## Recommended Next Action

Developer fixes both BLOCKING_IMPLEMENTATION findings, adds the required focused lifecycle and failure-path tests, and reruns the configured checks in an Android SDK environment.
