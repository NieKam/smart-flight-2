# TASK-012 — Review iteration 1

## Result

CHANGES_REQUESTED

## Task

Reviewed against [`.ai/tasks/TASK-012.md`](../tasks/TASK-012.md), the Architect task for foreground display behavior settings.

## Design

Reviewed against [`.ai/designs/TASK-012.md`](../designs/TASK-012.md), the Designer specification for the Display section, accessibility, lifecycle handoff, and map presentation.

## Blocking Findings

### 1. `BLOCKING_IMPLEMENTATION`

- **file:** `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/MapCard.kt`
- **location:** `MapCard` declaration at lines 72–79 and its call at lines 96–102
- **problem:** `MapCard` uses `largerMapZoom` when calling `OfflineMap`, but the public `MapCard` function has no `largerMapZoom` parameter. `GnssStatusScreen` passes that named argument to `MapCard`, so the feature branch does not compile.
- **why it violates the task/design:** The required larger-map-zoom value must be threaded through the existing map implementation and the app must build.
- **required correction:** Add and correctly propagate the parameter through `MapCard`, or otherwise fix the map wiring so the source compiles and the preference controls the active map.

### 2. `BLOCKING_IMPLEMENTATION`

- **file:** `app/src/main/java/kniezrec/com/flightinfo/ui/settings/UnitSettingsScreen.kt`
- **location:** `displaysettingRow`, lines 158–164
- **problem:** The `semantics` receiver is not composable, but it calls `stringResource(...)` while constructing `contentDescription`.
- **why it violates the task/design:** This is another compile-time error, and all new labels/descriptions must remain resource-backed.
- **required correction:** Resolve all localized strings in the composable scope before entering the semantics lambda, then assign the resolved strings there.

### 3. `BLOCKING_IMPLEMENTATION`

- **file:** `app/src/main/java/kniezrec/com/flightinfo/ui/settings/UnitSettingsScreen.kt`
- **location:** lines 116–120 and 173–179
- **problem:** The larger-zoom warning is passed unconditionally and is rendered regardless of whether `largerMapZoom` is enabled.
- **why it violates the task/design:** The task and design require the warning to be shown while larger zoom is enabled; the normal disabled state must not expose the opt-in warning.
- **required correction:** Render and include the warning in accessibility text only when `displayPreferences.largerMapZoom` is true.

### 4. `BLOCKING_IMPLEMENTATION`

- **file:** `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/MapCard.kt`
- **location:** `OfflineMap` update block, lines 263–269
- **problem:** The implementation changes the map maximum and clamps zoom, but contains no implementation of the required standard “maximum zoom reached” warning, nor any suppression/restoration logic tied to `largerMapZoom`.
- **why it violates the task/design:** Acceptance requires the standard warning to be suppressed at the larger range and restored at the standard range. A search of the current map implementation finds no warning path added or controlled by this preference.
- **required correction:** Integrate the existing warning behavior (or add the required behavior if absent in the rewrite) and drive its suppression/restoration from the same larger-zoom preference.

### 5. `BLOCKING_IMPLEMENTATION`

- **file:** `app/src/test/java/kniezrec/com/flightinfo/display/DisplayPreferencesTest.kt`; `app/src/test/java/kniezrec/com/flightinfo/map/MapZoomPolicyTest.kt`
- **location:** all tests added for TASK-012
- **problem:** The added tests cover only persistence defaults/malformed values/round-trip and the numeric max-zoom policy. They do not cover keep-screen-on flag side effects, orientation request/recreation persistence, map in-place update/clamping, warning suppression/restoration, or Settings switch semantics/warning presentation.
- **why it violates the task/design:** The acceptance criteria explicitly require focused coverage for those behaviors, and the current implementation has no tests that would catch the compile/warning/accessibility regressions above.
- **required correction:** Add appropriate focused unit and Compose/instrumentation coverage for the required lifecycle adapters/effects, map update behavior and warning policy, and the Display UI semantics/state presentation, within the configured test environment.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the TASK-012 task and design specifications, the current feature branch diff, affected `MainActivity`, display preference store, Settings composable, map composables, map rules, resources, and the added tests. The source review identified the compile-time issues and missing warning behavior listed above.

### Tests Verified

Not verified. `./gradlew testDebugUnitTest --console=plain` was started, but Gradle remained in task-graph calculation/resolution for several minutes without executing tests and was stopped. No test result is claimed.

### Build Verified

Not verified. No successful build was obtained; source inspection indicates compilation errors in the current implementation.

### CI Verified

Not verified. CI was not run or inspected for an execution result.

## Recommended Next Action

Developer fixes all BLOCKING_IMPLEMENTATION findings, then reruns the configured checks and requests another review iteration.
