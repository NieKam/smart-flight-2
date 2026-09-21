# TASK-007 — Review iteration 2

## Result

CHANGES_REQUESTED

## Task

Reviewed against [`.ai/tasks/TASK-007.md`](../tasks/TASK-007.md), including the offline-only map, shared foreground position/course path, session lifecycle, failure handling, accessibility, responsive layout, and focused test requirements.

## Design

Reviewed against [`.ai/designs/TASK-007.md`](../designs/TASK-007.md), including the map card placement and states, neutral course fallback, native-map interop boundary, offline behavior, controls, and teardown expectations.

## Blocking Findings

### 1. Invalid or missing course retains a non-neutral orientation

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/main/java/kniezrec/com/flightinfo/map/MapState.kt`
- location: `MapSessionRules.accept`, lines 43–51
- problem: `normalizeCourse(fix.bearingDegrees)?.let { markerCourse = it }` updates the orientation only for valid bearings. After a valid bearing has set (for example) 270 degrees, a subsequent fix with a missing, non-finite, or otherwise invalid bearing leaves the marker at 270 degrees.
- why it violates the task/design: TASK-007 requires a neutral orientation when no valid course is available. The Designer specification likewise says missing, non-finite, or out-of-range course leaves the last neutral orientation. Invalid course must not cause a previously valid orientation to be presented as current.
- required correction: Set the marker orientation to the neutral value when the current fix has no valid normalized bearing, and add/update focused tests covering a valid bearing followed by missing and invalid bearings.

### 2. Map-open failure creates an unconfigured network-capable fallback MapView

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/MapCard.kt`
- location: `OfflineMap` `AndroidView` factory, lines 225–242
- problem: When offline provider/archive setup throws, the catch branch calls `onOpenFailure()` but still returns `MapView(context)` without the offline provider or `setUseDataConnection(false)`. That fallback native map can use osmdroid's default network behavior while the required unavailable state is being installed.
- why it violates the task/design: TASK-007 requires that missing, corrupt, or unreadable archives show an unavailable state without network requests. Both the task and design require explicit offline configuration and prohibit online map access, including failure/retry paths.
- required correction: Do not create a default network-capable map after open failure. Return a safely configured offline-disabled/empty native view only if the interop API requires a non-null view, or restructure the state boundary so no MapView is created after failure; ensure the failure path cannot request network tiles and add a deterministic test for it.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the Architect task, Designer specification, iteration 1 review, current Developer branch implementation, current diff, existing dashboard/lifecycle/location/course architecture, map resources, and map unit/instrumentation tests. Confirmed the archive SHA-256 is `d69fb06c06ae3d66fc95f51365ef350a310aed3f9092b9684bffeeb0ecfcb8ff`, matching the task-recorded legacy asset. Confirmed the prior iteration's expanded layout, instance-scoped map ownership, guarded open path, and archive/Compose test additions are present. `git diff --check HEAD~1..HEAD` passed.

### Tests Verified

Attempted `./gradlew testDebugUnitTest ktlintCheck`. It did not execute tests because Gradle could not locate an Android SDK: `ANDROID_HOME` is unset and `local.properties` is absent.

### Build Verified

Not verified. The configured Gradle invocation failed during task dependency resolution because no Android SDK location is configured.

### CI Verified

Not verified. No CI run was available or executed.

## Recommended Next Action

Developer fixes both `BLOCKING_IMPLEMENTATION` findings, adds the bearing-fallback and offline-open-failure coverage, and requests the next review iteration.
