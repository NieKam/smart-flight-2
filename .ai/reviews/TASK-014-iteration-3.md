# TASK-014 — Review iteration 3

## Result

CHANGES_REQUESTED

## Task

Reviewed against `.ai/tasks/TASK-014.md` (TASK-014 — Complete authoritative background-monitoring lifecycle).

## Design

Reviewed against `.ai/designs/TASK-014.md` (TASK-014 — Authoritative background-monitoring lifecycle design).

## Blocking Findings

### 1. Activity recreation tears down the authoritative service/session

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- location: `onCreate()` call to `refreshPermissionState()` and `refreshPermissionState()` lines 404–418
- problem: `isForeground` is false during every newly created activity's `onCreate()`. When permission state is granted, `refreshPermissionState()` therefore takes its `else` branch and calls `stopBackgroundMonitoring()`. The old service/session is stopped before `onResume()` starts a replacement. This creates a stop/start gap, unregisters the authoritative callbacks, and loses the service-owned session/fix state during configuration/activity recreation.
- why it violates the task/design: TASK-014 requires activity recreation and repeated transitions to be idempotent, preserve usable-fix state across the activity/service handoff, and leave at most one service/session and one registration. The design explicitly requires recreation to preserve the same visible state without a reset or duplicate transition.
- required correction: Do not stop the existing background service merely because the new activity has not reached `onResume()` yet. Reconcile the existing service/session from the visible lifecycle, or otherwise preserve and reattach the current generation across recreation; ensure the old service is only stopped for an actual ineligible/teardown condition.

### 2. Exceptional platform registration can leak the callback that was installed before the exception

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/main/java/kniezrec/com/flightinfo/monitoring/LocationGnssMonitoringSession.kt:18-40`; `app/src/main/java/kniezrec/com/flightinfo/flight/AndroidFlightLocationPlatform.kt:22-42`; `app/src/main/java/kniezrec/com/flightinfo/gnss/AndroidGnssStatusPlatform.kt:22-42`
- problem: Both Android adapters assign their listener/callback field before invoking the platform registration method. If `requestLocationUpdates()` or `registerGnssStatusCallback()` throws, the session's `runCatching` converts the exception to `false`, but `locationRegistered`/`gnssRegistered` remains false. `stop()` consequently skips the corresponding unregister call even though the adapter has retained the callback object. The same issue occurs for partial GNSS registration after location registration succeeds.
- why it violates the task/design: TASK-014 explicitly requires exact callback unregistration, including partial-registration failure, and requires registration failure to release all callbacks/resources without stale session mutations. A throwing platform registration is a supported failure path in the Android adapters and is currently not cleaned up.
- required correction: Make the platform/session ownership record an installed callback before registration can fail, or provide failure cleanup in the adapter/session so every callback field that was installed is unregistered. Add tests for exceptions from both location and GNSS registration and assert all corresponding unregister operations occur.

### 3. Required focused lifecycle/failure coverage remains incomplete

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/test/java/kniezrec/com/flightinfo/monitoring/LocationForegroundServiceTest.kt`, `app/src/test/java/kniezrec/com/flightinfo/monitoring/LocationGnssMonitoringSessionTest.kt`
- location: focused monitoring test suites
- problem: The new service tests cover several happy-path transitions through test overrides, but they do not exercise the actual eligibility implementation for fine/approximate permission and provider loss, notification tap through the activity path, activity recreation/repeated service handoff, `startForeground` startup failure, or exceptional/partial callback-registration cleanup. The session tests cover a false GNSS registration result but not throwing location/GNSS registration or the adapter callback-retention leak described above. The preference-off test also never writes the preference Off, so it does not verify persisted setting semantics.
- why it violates the task/design: TASK-014 requires focused automated coverage for eligibility loss, tap/dismissal, recreation/repeated transitions, startup and registration failure, partial registration cleanup, stale-generation handling, and notification denial. The current suite can pass while these implemented paths remain incorrect.
- required correction: Add fake seams or platform-facing tests that drive the real service/session boundary for each mandated path, including actual approximate-only/fine permission and provider eligibility, tap/recreation, startup failure, throwing/partial registration, and persistence invariants. Keep the tests focused on one authoritative registration and stable notification cleanup.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the TASK-014 Architect task, TASK-014 Designer specification, TASK-014 iteration-1 and iteration-2 review artifacts, the current `feat/TASK-014` implementation and diff, `MainActivity` lifecycle/service calls, `BackgroundMonitoringBridge`, `LocationForegroundService`, `LocationGnssMonitoringSession`, Android location/GNSS platform adapters, existing controller attachment contracts, manifest/resources, and all focused monitoring tests. `git diff --check` completed without whitespace errors.

### Tests Verified

Attempted `./gradlew testDebugUnitTest ktlintCheck`. No tests executed: Gradle stopped before task execution because the local Android SDK is unavailable (`ANDROID_HOME`/`local.properties` `sdk.dir` is not configured).

### Build Verified

Not verified. Gradle failed during dependency/task-graph resolution before compilation for the missing Android SDK location.

### CI Verified

Not verified. No CI run was available locally.

## Recommended Next Action

Developer fixes the three BLOCKING_IMPLEMENTATION findings, adds the missing focused lifecycle and exception-cleanup coverage, and reruns the configured checks in an Android SDK environment.
