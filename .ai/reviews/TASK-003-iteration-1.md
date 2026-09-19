# TASK-003 — Review iteration 1

## Result

CHANGES_REQUESTED

## Task

Reviewed against [TASK-003](../tasks/TASK-003.md).

## Design

Reviewed against the [TASK-003 design specification](../designs/TASK-003.md).

## Blocking Findings

1. **classification:** BLOCKING_IMPLEMENTATION  
   **file:** `app/src/main/java/kniezrec/com/flightinfo/flight/FlightParametersController.kt`  
   **location:** `onLocation`, lines 44–53  
   **problem:** Every location callback publishes `FlightParametersState.Readings`, including a first callback where speed and altitude are both absent or non-finite. That makes the UI render the three row layout containing only unavailable placeholders.  
   **why it violates the task/design:** The task requires the card to remain `Waiting for GPS position…` until a valid current-session fix is available, and the design specifically requires the readings layout only after a current-session fix supplies at least one displayable speed or altitude field.  
   **required correction:** Retain waiting state for an initial callback with no valid speed and no valid altitude. Preserve the existing partial-row behavior once this observation session has received at least one displayable field, and add focused coverage for the all-fields-missing initial callback.

2. **classification:** BLOCKING_IMPLEMENTATION  
   **file:** `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/FlightParametersCard.kt`  
   **location:** `FlightParametersTitle`, lines 80–86  
   **problem:** The only polite live region is the title, whose text is identical in both waiting and readings states. Consequently, the transition to readings has no changed live-region text to announce; the implementation does not provide the required one-time polite availability announcement.  
   **why it violates the task/design:** The design requires that the waiting-to-readings transition be politely announced (for example, `Flight parameters available`) without announcing every routine numeric refresh.  
   **required correction:** Add a resource-backed one-time availability announcement triggered only when the card first changes from waiting to readings in an observation session, and retain non-live routine value updates.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the task and design specifications; the feature-branch diff; activity permission/lifecycle coordination; GNSS and flight controller/platform boundaries; Compose dashboard and flight-card semantics/layout; strings; unit tests; instrumented Compose tests; manifest; Gradle configuration; and configured GitHub Actions workflow. `git diff --check` reported no whitespace errors.

### Tests Verified

Attempted `./gradlew testDebugUnitTest ktlintCheck`. It did not complete with a result during this review environment's Gradle task-graph setup, so no test success is claimed. Source inspection confirms focused controller and Compose test additions exist, but they do not cover the initial callback with neither field valid or the availability announcement.

### Build Verified

Attempted a time-bounded `./gradlew :app:compileDebugKotlin --console=plain --no-daemon`; it did not complete within 60 seconds while Gradle was setting up. No build result is claimed.

### CI Verified

CI was inspected only. `.github/workflows/build.yml` runs `ktlintCheck`, `testDebugUnitTest`, and `assembleDebug`; no CI run was executed or verified.

## Recommended Next Action

Developer fixes the two BLOCKING_IMPLEMENTATION findings and adds focused test coverage, then submits the feature branch for another review.
