# TASK-036 — Upgrade path from the original app: migrate settings and route; ship as an update (versionCode)

## Goal
Users upgrading from the Play Store version (2.4.9, versionCode 49) keep their units, display options, notification preference, saved route, hidden cards and zoom-tip count. The build uses a versionCode higher than 49 so it installs as an update.

## Context
- Parity finding 14 (REGRESSION, MEDIUM), verified:
  - Original stores everything in SharedPreferences file `LocalPrefs` (`~/smart-flight/app/src/main/java/kniezrec/com/flightinfo/settings/SettingsFragment.kt:18`, `settings/FlightAppPreferences.kt:24`).
  - Original keys (`common/Constants.kt:39-48`, `FlightAppPreferences.kt:15-19`, `res/xml/app_preferences_layout.xml`):
    - `SPEED_UNIT_PREFERENCE_KEY`, `ALTITUDE_UNIT_PREFERENCE_KEY`, `DISTANCE_UNIT_PREFERENCE_KEY`, `VERTICAL_SPEED_UNIT_PREFERENCE_KEY`, `PRESSURE_UNIT_PREFERENCE_KEY`: String "1"/"2"/"3" (default "1"), mapped by array order in `res/values/preference_strings.xml`: speed 1=km/h 2=mph 3=kt; altitude 1=m 2=ft; distance 1=km 2=mi; vertical speed 1=m/s 2=m/min 3=ft/min; pressure 1=mbar 2=inHg.
    - `NOTIFICATION_PREFERENCE_KEY` (Boolean, default true), `KEEP_SCREEN_PREFERENCE_KEY` (false), `FORCE_ZOOM_PREFERENCE_KEY` (false), `FORCE_PORTRAIT_MODE_PREFERENCE_KEY` (true).
    - `city_a_key`, `city_b_key`: `Int` city `_id` (departure, destination), `-1` = unset.
    - `is_course_card_hidden`, `is_horizon_card_hidden` (Boolean), `tip_shown_count` (Int).
  - Rewrite: new files `display_units`, `display_behavior`, `monitoring_behavior`, `route` (keys defined in TASK-006/012), plus `card_visibility` / tip keys from TASK-029/031. No migration exists (grep for `LocalPrefs`, `city_a_key`, `SPEED_UNIT_PREFERENCE_KEY` finds nothing).
  - `app/build.gradle.kts:17-18`: `versionCode = 1`, `versionName = "1.0"`, same `applicationId` `kniezrec.com.flightinfo`; original `app/build.gradle:23-27`: versionCode 49, versionName "2.4.9".
- Both apps ship `assets/databases/cities_info.db`; the original route IDs are `_id` values of that table (`db/CitiesTable.kt`), the rewrite reads the same column (`_id`).
- Planner assumption (confirm in README open questions): versionCode 50, versionName "3.0.0".

## Dependencies
- TASK-006, TASK-012, TASK-029, TASK-031.

## Original app reference
- `settings/SettingsFragment.kt`, `settings/FlightAppPreferences.kt`, `common/Constants.kt`, `res/xml/app_preferences_layout.xml`, `res/values/preference_strings.xml`, `db/CitiesTable.kt`, `app/build.gradle`.

## Scope
- `LegacyPreferencesMigration` (runs once at startup before repositories are first read — e.g. in the Application `onCreate` synchronously, it is a few key reads; or as the first step of each repository's initial load): if `LocalPrefs` exists and a marker `legacy_migration_done` (stored in a new small file or in `LocalPrefs` itself) is absent, map every key above to the new keys, only writing values that are present in `LocalPrefs` and valid; then set the marker. Do not delete `LocalPrefs` (allows re-check; negligible size) — or delete after success; choose and justify.
- New values win over legacy only if the new file already has the key (fresh installs of the rewrite never have `LocalPrefs`).
- Route: legacy `Int` IDs → `Long` in `route_departure_id` / `route_destination_id`; invalid/missing cities are dropped by the existing restore rule.
- `versionCode = 50`, `versionName = "3.0.0"` (or the values the human chose).
- Verify locally that both `cities_info.db` assets are identical (e.g. `sha256sum` of `~/smart-flight/app/src/main/assets/databases/cities_info.db` and the rewrite's copy; shell only, no Gradle) and state the result in the PR. If they differ, IDs may not match: stop and ask.

## Out of scope
- Migrating permission-request history (original had no equivalent key; first request is harmless).

## Requirements
Required:
- Every legacy key listed is migrated with the documented mapping; unknown/malformed values fall back to defaults.
- Migration runs at most once and never overwrites a value the user set in the new app.
- versionCode > 49.

## Acceptance criteria
- [ ] Robolectric tests: full legacy file → all new values; partial file; malformed values ("7", wrong types); marker prevents re-run; existing new values preserved — verified by: CI unit test
- [ ] Characterization-style test: legacy route IDs → route card shows both cities after launch — verified by: CI unit test
- [ ] `versionCode` 50 (or chosen) — verified by: code review
- [ ] Install over the Play Store 2.4.9 build with customized settings and a route; all preserved — verified by: HUMAN on device

## Tests to add or update
- `LegacyPreferencesMigrationTest`, update `MainActivityCharacterizationTest`.

## Risks and edge cases
- `ListPreference` stores Strings; a legacy app version might have stored Ints — handle both.
- Signing: an update also requires the same signing key; out of scope for code, mention in PR.
