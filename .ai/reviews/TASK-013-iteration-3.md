# TASK-013 — Review iteration 3

## Result

CHANGES_REQUESTED

## Task

Reviewed against `.ai/tasks/TASK-013.md` (TASK-013 — Add optional background flight monitoring notification).

## Design

Reviewed against `.ai/designs/TASK-013.md` (TASK-013 Design Specification — Optional background flight-monitoring notification).

## Blocking Findings

### 1. Required monitoring lifecycle and failure-path coverage is still missing

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/test/java/kniezrec/com/flightinfo/monitoring/BackgroundMonitoringTest.kt`, `app/src/test/java/kniezrec/com/flightinfo/monitoring/LocationGnssMonitoringSessionTest.kt`
- location: entire focused monitoring test coverage
- problem: The current tests cover preference default/malformed-value handling, a small abstract state machine, and low-level callback registration. They do not exercise the implemented `BackgroundMonitoringBridge`/`LocationForegroundService` lifecycle or assert notification eligibility and transitions for backgrounding, foreground return/tap, first fix, notification dismissal, setting changes, fine-location/location-service failure, notification-permission denial, or service-start/registration failure. They also do not verify the activity-to-service handoff remains a single session across recreation and repeated transitions.
- why it violates the task/design: TASK-013's acceptance criteria explicitly require focused tests for session start/stop and duplicate prevention, notification eligibility, fix/dismissal/foreground transitions, permission/service failure paths, and Settings semantics where supported. These are the core correctness paths of the feature; the present tests can pass while the service/bridge wiring or notification behavior is incorrect.
- required correction: Add focused fake-platform/session/bridge tests that drive the implemented lifecycle and assert one authoritative location/GNSS registration, notification eligibility/cancellation, fix and dismissal stopping behavior, foreground/tap reconciliation, setting-off behavior, permission/location-service failure cleanup, notification-permission degradation, and stale-callback/generation handling. Add platform-facing or instrumentation coverage for service/notification behavior where the configured environment supports it, and retain/extend Settings semantics coverage.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the Architect task, Designer specification, TASK-013 iteration-1 and iteration-2 review artifacts, the current `feat/TASK-013` implementation, manifest permissions/service declaration, `MainActivity` lifecycle and handoff, `FlightParametersController`, `GnssStatusController`, Android location/GNSS adapters, monitoring bridge/session/service/preferences, Settings UI/resources, and focused tests. The current source review verifies that the latest change gives the service ownership of the location/GNSS registrations and forwards events through the bridge; the remaining finding concerns incomplete required test coverage.

### Tests Verified

Attempted `./gradlew testDebugUnitTest ktlintCheck`. It did not execute because Gradle could not locate an Android SDK (`ANDROID_HOME`/`sdk.dir` is not configured).

### Build Verified

Not verified. Gradle configuration failed before compilation for the missing Android SDK location.

### CI Verified

Not verified. No CI run was available locally.

## Recommended Next Action

Developer adds the required focused lifecycle, notification eligibility, transition, duplicate-prevention, and failure-path tests, then reruns the configured checks in an Android SDK environment.
