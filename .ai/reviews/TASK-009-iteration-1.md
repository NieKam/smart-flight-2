# TASK-009 — Review iteration 1

## Result

CHANGES_REQUESTED

## Task

Reviewed against [`.ai/tasks/TASK-009.md`](../tasks/TASK-009.md), the Architect task for adding optional live barometric pressure to the existing Flight parameters card.

## Design

Reviewed against [`.ai/designs/TASK-009.md`](../designs/TASK-009.md), the Designer specification for pressure-row presentation, accessibility, lifecycle, and responsive behavior.

## Blocking Findings

### Finding 1

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/test/java/kniezrec/com/flightinfo/flight/PressureControllerTest.kt`; `app/src/androidTest/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreenTest.kt`
- location: `PressureControllerTest`; the flight-parameters Compose tests
- problem: The required focused coverage is incomplete. The unit tests do not verify a registration exception, and the Compose/instrumentation tests do not verify pressure-row order/label, the unavailable pressure placeholder semantics, or the pressure row accessibility description (`Pressure, unavailable` and the expanded `millibars` unit). The existing UI test exercises only one valid formatted value and generic dash/value rendering.
- why it violates the task/design: TASK-009 acceptance criteria explicitly require focused tests for formatting, validation, unavailable hardware, session reset, stale callbacks, registration failure, and unregister behavior, plus Compose/instrumentation coverage for row order, content, placeholder, and accessibility where the configured environment supports it. The Designer specification likewise calls for verification of the four-row order, unavailable accessibility state, resource-backed semantics, and the expanded `millibars` announcement.
- required correction: Add focused unit coverage for registration exceptions and the complete session/reset/unregister failure paths as needed, and add Compose/instrumentation assertions for `Speed`, `Vertical speed`, `Altitude`, `Pressure` order, the pressure dash/unavailable content description, and a valid pressure accessibility description using `millibars`. Keep the tests scoped to the configured test environment.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the Architect task, Designer specification, current `feat/TASK-009` implementation, existing foreground lifecycle/coordinator, pressure platform/controller, flight-parameter state/controller, Compose card/screen, resources, and related tests. Verified by source inspection that the implementation uses `SensorManager`/`TYPE_PRESSURE`, filters non-finite and negative values, keeps pressure local to the row, uses session-token callback rejection, starts/stops with the authorized foreground lifecycle, and preserves the existing location observer path.

### Tests Verified

Attempted `./gradlew testDebugUnitTest`. It did not execute tests because the local environment has no configured Android SDK (`ANDROID_HOME`/`local.properties` SDK location is missing).

### Build Verified

No build was verified. Gradle configuration failed before compilation for the same missing Android SDK location.

### CI Verified

CI was not verified.

## Recommended Next Action

Developer fixes the BLOCKING_IMPLEMENTATION test-coverage finding and reruns the configured checks in an Android SDK environment. Workflow may proceed to another review iteration afterward.
