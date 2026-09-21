# TASK-006 — Review iteration 2

## Result

CHANGES_REQUESTED

## Task

Architect task: `.ai/tasks/TASK-006.md`.

## Design

Designer specification: `.ai/designs/TASK-006.md`.

## Blocking Findings

1. **classification:** BLOCKING_IMPLEMENTATION  
   **file:** `app/src/main/java/kniezrec/com/flightinfo/nearby/AndroidNearbyCityRepository.kt`  
   **location:** line 37  
   **problem:** `copyAsset()` calls `AssetManager.openFd()` to obtain the database length. `openFd()` is available only for an uncompressed packaged asset, but the project has no asset-packaging `noCompress` configuration for `cities_info.db`. Android packaging may compress the `.db` file, causing this call to throw before the subsequent stream copy and making the card unavailable on affected builds.  
   **why it violates the task/design:** The feature must reliably copy/open the bundled legacy database and return a nearby city from it offline. A normal packaged database asset can therefore fail every lookup rather than load.  
   **required correction:** Do not depend on `openFd()` for the asset length. Copy/read the asset through `context.assets.open()` (with a safe integrity/replacement strategy), or explicitly ensure the asset is packaged uncompressed while preserving the required safe copy/retry behavior.

## Non-Blocking Findings

None.

## Specification / Design Conflicts

None.

## Verification Performed

### Source Review

Read the TASK-006 architect task, TASK-006 designer specification, and iteration-1 review artifact. Reviewed the current `feat/TASK-006` implementation, including the foreground lifecycle and shared location-fix fan-out, nearby-city controller/coalescing/session invalidation, SQLite repository, Compose card/resources, and focused unit and Compose tests. Confirmed the iteration-1 findings for coalescing, offset presentation, retry semantics/focus treatment, and focused coverage were addressed in the current implementation.

### Tests Verified

Attempted `./gradlew testDebugUnitTest`. Gradle did not execute tests because this checkout has no Android SDK configured (`SDK location not found`; no `ANDROID_HOME` or `local.properties` SDK path).

### Build Verified

No build completed. Gradle stopped during task dependency configuration because the Android SDK is not configured locally.

### CI Verified

Not verified locally.

## Recommended Next Action

Developer fixes the blocking bundled-asset loading path, then reruns relevant unit and Android/Compose checks in an Android SDK-configured environment.
