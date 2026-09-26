# TASK-019 — Tell the user when location/GPS is off (dialog + card state), and react to changes mid-session

## Goal
Restore the original "Enable GPS" prompt and make the GNSS card show the correct state when location services are off, when there is no GNSS hardware, and when registration fails. Update live when the user toggles location.

## Context
- Parity finding 2 (REGRESSION, HIGH), verified:
  - Original: `~/smart-flight/app/src/main/java/kniezrec/com/flightinfo/MainActivityPresenter.kt:22-28` calls `showGpsNotEnabledDialog()` every time the Activity is attached with GPS disabled; `common/DialogUtils.kt:27-37` shows title `enable_gps_title` ("Enable GPS"), message `enable_gsp_message` ("Your GPS seems to be disabled, do you want to enable it?"), "Yes" opens location settings, "No" dismisses.
  - Rewrite (before migration): the dashboard only ever emitted `Waiting`; `LocationServicesDisabled` was unreachable; the "Location services are off / Open location settings" UI in the GNSS card (`GnssStatusScreen.kt:303-310` before TASK-016) was dead code. When location is turned off mid-session, the card kept stale satellites.
- After TASK-007..016: `LocationRepository` exposes `locationEnabled: StateFlow<Boolean>`, `hasGnssHardware()` and propagates registration failures; `GnssStatusViewModel` maps satellites only.
- `GnssStatusState` already has `LocationServicesDisabled`, `Unavailable`, `Error` with UI strings (`location_services_off_title/body`, `open_location_settings`, `gnss_unavailable_*`, `gnss_error_*`).

## Dependencies
- TASK-016.

## Original app reference
- `MainActivityPresenter.kt:22-28`, `common/DialogUtils.kt:27-37`, `common/Navigation.kt` (`goToLocationSettings`), strings `enable_gps_title`, `enable_gsp_message`, `yes`, `no` in `res/values/strings.xml` and `res/values-pl/strings.xml`.

## Scope
- `GnssStatusViewModel`: `combine(locationEnabled, satellites)` → `LocationServicesDisabled` when off (clear satellites), `Unavailable` when no GNSS hardware, `Error` when registration fails (retry restarts collection), else `Waiting`/`Available`.
- Flight parameters, nearby and route cards: when location turns off, show their waiting state (as today's `stop()` did) — do not show stale values.
- Dialog: an `AlertDialog` (brand theme) "Enable GPS" with Yes → open location settings (existing `openLocationSettings()` with snackbar fallback), No → dismiss. Shown once per Activity start (`ON_START`) when location is off and permission is granted; not re-shown on rotation (saveable flag); not shown if location becomes disabled while already visible (card state covers that, as in the original which only checked on attach).
- Returning from settings with location enabled resumes data automatically (repository reacts to `PROVIDERS_CHANGED`).
- Add dialog strings to `res/values/strings.xml` (Polish in TASK-036).

## Out of scope
- Satellite chart (TASK-022), searching animation (TASK-023).

## Requirements
Required:
- Dialog on start with location off; card shows "Location services are off" with an action; live transitions both ways.
- Behavior change vs. current rewrite: list in PR.

## Acceptance criteria
- [ ] ViewModel tests: disabled → `LocationServicesDisabled`; enabled + no hardware → `Unavailable`; registration failure → `Error` → retry; re-enable → `Waiting` → `Available` — verified by: CI unit test
- [ ] Robolectric test: launch with providers disabled shows the dialog; "Yes" fires `ACTION_LOCATION_SOURCE_SETTINGS`; rotation does not show it twice — verified by: CI unit test
- [ ] Toggling location in quick settings updates the card within ~1 s and data resumes — verified by: HUMAN on device

## Tests to add or update
- Update `GnssStatusViewModelTest`; new dialog test; update characterization tests if they assumed `Waiting` with providers off.

## Risks and edge cases
- Robolectric `ShadowLocationManager.setProviderEnabled` + broadcast of `PROVIDERS_CHANGED_ACTION` must be sent explicitly in tests.
- GPS provider vs. location master switch: the original checked the GPS provider; the rewrite's service eligibility accepts GPS or network (`LocationForegroundService.kt:133-138`). Decision: "disabled" = GPS provider disabled (matches the original; on current Android versions the GPS provider follows the location master switch). Document this in the PR and keep service eligibility unchanged.
