# TASK-010 — Review iteration 2

## Result

CHANGES_REQUESTED

## Task

Reviewed against the Architect task [`.ai/tasks/TASK-010.md`](../tasks/TASK-010.md).

## Design

Reviewed against the Designer specification [`.ai/designs/TASK-010.md`](../designs/TASK-010.md).

## Blocking Findings

### 1. Nearby-city accessibility description is still assembled outside resources

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/NearbyCityCard.kt:176-180`
- location: `Row` semantics in the private nearby-city row composable
- problem: The row content description is still built with Kotlin string concatenation (`"$name, $spoken"`). The distance row now supplies expanded unit wording, but the final accessibility description does not use the resource-backed description template.
- why it violates the task/design: TASK-010 and the design require labels, values, units, and accessibility descriptions to be resource-backed and localized. This path is used by the changed nearby-city distance presentation, so the implementation still does not satisfy the accessibility/localization requirement.
- required correction: Format the row description through a resource-backed string such as the existing `card_row_description`, preserving the expanded spoken distance unit.

### 2. Settings content is not centered within the specified maximum width

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/main/java/kniezrec/com/flightinfo/ui/settings/UnitSettingsScreen.kt:72-79`
- location: The settings content `Column` using `widthIn(max = 600.dp)`
- problem: The column is constrained to 600 dp but is laid out from the logical start. There is no parent alignment or centered arrangement for wider windows.
- why it violates the task/design: The design explicitly requires a single settings column with a 600 dp maximum readable width, centered on wider windows, including landscape/wide-window behavior. The current implementation leaves the content pinned to the start edge on a wide display.
- required correction: Center the constrained settings content in its available width while retaining logical start/end placement inside the column and the existing scroll behavior.

### 3. Required behavior coverage remains incomplete

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/androidTest/java/kniezrec/com/flightinfo/ui/settings/UnitSettingsScreenTest.kt:24-48`; missing dashboard/presentation UI tests
- location: Added Compose and preference tests
- problem: The new tests cover metric summaries/back semantics, one distance selection, malformed/default store values, and a second store read. They do not cover the authorized dashboard Settings entry and return path, all five selectors and exact options, persistence through the activity/process-facing integration, or immediate updates of already-rendered flight, nearby-city, and route values while preserving arrival/duration.
- why it violates the task/design: The acceptance criteria and implementation checklist explicitly require focused persistence/default, conversion/formatting, and Compose UI coverage for the settings flow and changed dashboard presentations. The feature’s central integration behavior remains unverified, and only one of the five selector interactions is exercised.
- required correction: Add focused tests for all selector option sets and selection semantics, authorized dashboard entry/back behavior, and recomposition of existing flight/nearby/route values after preference changes (including unchanged route arrival/duration). Keep the existing persistence/default and conversion tests, extending them as needed for the stated edge cases.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the Architect task, Designer specification, previous TASK-010 review, current `feat/TASK-010` implementation and latest commit diff, unit preference model/store, conversion/formatting helpers, `MainActivity` and dashboard wiring, settings screen, flight/nearby/route cards, resources, and existing/new tests. Confirmed the previous review’s back-arrow and scrollable-dialog findings are addressed in source.

### Tests Verified

Attempted `./gradlew testDebugUnitTest ktlintCheck`. No tests or lint checks executed because Gradle could not locate an Android SDK (`ANDROID_HOME`/`local.properties` is not configured).

### Build Verified

No build was successfully executed. The available Gradle verification failed during configuration because the Android SDK location is unavailable.

### CI Verified

CI was not verified.

## Recommended Next Action

Developer fixes the three BLOCKING_IMPLEMENTATION findings and requests the next review iteration. Repeat build and test verification in an environment with a configured Android SDK.
