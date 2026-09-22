# TASK-014 — Review iteration 4

## Result

CHANGES_REQUESTED

## Task

Reviewed against `.ai/tasks/TASK-014.md` (TASK-014 — Complete authoritative background-monitoring lifecycle).

## Design

Reviewed against `.ai/designs/TASK-014.md` (TASK-014 — Authoritative background-monitoring lifecycle design).

## Blocking Findings

### 1. One forwarded location performs the usable-fix transition twice

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/main/java/kniezrec/com/flightinfo/monitoring/BackgroundMonitoringBridge.kt:104-107`; `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt:496-510`
- location: `BackgroundMonitoringBridge.forwardLocation`; the `FlightParametersController` callback configured in `MainActivity`
- problem: `forwardLocation` invokes the activity location handler and then calls `onUsableLocationFix` itself. The activity handler is `FlightParametersController.acceptLocationFix`, whose `onLocationFix` callback already calls `BackgroundMonitoringBridge.onUsableLocationFix`. Therefore each service-emitted fix reaches the usable-fix transition twice. When backgrounded, the first invocation calls `LocationForegroundService.onUsableFix()` and tears down the session; the second invocation calls it again through the still-attached bridge.
- why it violates the task/design: TASK-014 requires one authoritative event path, exactly-once delivery, and idempotent fix cancellation/teardown. A single usable fix must cancel the waiting state and end the current background observation once, without duplicate service/session mutations or duplicate lifecycle effects.
- required correction: Establish one owner for the usable-fix transition in the shared event path. Remove the duplicate invocation or otherwise guarantee that a forwarded fix can trigger `onUsableFix` only once, and add a test that uses the real activity/controller callback path and asserts one cancellation/teardown for one forwarded fix.

### 2. Required focused coverage still does not cover the implemented Android lifecycle and eligibility paths

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/test/java/kniezrec/com/flightinfo/monitoring/LocationForegroundServiceTest.kt`; `app/src/test/java/kniezrec/com/flightinfo/monitoring/BackgroundMonitoringBridgeTest.kt`
- location: focused monitoring test suites
- problem: The current tests use `TestLocationForegroundService` overrides for eligibility, notification permission, and foreground-start behavior, and directly call service/bridge methods. They do not exercise the actual `MainActivity` notification-tap PendingIntent path or activity recreation handoff, and they do not verify real fine-versus-approximate permission and provider-loss reconciliation, notification permission denial through the platform-facing service path, or recovery after those transitions. The session tests cover fake registration exceptions, but not the Android adapter cleanup behavior itself.
- why it violates the task/design: TASK-014 explicitly requires focused coverage for tap/recreation, approximate-only/fine permission and provider eligibility, notification-permission denial, startup/registration failure, partial-registration cleanup, and stale-generation behavior. The current tests can pass while the activity/service boundary and Android eligibility implementation remain unverified; the acceptance checklist likewise requires lifecycle, eligibility, transition, duplicate-prevention, failure, notification, and stale-callback coverage.
- required correction: Add focused tests through the relevant real seams (or testable platform adapters) for notification tap and activity recreation/repeated handoff, actual permission/provider eligibility loss and recovery, denied notification posting, startup failure, partial/throwing registration cleanup, and the duplicate-fix teardown invariant. Keep the tests focused on one registration/service session and unchanged persisted preference semantics.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the Architect task, Designer specification, TASK-014 review iteration 3, current branch history and implementation, `MainActivity`, `BackgroundMonitoringBridge`, `LocationForegroundService`, `LocationGnssMonitoringSession`, Android location/GNSS adapters, foreground controller attachment contracts, manifest/resources, and all focused monitoring tests. Reviewed the latest developer commit and checked the repository status. `git diff --check` was not run separately in this iteration.

### Tests Verified

Attempted `./gradlew testDebugUnitTest ktlintCheck`. No tests executed: Gradle stopped before task execution because the local Android SDK is unavailable (`ANDROID_HOME`/`local.properties` `sdk.dir` is not configured).

### Build Verified

Not verified. Gradle failed during dependency/task-graph resolution before compilation for the missing Android SDK location.

### CI Verified

Not verified. No CI run was available locally.

## Recommended Next Action

Developer fixes the two BLOCKING_IMPLEMENTATION findings, adds the missing focused lifecycle/eligibility and duplicate-fix coverage, and reruns the configured checks in an Android SDK environment.
