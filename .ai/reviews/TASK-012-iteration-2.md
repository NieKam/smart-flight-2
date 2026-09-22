# TASK-012 — Review iteration 2

## Result

CHANGES_REQUESTED

## Task

Reviewed against [`.ai/tasks/TASK-012.md`](../tasks/TASK-012.md), the Architect task for foreground display behavior settings.

## Design

Reviewed against [`.ai/designs/TASK-012.md`](../designs/TASK-012.md), the Designer specification for the Display section, accessibility, lifecycle handoff, and map presentation.

## Blocking Findings

### 1. Missing required focused behavior coverage

- **classification:** `BLOCKING_IMPLEMENTATION`
- **file:** `app/src/test/java/kniezrec/com/flightinfo/display/DisplayPreferencesTest.kt`, `app/src/test/java/kniezrec/com/flightinfo/map/MapZoomPolicyTest.kt`, and `app/src/androidTest/java/kniezrec/com/flightinfo/ui/settings/UnitSettingsScreenTest.kt`
- **location:** TASK-012 coverage added in these files
- **problem:** The current tests cover persistence defaults/malformed values, a preference round trip, a pure max-zoom/warning policy, and one enabled-warning/accessibility presentation. They do not verify orientation preference persistence across an activity recreation/resume, the actual keep-screen-on and orientation activity effects for both toggle directions, direct UI toggling of each Display control, in-place `MapView` maximum-zoom update/clamping, or restoration of the standard warning after disabling larger zoom.
- **why it violates the task/design:** TASK-012 acceptance criteria explicitly require focused unit/Compose coverage for each toggle’s side effect, orientation recreation/persistence, map maximum-zoom selection, warning suppression/restoration, and accessibility/state presentation. The design verification expectations likewise call for screen-flag add/clear, orientation persistence, and in-place map update/clamping. The current tests would not catch regressions in these required behaviors.
- **required correction:** Add focused tests in the configured test layers for the missing cases, using test seams around the Android window/orientation APIs and map instance as needed. At minimum, exercise both directions of each activity effect, persisted orientation state after recreation/resume, UI click callbacks/state changes for all three switches including the off-state warning absence, and active-map max-zoom update/clamping plus warning restoration.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the Architect task, Designer specification, iteration-1 review, current feature-branch commits, and the affected activity, display preference store/applier, Settings composable, map composables/rules, resources, tests, call sites, manifest/CI workflow, and diff whitespace. The iteration-1 compile and conditional-warning findings are resolved: `MapCard` receives and propagates `largerMapZoom`, localized strings are resolved outside the semantics lambda, and the Settings warning is only rendered and announced when enabled. The current source includes the requested 6/9 map policy, clamping, offline provider configuration, and warning suppression path.

### Tests Verified

Not verified. `./gradlew testDebugUnitTest --console=plain` was executed but failed before test execution because no Android SDK location is configured (`ANDROID_HOME`/`sdk.dir` is missing). No test result is claimed.

### Build Verified

Not verified. No successful build was obtained; the same SDK-location failure prevented Gradle test task configuration from completing.

### CI Verified

Not verified. The workflow file was inspected, but CI was not run and no CI result is available.

## Recommended Next Action

Developer adds the missing focused TASK-012 tests, then reruns the configured checks in an Android SDK environment and requests another review iteration.
