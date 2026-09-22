# TASK-014 — Review iteration 6

## Result

CHANGES_REQUESTED

## Task

Reviewed against `.ai/tasks/TASK-014.md` (TASK-014 — Complete authoritative background-monitoring lifecycle).

## Design

Reviewed against `.ai/designs/TASK-014.md` (TASK-014 — Authoritative background-monitoring lifecycle design).

## Blocking Findings

### 1. Activity recreation still stops the authoritative service/session before resume

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- location: `onCreate()` line 148 and `refreshPermissionState()` lines 409–421
- problem: `isForeground` is false during a newly created activity’s `onCreate()`. When permission is granted, `refreshPermissionState()` therefore enters the `else if` branch and calls `stopBackgroundMonitoring()` at line 420. During configuration recreation, the previous activity deliberately skips clearing/stopping in `onDestroy()` at lines 402–405, but the replacement activity then stops that still-authoritative service before `onResume()` starts it again.
- why it violates the task/design: TASK-014 requires activity recreation and repeated transitions to retain one authoritative session, preserve usable-fix state, and avoid a stop/start gap or duplicate listener. The design explicitly requires recreation to leave the same dashboard/session state without resetting the service-owned monitoring run.
- required correction: Do not stop the existing background service from the pre-resume permission refresh of a replacement activity. Reconcile/reattach it from the visible lifecycle, or otherwise distinguish an actual ineligible condition from the temporary pre-resume recreation state so the existing service/session and generation survive configuration changes.

### 2. Required activity-level lifecycle coverage is still absent

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/test/java/kniezrec/com/flightinfo/monitoring/LocationForegroundServiceTest.kt`, `app/src/test/java/kniezrec/com/flightinfo/monitoring/BackgroundMonitoringBridgeTest.kt`
- location: service recreation/handoff tests at `LocationForegroundServiceTest.kt:216-261`; bridge tests throughout `BackgroundMonitoringBridgeTest.kt`
- problem: The added tests exercise a Robolectric service replacement and direct bridge calls, but no test creates/recreates `MainActivity` or drives its actual `onCreate`/`onResume`/`onPause`/`onDestroy` path. Consequently the regression above is not detected. The “activity recreation” test only repeats service calls and visibility flags; it never invokes the activity’s `refreshPermissionState()` or verifies that the old service remains active through configuration recreation.
- why it violates the task/design: The task and design explicitly require focused coverage for activity recreation, repeated foreground/background handoff, retained fix state, and duplicate prevention. Coverage that bypasses the activity boundary can pass while the implemented lifecycle tears down the authoritative session.
- required correction: Add a focused Robolectric/instrumentation test through the activity lifecycle (or an equivalent seam that executes the same `onCreate` permission-refresh behavior) which recreates a granted activity while the service is active and asserts no stop/start gap, one service/session/registration, retained usable-fix state, and correct subsequent foreground/background reconciliation.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the Architect task, Designer specification, TASK-014 review iterations 1–5, current `feat/TASK-014` history and implementation, `MainActivity`, `BackgroundMonitoringBridge`, `LocationForegroundService`, `LocationGnssMonitoringSession`, Android location/GNSS adapters, foreground controller handoff, manifest/resources, and all focused monitoring tests. Confirmed the earlier duplicate usable-fix call and registration-cleanup changes are present. Ran `git diff --check` successfully.

### Tests Verified

No tests executed. `./gradlew testDebugUnitTest ktlintCheck` failed before task execution because the local Android SDK is unavailable: `ANDROID_HOME` and `local.properties` `sdk.dir` are not configured.

### Build Verified

Not verified. Gradle stopped before compilation while resolving the missing Android SDK location.

### CI Verified

Not verified. No CI run was available locally.

## Recommended Next Action

Developer fixes the activity recreation teardown and adds real activity-level lifecycle coverage, then reruns the configured checks in an Android SDK environment.
