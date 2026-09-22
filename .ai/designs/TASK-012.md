# TASK-012 Design Specification — Foreground display behavior settings

## 1. Scope and intent

Extend the existing authorized-dashboard Settings destination with a separate **Display** section containing:

- Keep screen always on — off by default.
- Portrait orientation — on by default; off means sensor-based orientation.
- Larger map zoom — off by default; on permits map zoom levels through 9 instead of 6.

The existing five unit selectors remain unchanged and remain in the Settings screen. This design does not add notification, background tracking, card visibility, permission, or other legacy preferences.

The Architect specification is authoritative for behavior. This document defines the screen composition, interaction model, visual treatment, accessibility, lifecycle handoff, and map-state presentation needed to implement it in the current single-activity Compose architecture.

## 2. Inputs reviewed

### Current rewrite

- `app/src/main/java/kniezrec/com/flightinfo/ui/settings/UnitSettingsScreen.kt`
- `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- `app/src/main/java/kniezrec/com/flightinfo/displayunits/UnitPreferences.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/MapCard.kt`
- `app/src/main/java/kniezrec/com/flightinfo/map/MapState.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/theme/Theme.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/theme/Color.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/theme/Type.kt`
- `app/src/main/res/values/strings.xml`
- Existing Settings, persistence, map, orientation, and Compose tests under `app/src/test` and `app/src/androidTest`

### Original application

- `app/src/main/res/xml/app_preferences_layout.xml`
- `app/src/main/res/values/preference_strings.xml`
- `app/src/main/java/kniezrec/com/flightinfo/MainActivityPresenter.kt`
- `app/src/main/java/kniezrec/com/flightinfo/settings/FlightAppPreferences.kt`
- `app/src/main/java/kniezrec/com/flightinfo/base/BaseMapView.kt`
- `app/src/main/java/kniezrec/com/flightinfo/cards/map/MapCardViewPresenter.kt`
- `/home/ai-dev/smart-flight/promo/promo.png` (1024 × 500 dashboard continuity reference)

## 3. Behavior inventory

### Observed legacy behavior

- The legacy General category uses checkbox preferences for the three controls.
- Defaults are: screen awake `false`, portrait forced `true`, larger zoom `false`.
- The legacy screen labels the controls “Keep screen always on”, “Force portrait orientation mode”, and “Force bigger map zoom”.
- The legacy activity applies the screen flag and requested orientation when the activity view is attached.
- The legacy map uses maximum zoom 6 normally and 9 when larger zoom is enabled. Its larger-zoom option warns that tiles beyond the normal range may be unavailable or grey.
- The legacy settings screen also exposes notification and unit preferences; those are explicitly outside this task.

### Required behavior

- Store and independently restore all three booleans. Missing or malformed values fall back to the individual documented default.
- Apply `FLAG_KEEP_SCREEN_ON` and clear only Smart Flight’s use of that flag without restarting the app.
- Persist the orientation choice before requesting portrait or sensor orientation. Apply it through the activity API and reread it after recreation.
- Change only the active map’s maximum allowed zoom: 6 when off, 9 when on. Preserve the default zoom of 3 and follow/recenter zoom of 6.
- If the active map is above 6 when larger zoom is disabled, clamp safely to 6 before or as the maximum is lowered.
- Suppress the existing standard maximum-zoom warning while larger zoom is enabled; restore the normal warning behavior when disabled.
- Keep the existing authorized-dashboard entry point and Settings/About/back precedence.

## 4. Screen structure

The destination remains the current full-screen Settings surface:

```text
Scaffold
├── TopAppBar
│   ├── Navigate-up IconButton
│   └── “Settings”
└── scrollable content, safe drawing insets applied
    └── centered column, width <= 600 dp
        ├── Display section heading
        ├── Keep screen always on row
        ├── Portrait orientation row
        ├── Larger map zoom row
        ├── optional inline larger-zoom warning
        ├── Units section heading
        ├── existing five unit selector rows
        └── dividers between rows
```

Place **Display** before **Units**, because these are global foreground behaviors and the task calls for a distinct section. Keep the existing Units order and choice-dialog behavior. The Display section should not be a dialog or a second destination: each value is changed directly from its row switch.

### Layout and spacing

- Preserve the existing top app bar, scroll behavior, horizontal content padding, safe-drawing treatment, and `widthIn(max = 600.dp)` column.
- Use a readable content inset of approximately 12–16 dp horizontally and 24 dp vertically around the scroll content, consistent with the current Settings screen.
- Section heading: `titleLarge`, with 8–12 dp separation before its first row and 20–24 dp separation before the next section heading.
- Each setting row has a minimum height of 64 dp, with at least 12 dp vertical content padding and a full-width hit target. The switch itself must retain the Material minimum 48 dp interaction area.
- Use a two-column row: a weighted text column on the start side and the switch on the end side. The text column may wrap; it must never be constrained to a single line by the switch.
- Keep dividers between rows, not around the whole section. Dividers should use the theme’s low-emphasis outline color.
- The warning belongs below the Larger map zoom row, inset to the text column or section content, and participates in scrolling. It must not be a transient snackbar: the consequence is persistent while the option is enabled.

## 5. Display row content and states

Each row is a single semantic switch action. Tapping either the label/summary area or the switch toggles the same value; do not create two independently announced controls. The switch is visually on/off, and the summary provides a non-color indication of the current state.

### Keep screen always on

- Label: localized “Keep screen always on”.
- Summary off: localized “Off”.
- Summary on: localized “On”.
- Switch checked state maps directly to the stored preference.
- On toggle: persist, then add the current activity’s keep-screen-on flag. On untoggle: persist, then clear only that flag.

### Portrait orientation

- User-facing label should be concise: “Portrait orientation”. This is a modern rewrite label for the legacy “Force portrait orientation mode”; the behavior remains the same.
- Summary on: localized “Portrait”.
- Summary off: localized “Sensor”.
- Checked means portrait is requested. Unchecked means sensor-based orientation is requested.
- Persist before requesting the new orientation. The row should update immediately from the Compose state; activity recreation must reread the stored value and show the same state.
- Do not show a loading state or a confirmation dialog. If the platform/window mode cannot honor sensor orientation, remain stable and continue to display the selected preference rather than inventing a separate unsupported state.

### Larger map zoom

- Label: localized “Larger map zoom”. This is a clearer modern rewrite label for the legacy “Force bigger map zoom”.
- Summary off: localized “Off”.
- Summary on: localized “On”.
- When on, show an always-available supporting warning immediately below the row: extra zoom may have unavailable or grey map areas because the packaged offline archive may not contain tiles at those levels. The warning is informative and does not disable the switch.
- The warning is part of the row’s accessible description/state so a screen reader receives the consequence without relying on color or visual proximity.
- On toggle, update the active map in place when it exists. Do not reset center, marker, route overlay, default viewport, first-fix behavior, or offline provider. Do not trigger a tile download or enable a network provider.

## 6. Visual design

- Retain the Smart Flight dark-purple page/card character visible in the legacy dashboard reference and already used by the rewrite. Settings itself should remain a clean Material 3 surface using the existing theme tokens rather than introducing a new settings palette.
- Use the current Material 3 typography. Section headings use `titleLarge`; labels use `bodyLarge`; summaries and the zoom warning use `bodyMedium` or an equivalent readable supporting style.
- Primary/on switch colors come from `MaterialTheme.colorScheme.primary`; off state uses the Material 3 neutral outline/surface treatment. Do not encode state only with purple versus grey.
- The warning uses an accessible supporting container/text treatment with sufficient contrast. Avoid an alarm-red-only treatment because unavailable tiles are an expected opt-in consequence, not an error.
- No new decorative icons are necessary for these rows. The switch is the state/control affordance; adding unrelated icons would compete with the simple unit-list hierarchy.
- Preserve the existing purple/dark-purple dashboard styling when returning from Settings; opening Settings should not change the dashboard’s visual state.

## 7. Accessibility and localization

- Add every new label, summary, warning, and semantic description to Android resources. Do not concatenate user-visible English in composables.
- Use a merged row semantics node with `Role.Switch`, checked state, and a localized state description. The row’s action should be announced once, including the current summary (for example, “Portrait orientation, Portrait, switch, on”).
- Do not add a separate clickable parent around a separately actionable switch without merging semantics; avoid duplicate TalkBack focus stops.
- Ensure all row and switch actions are at least 48 dp, and retain visible focus/pressed states supplied by Material components.
- Long localized text and large font scales must wrap naturally. The switch may align to the top/end of a multi-line row rather than forcing vertical clipping.
- Preserve RTL: labels and warning text start-align in the logical start direction, while the switch sits at logical end. Do not use hard-coded left/right padding for content placement.
- Keep content readable in landscape and with font scaling; the 600 dp maximum width prevents excessively long lines on tablets while the outer scroll handles constrained height.

## 8. State, persistence, and lifecycle handoff

Define a small display-preferences model/store alongside the existing preference convention. Use a dedicated namespace distinct from `display_units_*` and legacy preference names. The model has three booleans with defaults:

```text
keepScreenAlwaysOn = false
portraitOrientation = true
largerMapZoom = false
```

Read each key independently with defensive boolean handling. A value of the wrong type, absent key, or otherwise malformed persisted value resolves only that property to its default; it must not invalidate the other two values. Write changes atomically enough for a later activity launch to observe the complete model, following the current `SharedPreferences` store style.

`MainActivity` should own the loaded display preferences in the same way it owns unit preferences. On initial creation and resume/recreation, reread the store. The authorized Settings branch receives the model and a callback that persists and applies the changed value.

Apply platform effects through small testable adapters or equivalent seams:

- screen-awake adapter: add/clear `FLAG_KEEP_SCREEN_ON` on the current `Window` only;
- orientation adapter: request `SCREEN_ORIENTATION_PORTRAIT` or `SCREEN_ORIENTATION_SENSOR` only when the requested value differs, avoiding an orientation loop;
- map-maximum adapter/session input: derive the max from the preference and update the existing map instance.

Apply the stored screen and orientation values as part of activity setup/resume, not only when Settings is visible. Persist the orientation value before calling the platform request. Normal Android recreation is expected; the existing controllers and map/session state must not be deliberately reset by this feature.

## 9. Map behavior and visible states

The max zoom is a policy input to the existing offline map, not a new map session or a new default zoom:

| Larger map zoom | Allowed maximum | Initial zoom | Follow/recenter zoom | Provider |
|---|---:|---:|---:|---|
| Off | 6 | 3 | 6 | packaged offline archive, data connection disabled |
| On | 9 | 3 | 6 | packaged offline archive, data connection disabled |

When the map is already composed, update `maxZoomLevel` in place where the osmdroid instance supports it. If disabling while current zoom is greater than 6, clamp the current zoom to 6 before/with the max update, then invalidate/reconcile the viewport without changing its center or route overlays. Enabling must not jump the viewport to zoom 9.

The standard “maximum zoom reached” behavior remains active for the normal range. It is suppressed while the preference permits the larger range and restored when the preference returns to off. The warning suppression is driven by the same preference that controls the max; do not duplicate a second independent map flag.

Map states remain the existing states: loading, ready, unavailable, and inactive. The larger-zoom warning is not a map-loading/error state and must not make the offline map unavailable. If zoom-9 tiles are missing, the map remains offline and may show grey/unavailable areas as warned.

## 10. Responsive interaction flows

### Open and edit Settings

1. Authorized user selects the existing dashboard Settings action.
2. Settings opens with Display above Units and all values loaded from the store.
3. A row toggle persists and updates its summary immediately.
4. Back/up returns to the dashboard; unit, route, sensor/location, map, About, and permission state are unchanged.

### Toggle orientation

1. User toggles Portrait orientation.
2. Store the new value and request portrait or sensor orientation.
3. Android may recreate the activity. On recreation, reread the store, reapply the same requested orientation without looping, and render the selected summary.
4. The existing dashboard resumes with its normal observation lifecycle.

### Toggle larger zoom while map is visible

1. User changes Larger map zoom in Settings.
2. Store the value and update the map policy when the map instance is active.
3. Returning to the dashboard preserves the current map session/position where the existing lifecycle permits it; it does not download tiles.
4. Disabling safely clamps an out-of-range zoom and restores standard warning behavior.

## 11. Loading, empty, and error handling

There is no independent loading, empty, or error state for the preference controls. They always render from defaults or the stored model.

- Missing/malformed persistence: silently use the affected documented default.
- Orientation platform limitation: keep the selected value and stable UI; do not add a new product-facing error requirement.
- Offline archive unavailable: retain the existing MapCard unavailable state and text; the larger-zoom setting must not change it.
- Missing high-zoom tiles: preserve the enabled setting and show the documented persistent warning; do not fall back to network.

## 12. Verification expectations

The implementation should add focused coverage for:

- independent defaults and malformed/missing display values;
- round-trip persistence;
- screen flag add/clear and orientation requests;
- orientation persistence across recreation/resume;
- Settings semantics, summaries, warning announcement, and minimum interaction targets;
- max zoom selection (6/9), unchanged initial/follow zoom values, in-place update/clamping, and warning suppression/restoration;
- authorized dashboard entry and absence of controls from permission onboarding.

Existing project checks and the current unit/settings/map tests must continue to pass. No new dependency, permission, network provider, service, XML preference screen, second activity, or navigation framework is part of this design.

## 13. Intentional deviations from the legacy UI

- The legacy XML places these checkboxes in a broad “General” category alongside notification. The rewrite uses a dedicated **Display** section and excludes notification because the task explicitly defers background-service behavior.
- The rewrite uses direct Material 3 switches with explicit current-state summaries and merged switch semantics instead of legacy checkbox rows. This improves discoverability, touch targets, and screen-reader output.
- “Force portrait orientation mode” becomes “Portrait orientation”, and “Force bigger map zoom” becomes “Larger map zoom”. The shorter labels preserve the legacy meaning while reducing jargon and leaving the current state in the summary.
- The legacy larger-zoom warning is a preference summary. The rewrite presents it as a clearly readable, resource-backed supporting warning tied to the enabled state and accessibility description, so its consequence remains visible without requiring the user to infer it from a long static summary.
- The rewrite keeps the current 600 dp centered Compose column and responsive scrolling rather than reproducing the legacy XML preference layout. This is intentional for large screens, landscape, RTL, and large font scales.

## 14. Unresolved questions

None. The task specification provides the required defaults, labels/meaning, zoom limits, lifecycle constraints, and explicit scope boundaries.
