# TASK-005 — Review iteration 2

## Result

CHANGES_REQUESTED

## Task

Reviewed against [TASK-005](../tasks/TASK-005.md).

## Design

Reviewed against [TASK-005 design specification](../designs/TASK-005.md).

## Blocking Findings

1. **classification:** BLOCKING_IMPLEMENTATION  
   **file:** `app/src/test/java/kniezrec/com/flightinfo/orientation/OrientationPlatformAdaptersTest.kt` (and the display-rotation mapping in `app/src/main/java/kniezrec/com/flightinfo/orientation/OrientationSource.kt`)  
   **location:** `OrientationPlatformAdaptersTest`, lines 7-55; `AndroidOrientationSource.registerSensorListener`, lines 101-109  
   **problem:** The submitted tests still do not exercise the display-relative orientation transform for portrait, reverse portrait, landscape, and reverse landscape. The new test checks `OrientationEventDispatcher` generation isolation, and the Horizon-controller test checks that a replacement session re-establishes its reference, but neither feeds controlled samples through or otherwise verifies the four rotation mappings.  
   **why it violates the task/design:** TASK-005 explicitly requires focused automated coverage of display-relative orientation behavior across all four display rotations. This is central to the feature: the production mapping is Android-platform code and an inverted/remapped axis would produce a visibly and semantically incorrect Horizon result despite the existing controller tests passing.  
   **required correction:** Extract or inject the display-rotation orientation calculation behind a small testable boundary as needed, then add controlled tests for rotations 0, 90, 180, and 270. Verify the resulting display-relative pitch and roll direction/value (and preserve heading behavior) for each rotation, in addition to the existing reset/re-reference test.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Independently reviewed the Architect task, Designer specification, prior iteration artifact, and the current `feat/TASK-005` implementation through commit `363556f`. Inspected `MainActivity`, the shared orientation source and adapters, Horizon state/controller/mapping, Horizon Compose card, dashboard placement, resource strings, manifest, existing Course coordination, focused unit tests, focused Compose tests, and CI workflow.

Confirmed that the iteration-1 stale-registration correction now captures listener snapshots with a registration generation, and that the replacement-session test covers stale callback rejection. Confirmed resource-backed transition announcements and the dedicated recalibrating state are present. The required all-four-rotations mapping coverage remains absent.

### Tests Verified

Attempted `./gradlew testDebugUnitTest`. The local invocation did not produce a completed Gradle result or any unit-test report files, so no tests are verified as passed.

### Build Verified

No build was executed successfully.

### CI Verified

No CI run was executed. Reviewed `.github/workflows/build.yml`; it runs `ktlintCheck`, `testDebugUnitTest`, and `assembleDebug` on GitHub Actions.

## Recommended Next Action

Developer adds controlled, focused tests for display-relative pitch/roll across all four display rotations, then resubmits for review.
