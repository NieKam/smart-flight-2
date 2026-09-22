# TASK-014 — Review iteration 5

## Result

CHANGES_REQUESTED

## Task

Reviewed against `.ai/tasks/TASK-014.md` (TASK-014 — Complete authoritative background-monitoring lifecycle).

## Design

Reviewed against `.ai/designs/TASK-014.md` (TASK-014 — Authoritative background-monitoring lifecycle design).

## Blocking Findings

### 1. Turning the setting Off disables the visible dashboard's monitoring session

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- location: `onResume()` lines 373-377 and `startObservation()` lines 578-582
- problem: When the persisted “Show background notification” setting is Off, `onResume()` stops `LocationForegroundService`, but `refreshPermissionState()` has already called `startObservation()`, which marks both foreground controllers as consuming an external service session. With no service running, there is no authoritative location/GNSS registration to forward events to those controllers. The same break occurs when the setting is toggled Off while visible: the bridge stops the service while the controllers remain attached to the now-absent external session.
- why it violates the task/design: The task explicitly preserves TASK-013’s foreground dashboard behavior and says the notification setting controls background notification behavior, while setting changes must reconcile without changing foreground calculations. A setting Off must not leave the visible dashboard without its location/GNSS monitoring path.
- required correction: Keep a valid authoritative foreground monitoring session when the authorized activity is visible even when background notification is Off, or otherwise make the controllers use a valid foreground-owned registration in that state. Reconcile the setting without stopping the only session needed by the visible dashboard, and add a regression test for foreground monitoring with the setting Off and for turning it Off while visible.

### 2. Required activity lifecycle coverage is still incomplete

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/test/java/kniezrec/com/flightinfo/monitoring/LocationForegroundServiceTest.kt`; `app/src/test/java/kniezrec/com/flightinfo/monitoring/BackgroundMonitoringBridgeTest.kt`
- location: service tests lines 99-113 and 193-209; bridge tests lines 17-107
- problem: The added tests inspect the notification PendingIntent and directly exercise bridge/service doubles, but do not execute the actual MainActivity notification-tap path, activity recreation handoff, or a real repeated activity resume/pause transition. The “recreation” test destroys and recreates only the Robolectric service and explicitly clears the bridge; it does not verify that an activity configuration recreation retains the authoritative service/session. Eligibility tests only call protected predicates and do not drive provider/permission loss through the running service cleanup path.
- why it violates the task/design: TASK-014 explicitly requires focused automated coverage for notification tap, activity recreation, repeated handoff, eligibility loss, duplicate prevention, and stale-generation behavior. The acceptance criteria require those lifecycle transitions to leave one session/registration and preserve state; the current suite can pass while the activity/service wiring remains wrong.
- required correction: Add focused tests through the activity/service seams (or equivalent testable lifecycle seams) that perform notification tap/foreground reconciliation, configuration recreation without a stop/start gap, repeated resume/pause handoff, and running-service cleanup after fine/approximate permission or provider loss. Assert the one-registration and retained-fix invariants and preserve preference semantics.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the Architect task, Designer specification, TASK-014 review iterations 1–4, current feature-branch history, `MainActivity`, `BackgroundMonitoringBridge`, `LocationForegroundService`, `LocationGnssMonitoringSession`, Android location/GNSS adapters, foreground controllers, manifest/resources, and focused monitoring tests. Verified that the prior duplicate usable-fix call was removed and that the latest tests add service notification, eligibility predicate, and generation-guard cases. Ran `git diff --check` successfully.

### Tests Verified

Attempted `./gradlew testDebugUnitTest ktlintCheck`. No tests executed: Gradle stopped during task dependency resolution because the local Android SDK is unavailable (`ANDROID_HOME`/`local.properties` `sdk.dir` is not configured).

### Build Verified

Not verified. The configured Gradle invocation failed before compilation for the missing Android SDK location.

### CI Verified

Not verified. No CI run was available locally.

## Recommended Next Action

Developer fixes both BLOCKING_IMPLEMENTATION findings, adds the missing activity/service lifecycle and setting-Off regression coverage, and reruns the configured checks in an Android SDK environment.
