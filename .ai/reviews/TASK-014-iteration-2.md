# TASK-014 — Review iteration 2

## Result

CHANGES_REQUESTED

## Task

Reviewed against `.ai/tasks/TASK-014.md` (TASK-014 — Complete authoritative background-monitoring lifecycle).

## Design

Reviewed against `.ai/designs/TASK-014.md` (TASK-014 — Authoritative background-monitoring lifecycle design).

## Blocking Findings

### 1. Required service-boundary and failure-path test coverage is still missing

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/test/java/kniezrec/com/flightinfo/monitoring/BackgroundMonitoringBridgeTest.kt`, `app/src/test/java/kniezrec/com/flightinfo/monitoring/LocationGnssMonitoringSessionTest.kt`
- location: `BackgroundMonitoringBridgeTest.kt:17-107`; no focused test for `LocationForegroundService`
- problem: Iteration 2 adds bridge tests and retains low-level session tests, but the new tests use only a `FakeService`. They do not exercise the implemented `LocationForegroundService` behavior or assert notification publication/cancellation, notification-permission denial, preference/provider/permission eligibility cleanup, service startup failure, partial platform registration failure through the service, notification dismissal, service teardown, or activity recreation/repeated service reconciliation. The existing session tests cover GNSS registration failure, but do not cover the service-level cleanup and stable notification contract that consumes that result.
- why it violates the task/design: TASK-014 explicitly requires focused fake-platform/session/bridge tests for the lifecycle, eligibility, transition, duplicate-prevention, failure, notification, and stale-generation requirements, plus platform-facing coverage where supported. The design likewise requires these transitions to leave no stale notification or duplicate service/session. The current tests can pass while the Android service wiring in `LocationForegroundService.kt:46-97` and `:117-184` is wrong, so the acceptance criterion for focused automated coverage is not met.
- required correction: Add focused tests around the actual service/session boundary using injectable or otherwise testable fake platform, notification-manager, permission/provider, and lifecycle seams. Assert one location/GNSS registration, visible/background handoff and foreground return/tap, usable-fix cancellation, dismissal semantics, setting-off cleanup, precise/approximate permission and provider loss, notification denial, startup and partial-registration failure, recreation/repeated transitions, stable notification ID, and stale location/GNSS callback rejection. Add platform-facing notification/service tests where the configured environment supports them.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the Architect task, Designer specification, iteration-1 review, current branch changes, `MainActivity`, `BackgroundMonitoringBridge`, `LocationForegroundService`, `LocationGnssMonitoringSession`, the foreground controller attachment contracts, Android platform adapters, manifest-related call sites, and all focused monitoring tests. The preference-off lifecycle correction is present: the activity calls `setNotificationEnabled`, and the service exposes teardown for that path. The uncommitted developer change in `BackgroundMonitoringBridgeTest.kt` was not modified or included in this review commit.

### Tests Verified

Attempted `./gradlew testDebugUnitTest ktlintCheck`. Gradle did not execute the tests because the local environment has no configured Android SDK (`ANDROID_HOME`/`local.properties` `sdk.dir` is missing).

### Build Verified

Not verified. Gradle failed during task dependency resolution before compilation because the Android SDK location is unavailable.

### CI Verified

Not verified. No CI run was available locally.

## Recommended Next Action

Developer adds the required service-boundary, notification, eligibility, lifecycle, failure, and stale-callback tests, then reruns the configured checks in an Android SDK environment. The workflow may proceed to another review after those changes.
