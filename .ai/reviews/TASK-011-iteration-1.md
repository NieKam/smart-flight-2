# TASK-011 — Review iteration 1

## Result

CHANGES_REQUESTED

## Task

Reviewed against [.ai/tasks/TASK-011.md](/home/ai-dev/smart-flight-2/.ai/tasks/TASK-011.md).

## Design

Reviewed against [.ai/designs/TASK-011.md](/home/ai-dev/smart-flight-2/.ai/designs/TASK-011.md).

## Blocking Findings

### 1. Compact dashboard header can overlap the title

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt`
- location: lines 112–127
- problem: The app name is centered in a fixed 56dp header while Settings and About are always placed in a trailing `Row`. There is no width constraint, wrapping, or compact overflow behavior. On narrow/compact windows the action row occupies the same horizontal area as the centered title, so the title and labeled actions can overlap or become obscured.
- why it violates the task/design: TASK-011 requires the title not to collide with actions. The design explicitly requires wrapping or a labeled menu fallback at compact widths while keeping About discoverable and labeled.
- required correction: Make the header responsive: reserve space for the title and action group, wrap the labeled actions, or switch to a labeled Material 3 overflow/menu affordance at compact widths. Verify that both actions remain accessible and the title does not overlap them.

### 2. Required focused behavior coverage is missing

- classification: BLOCKING_IMPLEMENTATION
- file: `app/src/test/java/kniezrec/com/flightinfo/about/AboutPlatformTest.kt`
- location: lines 7–31; no corresponding About Compose/instrumentation test exists
- problem: The change tests only pure version formatting and URI construction. It does not test package-metadata fallback through `AndroidAppVersionProvider`, handler/launch failure behavior of `AndroidExternalIntentLauncher`, authorized-dashboard About visibility, dialog dismissal/back behavior, or key About content/accessibility behavior.
- why it violates the task/design: TASK-011 acceptance criteria explicitly require focused tests for version metadata fallback, intent handler failure, About visibility and dismissal, and key content/accessibility where the configured Compose environment supports them. These are the new behavior’s primary correctness and regression risks.
- required correction: Add focused unit tests for provider metadata/API fallback and launcher resolution/start failure, plus Compose/instrumentation coverage for the authorized-only About entry, opening/dismissal (including back), and key labels/content/semantics in the configured test environment. Keep the existing project checks covered.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

- Read TASK-011 and its design specification.
- Reviewed the Developer commit `4d67bb7` and its diff from `main`.
- Inspected the About platform adapter, Compose dialog, dashboard header, MainActivity authorized/onboarding branches, resources, existing Gradle/test conventions, and the new About unit test.
- Ran `git diff --check`; no whitespace errors were reported.

### Tests Verified

The configured `./gradlew test ktlintCheck` command was attempted, but Gradle failed during task configuration before tests or ktlint executed because no Android SDK location is available (`ANDROID_HOME`/`sdk.dir` is missing). Therefore no tests were actually executed or verified.

### Build Verified

No build was verified. The Gradle invocation could not configure the Android test task because the local Android SDK is unavailable.

### CI Verified

CI was not run or otherwise verified.

## Recommended Next Action

Developer fixes the two BLOCKING_IMPLEMENTATION findings, then the workflow may perform another review iteration. The project must also be rechecked in an Android SDK environment.
