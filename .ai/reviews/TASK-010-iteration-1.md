# TASK-010 — Review iteration 1

## Result

CHANGES_REQUESTED

## Task

Reviewed against the Architect task [`.ai/tasks/TASK-010.md`](../tasks/TASK-010.md).

## Design

Reviewed against the Designer specification [`.ai/designs/TASK-010.md`](../designs/TASK-010.md).

## Blocking Findings

### 1. Accessibility presentation is not expanded or fully resource-backed

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/FlightParametersCard.kt:180-188`; `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/NearbyCityCard.kt:95-99`; `app/src/main/java/kniezrec/com/flightinfo/ui/route/RouteCard.kt:152`
- problem: Accessibility values reuse compact abbreviations such as `mbar`, `inHg`, `km`, and `mi`, and the nearby-city/route presentation does not provide expanded spoken unit wording. Route distance also constructs the visible unit with hardcoded `"mi"`/`"km"` rather than a resource. The selector-row content description is assembled by string concatenation (`"$label, $value"`) rather than a resource-backed accessibility string.
- why it violates the task/design: TASK-010 and the design require expanded accessible wording (for example, miles and millibars) and require labels, visible values, units, and accessibility descriptions to be resource-backed. The current implementation therefore does not satisfy the accessibility/localization acceptance criteria for the changed flight, nearby-city, and route values.
- required correction: Add resource-backed expanded unit/accessibility strings and use them for all spoken value descriptions, while retaining compact resource-backed units for visible values. Replace hardcoded route units and concatenated selector semantics with formatted resource strings.

### 2. Settings back control does not implement the specified top-app-bar back affordance

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/main/java/kniezrec/com/flightinfo/ui/settings/UnitSettingsScreen.kt:61-65`
- problem: The settings `TopAppBar` uses a text `TextButton` labeled “Back” as its navigation icon instead of a leading back-arrow navigation control.
- why it violates the task/design: The design explicitly requires a leading back arrow with a minimum 48 dp target and Navigate up semantics. A text button is a different top-app-bar control and does not provide the specified standard navigation affordance/semantics.
- required correction: Use a standard leading back/up icon control with a 48 dp target and localized Navigate up content description, wired to the existing `onBack` callback.

### 3. Selector dialog content is not resilient to large text or constrained windows

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/main/java/kniezrec/com/flightinfo/ui/settings/UnitSettingsScreen.kt:115-129`
- problem: The `AlertDialog` option content is a plain non-scrollable `Column`, and the option text is not given a constrained/weighted layout that can reliably wrap alongside the radio control. At large font scales or a short-height landscape window, the options can exceed the dialog viewport instead of scrolling.
- why it violates the task/design: TASK-010 requires settings to remain usable at large font scales in portrait and landscape, and the design specifically requires dialog options to scroll when needed rather than being compressed or clipped.
- required correction: Make the dialog option area vertically scrollable with appropriate size constraints and allow option labels to wrap while preserving at least 48 dp touch targets.

### 4. Required persistence and Compose UI coverage was not added

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/test/java/kniezrec/com/flightinfo/displayunits/UnitPresentationTest.kt:7-23`; missing tests for `UnitPreferencesStore` and `UnitSettingsScreen`
- problem: The only new test coverage checks a subset of numeric conversions, sign formatting, and non-finite input. There are no tests for defaults/malformed independent preference fallback, persistence across reads/writes, selector choices, settings entry/back behavior, accessibility/current summaries, or immediate Compose updates to dashboard values.
- why it violates the task/design: The acceptance criteria explicitly require focused conversion, formatting, persistence/default, and Compose UI coverage. The implementation’s most important new behavior is consequently unverified, and existing tests do not cover the settings screen or changed unit rendering.
- required correction: Add focused unit tests for all conversion/formatting and preference-store cases, plus Compose/UI coverage for authorized settings entry, all five selectors/options/default summaries, selection/dismissal/back behavior, accessibility semantics, and presentation updates for flight/nearby/route values.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the TASK-010 Architect task, TASK-010 Designer specification, current `feat/TASK-010` implementation, changed-file diff, existing dashboard/card/route architecture, resource strings, and available unit/instrumentation tests. No prior TASK-010 review artifact exists for this first iteration.

### Tests Verified

Attempted `./gradlew testDebugUnitTest ktlintCheck`. It did not execute tests or lint because Gradle could not locate an Android SDK (`ANDROID_HOME`/`local.properties` is not configured).

### Build Verified

No build was successfully executed. The same Gradle invocation failed during dependency/task configuration because the Android SDK location is unavailable.

### CI Verified

CI was not verified.

## Recommended Next Action

Developer fixes the four BLOCKING_IMPLEMENTATION findings, adds the required tests, and requests the next review iteration. Build/test verification should be repeated in an environment with a configured Android SDK.
