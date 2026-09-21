# TASK-006 — Review iteration 1

## Result

CHANGES_REQUESTED

## Task

Architect task: `.ai/tasks/TASK-006.md`.

## Design

Designer specification: `.ai/designs/TASK-006.md`.

## Blocking Findings

1. **classification:** BLOCKING_IMPLEMENTATION  
   **file:** `app/src/main/java/kniezrec/com/flightinfo/nearby/NearbyCityController.kt`  
   **location:** lines 80–112  
   **problem:** Every accepted GPS fix is submitted to the single-thread executor. Obsolete requests are only discarded after they have completed, so frequent fixes combined with a slow database scan create an unbounded queue and make the newest lookup wait behind all older scans.  
   **why it violates the task/design:** TASK-006 requires coalescing or cancellation so only the newest query matters and work does not accumulate unboundedly; the design likewise requires newest-fix-safe lookup without stale work delaying it.  
   **required correction:** Coalesce pending lookup requests or cancel/remove superseded work (including retry replacement) so at most the active/newest lookup is queued, while retaining the existing session/fix invalidation check.

2. **classification:** BLOCKING_IMPLEMENTATION  
   **file:** `app/src/main/java/kniezrec/com/flightinfo/nearby/NearbyCityController.kt`  
   **location:** lines 115–120  
   **problem:** The UTC offset is constructed as a raw English-style string (for example, `UTC+02:00`) and then reused as the spoken offset. It does not provide the required localized, accessible offset wording.  
   **why it violates the task/design:** The task requires an accessible, localized UTC offset; the design explicitly requires resource-backed visible/spoken offset templates with localized plus/minus and hour/minute wording rather than punctuation/symbol pronunciation.  
   **required correction:** Represent the offset in presentation data suitable for locale/resource formatting, and use resource-backed visible and spoken templates that provide a localized accessible UTC offset.

3. **classification:** BLOCKING_IMPLEMENTATION  
   **file:** `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/NearbyCityCard.kt`  
   **location:** lines 39–40  
   **problem:** The retry button replaces its child text semantics with only the retry hint via `contentDescription`; TalkBack therefore loses the visible `Try again` action label. It also has no visible keyboard/switch focus outline.  
   **why it violates the task/design:** The design requires a visible label, button semantics, resource-backed retry hint, and a cyan focus outline. The hint must supplement the action label, not replace it.  
   **required correction:** Preserve the resource-backed `Try again` label in the button's accessible semantics, expose the hint as supplemental semantics (as the established GNSS action does), and add the specified visible focus treatment while keeping the 48 dp target.

4. **classification:** BLOCKING_IMPLEMENTATION  
   **file:** `app/src/test/java/kniezrec/com/flightinfo/nearby/NearbyCityControllerTest.kt`  
   **location:** entire test class; no TASK-006 nearby-city Compose/instrumentation test is present  
   **problem:** The three controller tests do not cover required metric one-decimal formatting, local-time/offset formatting including invalid zones, repository malformed/empty data failures, retry behavior, newer-result-wins behavior, or Compose state/content/retry semantics.  
   **why it violates the task/design:** The acceptance criteria explicitly require focused automated coverage for those behaviors and Compose semantics.  
   **required correction:** Add focused unit and Compose/instrumentation tests for all listed acceptance coverage, including retries and stale-result invalidation; retain existing tests as applicable.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Reviewed the TASK-006 task and design, feature-branch diff, lifecycle/location fan-out, nearby repository/controller/card/resources, existing dashboard architecture, legacy-asset checksum, and current nearby-city tests. The copied `cities_info.db` exactly matches the available legacy asset (SHA-256 `abb27f8ae2feac1d467c38318392e7c353e680ce0b2f94c3f02e5c54c8e3cc70`), and its `cities_info` schema contains the queried columns.

### Tests Verified

Attempted `./gradlew testDebugUnitTest`. It did not execute tests because no Android SDK is configured (`SDK location not found`; no `ANDROID_HOME` or `local.properties` SDK path). No test result is claimed.

### Build Verified

No build completed; Gradle stopped during task dependency configuration for the missing Android SDK.

### CI Verified

Not verified locally.

## Recommended Next Action

Developer fixes the four BLOCKING_IMPLEMENTATION findings, adds the required focused tests, and reruns available checks in an Android SDK-configured environment.
