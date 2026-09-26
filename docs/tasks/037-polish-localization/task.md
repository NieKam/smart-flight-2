# TASK-037 — Polish localization and a CI check that every string is translated

## Goal
Restore the Polish translation for every screen and prevent regressions with a JVM test that fails when a default string lacks a Polish translation.

## Context
- Parity finding 12 (MISSING, MEDIUM): original `~/smart-flight/app/src/main/res/values-pl/strings.xml` and `values-pl/preference_strings.xml` translate every screen; the rewrite has no `res/values-*` directories. The rewrite has ~169 `<string>`/`<plurals>` entries in `app/src/main/res/values/strings.xml` (count before the parity tasks; more are added by TASK-019..034).
- The locale-dependent map-button glyph check (`MapCard.kt:188-194`) was removed in TASK-014, so translation no longer breaks the map buttons.
- This task runs last among UI tasks so all new strings exist.

## Dependencies
- TASK-019, TASK-020, TASK-021, TASK-022, TASK-023, TASK-024, TASK-025, TASK-027, TASK-028, TASK-029, TASK-031, TASK-032, TASK-033, TASK-034, TASK-036.

## Original app reference
- `res/values-pl/strings.xml`, `res/values-pl/preference_strings.xml`, `res/values/strings.xml`, `res/values/preference_strings.xml`.

## Scope
- `app/src/main/res/values-pl/strings.xml` with a translation for every translatable string and plural (Polish plural categories one/few/many/other).
- Reuse the original Polish wording wherever the meaning is the same (e.g. "Enable GPS", "Move device closer to the window", unit names, settings labels, "Hide this card?", max-zoom tip).
- Mark non-translatable strings (`translatable="false"`) e.g. the feedback e-mail address, unit symbols if intentionally identical.
- JVM test `TranslationCompletenessTest`: parses `values/strings.xml` and `values-pl/strings.xml` from the source tree (path relative to the module) and asserts every translatable name exists in Polish, format arguments match (`%1$s`, `%d` counts/types), and plurals define the Polish categories.

## Out of scope
- Other languages.

## Requirements
Required:
- 100% of translatable strings translated; format arguments consistent.
- Polish text fits in the UI (check long labels).

## Acceptance criteria
- [ ] `TranslationCompletenessTest` passes — verified by: CI unit test
- [ ] Robolectric Compose smoke test under `@Config(qualifiers = "pl")` renders the dashboard without missing-resource crashes — verified by: CI unit test
- [ ] Wording reviewed by a Polish speaker; no truncation on a phone — verified by: HUMAN on device

## Tests to add or update
- New: `TranslationCompletenessTest`, Polish smoke test.

## Risks and edge cases
- Plural categories: Polish needs `one`, `few`, `many` (and `other` for fractions); missing ones fall back to `other` silently — the test must check.
