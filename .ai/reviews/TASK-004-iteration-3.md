# TASK-004 — Review iteration 3

## Result

CHANGES_REQUESTED

## Task

Reviewed against [TASK-004](../tasks/TASK-004.md).

## Design

Reviewed against the [TASK-004 design specification](../designs/TASK-004.md).

## Blocking Findings

1. **classification:** BLOCKING_IMPLEMENTATION  
   **file:** `app/src/main/java/kniezrec/com/flightinfo/flight/FlightParametersController.kt` and `app/src/main/java/kniezrec/com/flightinfo/course/CourseController.kt`  
   **location:** `FlightParametersController.start`/`stop`/`onLocation`, lines 25–48; `CourseController.onGpsBearing`, lines 51–54  
   **problem:** A location callback registered in an old foreground session is passed directly to `onLocation` with no active-registration/session guard. If it is queued while paused or before a restart, it can arrive after a new session has received a compass heading; `CourseController.onGpsBearing` then accepts that old callback and displays its bearing as current-session data.  
   **why it violates the task/design:** TASK-004 requires GPS bearing to come only from the current foreground location-observation session and requires every pause/restart/retry/reset to clear values before a new current-session callback. The existing orientation token guard does not protect the shared location path.  
   **required correction:** Invalidate and gate location callbacks by the active foreground registration/session before invoking either flight-parameter or course callbacks. Add a focused regression test that delivers an old location callback after stop/restart and verifies it cannot populate GPS bearing (or flight readings).

2. **classification:** BLOCKING_IMPLEMENTATION  
   **file:** `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/CourseCard.kt`  
   **location:** `CourseReading`, lines 88–97  
   **problem:** The available-state primary value and fixed 72 dp direction visual are always laid out in one `Row`. There is no width/font-scale condition or stacked layout, so narrow widths or 200% text can constrain the weighted degree/cardinal group rather than switching to the required readable vertical layout.  
   **why it violates the task/design:** The design explicitly requires a stacked layout when the row's minimum readable widths do not fit, including at 200% font scale, with no clipping, overlap, or truncation.  
   **required correction:** Choose the row only when the available width and font scale support both elements; otherwise stack the heading/cardinal, visual, and GPS row. Add focused Compose coverage for the narrow/large-text layout.

3. **classification:** BLOCKING_IMPLEMENTATION  
   **file:** `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/CourseCard.kt`  
   **location:** `StaticCourse` and `CourseReading`, lines 66–99  
   **problem:** The Course state texts and the waiting-to-available transition have no polite live-region semantics. The card therefore never exposes the required one-time polite state/availability announcement; adding a live region to the rapidly updated heading itself would instead announce every sensor update.  
   **why it violates the task/design:** TASK-004 requires state changes to be exposed politely without announcing every sensor update, and the design specifically calls for a one-time polite "Compass heading available" transition plus polite unavailable/error changes.  
   **required correction:** Add resource-backed polite announcements for static state changes and exactly the transition to available (without making the continuously changing heading/bearing live). Add focused semantics coverage for this behavior.

4. **classification:** BLOCKING_IMPLEMENTATION  
   **file:** `app/src/main/java/kniezrec/com/flightinfo/course/CourseState.kt` and `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/CourseCard.kt`  
   **location:** `compassCardinal`, lines 15–23; `CourseReading`, lines 82 and 91  
   **problem:** The visible and spoken cardinal values (`N`, `NE`, and so on) are hard-coded Kotlin strings, then rendered directly and interpolated into accessibility text.  
   **why it violates the task/design:** The task requires all user-visible and accessibility text to be Android resources. This prevents resource-based localization/presentation of the cardinal values and their spoken equivalents.  
   **required correction:** Keep the pure mapping framework-independent (for example, map to a direction enum), but resolve its displayed and spoken cardinal strings through Android resources in the presentation layer. Extend the focused UI/semantics tests accordingly.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected TASK-004, its design specification, iteration-2 review, the current branch history/diff, course state/controller/platform, shared location controller/platform, activity lifecycle coordination, dashboard and Course composables, resources, and focused unit/instrumentation tests. Confirmed the prior iteration's discarded Course-card modifier and stale orientation-callback findings are fixed: the caller modifier is applied and orientation callbacks are session-token gated.

### Tests Verified

Attempted `./gradlew testDebugUnitTest --console=plain --no-daemon` twice. Gradle launched its daemon but did not return a completed task result through the available local command session, so no unit-test result was verified. Instrumentation tests were not run.

### Build Verified

No build was completed or verified.

### CI Verified

Not verified.

## Recommended Next Action

Developer fixes the four BLOCKING_IMPLEMENTATION findings and adds the requested regression/Compose semantics and responsive-layout coverage, then reruns the relevant local and instrumentation checks where available.
