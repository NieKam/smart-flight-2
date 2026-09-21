# TASK-007 — Review iteration 1

## Result

CHANGES_REQUESTED

## Task

Reviewed against [`.ai/tasks/TASK-007.md`](../tasks/TASK-007.md), including the offline archive, foreground-session, map-state, accessibility, responsive-layout, and testing requirements.

## Design

Reviewed against [`.ai/designs/TASK-007.md`](../designs/TASK-007.md), including the card hierarchy, bounded normal/expanded presentation, native-map interop boundary, failure states, and interaction/accessibility requirements.

## Blocking Findings

### 1. Expanded mode does not change the map presentation

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/MapCard.kt`
- location: `MapCard`, the `BoxWithConstraints` modifier around lines 87–90
- problem: The normal and expanded branches only change the `max` constraint of `heightIn`; neither branch supplies a height or aspect-ratio constraint. The map content uses `fillMaxSize()` and has no intrinsic height, so both states resolve to the same minimum-height card in normal use. The `expanded` argument passed to `OfflineMap` is also unused.
- why it violates the task/design: TASK-007 requires the expand/collapse control to change the map card between normal and expanded presentations. The design specifies materially different bounded normal and expanded content regions (approximately 16:9 versus 4:3), while remaining in the outer dashboard scroll.
- required correction: Give normal and expanded states distinct, responsive bounded heights/aspect-ratio constraints (with the specified minimum and available-height safeguards), and verify that the control changes the measured presentation without introducing nested scrolling.

### 2. Native map creation can crash instead of showing the required unavailable state

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/MapCard.kt`
- location: `OfflineMap` `AndroidView` factory, approximately lines 199–210
- problem: `OfflineTileProvider(arrayOf(ZipFileArchive(archive)))`, `setTileSource`, and `MapView` creation are executed without a failure boundary. `MapArchiveRepository` only checks that the file is a non-empty ZIP with at least one entry; an unreadable/unsupported archive can pass that check and then throw while the provider or map is opened.
- why it violates the task/design: The task requires missing, corrupt, or failed-to-open/read archives to leave the dashboard usable and show an explicit unavailable state with retry. An exception from the AndroidView factory can terminate composition instead of transitioning to `MapCardState.Unavailable`.
- required correction: Validate/open the archive at the actual osmdroid boundary and propagate failures into guarded unavailable state. Ensure retry invalidates the old attempt and does not create duplicate map instances or listeners.

### 3. MapView and marker ownership is global rather than scoped to the active card/session

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/MapCard.kt`
- location: top-level `activeMap`/`activeMarker` variables and `OfflineMap` disposal/update code, approximately lines 158–182 and 213–230
- problem: The map and marker are stored in process-global mutable variables. Any `OfflineMap` instance writes those variables, and disposal unconditionally clears and detaches whichever instance is currently global. This makes a prior composition/session able to detach a newer map, and makes the recenter action operate on mutable global state rather than the card/session that owns the control.
- why it violates the task/design: The task requires one controlled native map per active card/session, explicit cleanup on lifecycle transitions, and stale work/session isolation. The design requires the native-map interop boundary to keep map objects outside domain state but scoped to the active composition; global ownership cannot safely distinguish old, retried, recreated, or concurrently disposed instances.
- required correction: Scope the `MapView` and marker to the active `AndroidView`/session instance, guard disposal by identity/session token, and route recentering through that instance. Verify that teardown of an old map cannot detach or clear a newer map.

### 4. Required focused coverage for archive failure/retry and Compose controls is absent

- classification: `BLOCKING_IMPLEMENTATION`
- file: `app/src/test/java/kniezrec/com/flightinfo/map/MapStateTest.kt` and `app/src/androidTest/java/kniezrec/com/flightinfo/ui/gnss/`
- location: TASK-007 map test coverage
- problem: The only new tests cover coordinate validity, first-fix bookkeeping, recenter values, course normalization, and reset. There are no tests for atomic archive-copy failure/retry or stale-attempt suppression, and no Compose/instrumentation tests for map card placement, unavailable/loading semantics, recenter/expand labels, enabled states, or expand/collapse behavior.
- why it violates the task/design: The acceptance criteria explicitly require focused coverage for archive-copy failure/retry and Compose control semantics where the configured test environment supports them. Those are the failure and accessibility paths most likely to regress and are not covered by the submitted tests.
- required correction: Add repository tests using injectable I/O/execution or an equivalent deterministic seam for partial/corrupt copy and retry/session invalidation, plus Compose tests for card placement and control semantics/expanded presentation in the configured Android test environment.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the TASK-007 Architect task and Designer specification, the complete Developer commit diff, relevant existing dashboard/lifecycle/location architecture, manifest and Gradle configuration, map resources, and all new map tests. Confirmed the packaged archive SHA-256 matches the task-recorded legacy archive: `d69fb06c06ae3d66fc95f51365ef350a310aed3f9092b9684bffeeb0ecfcb8ff`. `git diff --check` passed.

### Tests Verified

Not executed. The configured Gradle invocation (`./gradlew testDebugUnitTest ktlintCheck`) could not determine dependencies because no Android SDK is installed/configured (`local.properties` and `ANDROID_HOME` are absent).

### Build Verified

Not verified. The local environment has no configured Android SDK, so Gradle stopped during task dependency resolution before compilation.

### CI Verified

Not verified. No CI run was available or executed.

## Recommended Next Action

Developer fixes all `BLOCKING_IMPLEMENTATION` findings, adds the required focused coverage, and requests the next review iteration. Build and tests should be run in an Android SDK/device environment when available.
