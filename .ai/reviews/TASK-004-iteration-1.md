# TASK-004 — Review iteration 1

## Result

CHANGES_REQUESTED

## Task

Reviewed against [TASK-004](../tasks/TASK-004.md).

## Design

Reviewed against the [TASK-004 design specification](../designs/TASK-004.md).

## Blocking Findings

1. **classification:** BLOCKING_IMPLEMENTATION  
   **file:** `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/CourseCard.kt`  
   **location:** `CourseReading`, lines 50–64  
   **problem:** The available Course card renders only the degree/cardinal pair and GPS-bearing row. It contains no plane/compass direction visual.  
   **why it violates the task/design:** The task requires the primary reading to be accompanied by a simple plane/compass direction visual which updates to the same normalized heading. The design also specifies the available-state visual and its rotation behavior.  
   **required correction:** Add the required decorative direction visual to the available state and update/rotate it atomically from the normalized heading, while keeping the text reading primary and accessible.

2. **classification:** BLOCKING_IMPLEMENTATION  
   **file:** `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/CourseCard.kt`  
   **location:** `CourseReading`, lines 53 and 57  
   **problem:** Degree values are assembled with hard-coded source-string characters instead of a resource-backed value template. The character in the source is a lone `0xB0` byte, which is rendered as `�` in the inspected text rather than a valid UTF-8 degree symbol.  
   **why it violates the task/design:** TASK-004 requires all degree/value formatting templates to be Android resources and requires the displayed numeric degree value to include a degree symbol. The current implementation can display an invalid replacement glyph and is not localizable through resources.  
   **required correction:** Add a resource-backed degree-value template and use it for both compass and GPS bearing after locale-aware integer formatting; ensure the resource/source encoding produces the actual degree symbol.

3. **classification:** BLOCKING_IMPLEMENTATION  
   **file:** `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/CourseCard.kt`  
   **location:** `StaticCourse`, line 46  
   **problem:** The compass-error `Try again` action lacks the specified resource-backed retry outcome hint and the established 2 dp cyan keyboard-focus outline.  
   **why it violates the task/design:** The design explicitly requires the existing cyan text-action treatment, including a 48 dp minimum target, keyboard-focus outline, and the spoken `Retries compass` hint. The GNSS action already establishes this project convention, but the Course action does not implement its accessibility semantics or focus styling.  
   **required correction:** Supply the required retry-hint resource and semantics, and apply the established focus-visible action treatment (including the 2 dp cyan outline and minimum touch target) to the Course retry control.

4. **classification:** BLOCKING_IMPLEMENTATION  
   **file:** `app/src/test/java/kniezrec/com/flightinfo/course/CourseControllerTest.kt` and `app/src/androidTest/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreenTest.kt`  
   **location:** entire new course test coverage / no Course-card cases  
   **problem:** The new unit test checks only selected cardinal boundaries and a small subset of lifecycle behavior. There are no Compose/instrumentation tests for Course waiting, available, unavailable, or error content, and no focused tests for all specified boundaries, retry cleanup, or lifecycle/session cleanup cases required by the task.  
   **why it violates the task/design:** TASK-004 acceptance criteria explicitly require focused automated coverage for normalization/cardinal boundaries, current-session reset semantics, absent/failed observation, bearing validity/normalization, lifecycle cleanup, and Compose waiting/available/unavailable/error content.  
   **required correction:** Add focused unit tests covering every specified boundary and the required registration/retry/session-cleanup behavior, plus Compose/instrumentation tests for all Course-card states and their key accessibility/content behavior.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected TASK-004 and its design, the TASK-004 implementation diff, `MainActivity`, the new course platform/controller/state/card, the shared flight-location integration, dashboard composition, strings, and the related unit/instrumentation tests. Also checked the implementation diff for whitespace errors.

### Tests Verified

Attempted `./gradlew testDebugUnitTest ktlintCheck` and `./gradlew testDebugUnitTest --console=plain --no-daemon`. Neither task completed or produced test-result XML during this review session, so no test pass result was verified.

### Build Verified

No successful build was completed or verified. Android instrumentation tests were not run.

### CI Verified

Not verified.

## Recommended Next Action

Developer fixes the four BLOCKING_IMPLEMENTATION findings, adds the required focused coverage, and reruns the relevant local checks where available.
