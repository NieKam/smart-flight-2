# TASK-003 — Review iteration 2

## Result

CHANGES_REQUESTED

## Task

Reviewed against [TASK-003](../tasks/TASK-003.md).

## Design

Reviewed against the [TASK-003 design specification](../designs/TASK-003.md).

## Blocking Findings

1. **classification:** BLOCKING_IMPLEMENTATION  
   **file:** `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt`  
   **location:** imports at lines 12–15; uses at lines 63 and 93 (and further `heightIn` calls)  
   **problem:** The feature change removed the `androidx.compose.foundation.layout.heightIn` import, while the file continues to call `heightIn`. It also imports `widthIn` twice. `heightIn` is therefore an unresolved reference and the Kotlin source cannot compile.  
   **why it violates the task/design:** TASK-003 requires the existing GNSS dashboard to be extended while preserving its behavior; an uncompilable screen prevents the required dashboard and flight-parameters feature from being delivered.  
   **required correction:** Restore the `heightIn` import and remove the duplicate `widthIn` import, then run the configured checks in an environment with an Android SDK.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Inspected the Architect task, Designer specification, previous iteration review, current feature-branch commits and diff, activity permission/lifecycle coordination, GNSS and flight observer/controller boundaries, Android location adapter, Compose dashboard and accessibility semantics, resources, manifest, focused unit and Compose tests, and CI workflow. Confirmed the two iteration-1 findings are addressed: an initial no-field callback remains waiting, and a resource-backed polite availability announcement plus Compose coverage was added. `git diff --check` completed with no whitespace errors.

### Tests Verified

Attempted `./gradlew testDebugUnitTest ktlintCheck --console=plain --no-daemon`. Gradle could not determine the test task dependencies because no Android SDK location is configured (`ANDROID_HOME` is absent and `local.properties` has no `sdk.dir`). No tests or formatting checks executed successfully.

### Build Verified

No build completed. The same missing Android SDK configuration blocks Gradle before compilation; source review identified the unresolved `heightIn` reference independently.

### CI Verified

CI configuration was inspected only. `.github/workflows/build.yml` runs `ktlintCheck`, `testDebugUnitTest`, and `assembleDebug`; no CI run was executed or verified.

## Recommended Next Action

Developer fixes the blocking import regression, runs the configured checks where an Android SDK is available, and submits the feature branch for review.
