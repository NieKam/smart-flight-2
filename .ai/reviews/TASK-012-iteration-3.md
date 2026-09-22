# TASK-012 — Review iteration 3

## Result

CHANGES_REQUESTED

## Task

Reviewed against [`.ai/tasks/TASK-012.md`](../tasks/TASK-012.md), the Architect task for foreground display behavior settings.

## Design

Reviewed against [`.ai/designs/TASK-012.md`](../designs/TASK-012.md), the Designer specification for the Display section, lifecycle handoff, map state, and accessibility.

## Blocking Findings

### 1. Display-setting navigation disposes the map and loses its viewport

- **classification:** `BLOCKING_IMPLEMENTATION`
- **file:** `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`; `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/MapCard.kt`
- **location:** `MainActivity.kt:161-178`; `MapCard.kt:102-112, 241-244`
- **problem:** The authorized dashboard and Settings are mutually exclusive branches. Opening Settings removes `GnssStatusScreen` from composition, which disposes `OfflineMap` and calls `MapInstance.dispose()`. A Larger map zoom toggle therefore changes only persisted Compose state while no map instance is active; returning to the dashboard constructs a new map at the factory's default center and zoom. The current in-place `applyMapZoomPolicy` path is only reached after that new map has already been created.
- **why it violates the task/design:** TASK-012 requires changing Larger map zoom to update the active offline map in place where supported, and explicitly requires that settings changes not reset map position except for normal orientation recreation. The design's interaction flow likewise requires returning from Settings to preserve the current map session/position. This implementation loses the viewport for a non-orientation display-setting change and does not exercise the required active-map update path from the actual Settings flow.
- **required correction:** Preserve the map instance/viewport across the authorized Settings branch, or persist and restore the map center and zoom around the branch transition, and route the preference change to the active map policy. Ensure enabling changes only the maximum, while disabling clamps an out-of-range viewport safely and restores warning behavior, without resetting center, overlays, or normal zoom behavior.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the Architect task, Designer specification, iteration-2 review, current feature-branch history and diff, display preference store/applier, MainActivity lifecycle and Settings branch, Settings Compose UI and semantics, map ownership/update/disposal paths, resources, manifest, call sites, and newly added unit/Compose tests. `git diff --check` completed without whitespace errors.

### Tests Verified

Attempted `./gradlew testDebugUnitTest --console=plain`. The task did not execute because the local environment has no configured Android SDK (`ANDROID_HOME`/`sdk.dir` is missing). No test result is claimed.

### Build Verified

Not verified. Gradle configuration stopped at the missing Android SDK location before compilation.

### CI Verified

Not verified. CI was not run.

## Recommended Next Action

Developer fixes the blocking map lifecycle/viewport preservation issue and adds coverage for the actual Settings-to-dashboard map preservation and active max-zoom update flow, then requests another review iteration.
