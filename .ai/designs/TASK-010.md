# TASK-010 — Configurable display units

## Purpose and scope

Add a discoverable, accessible Compose settings destination for the five
display-unit preferences used by the authorized Smart Flight dashboard. The
settings screen changes presentation only: all sensor, location, route, map,
and arrival calculations continue to use the existing canonical metric state.

This design covers entry from the authorized dashboard, the settings screen,
single-choice interactions, persistence, immediate dashboard updates,
formatting and accessibility states, and portrait/landscape behavior. It does
not add general application settings, permission controls, background tracking,
or a second activity/module/navigation framework.

## Inputs inspected

### Authoritative requirements

- `.ai/tasks/TASK-010.md`

### Rewrite implementation and theme

- `app/src/main/java/kniezrec/com/flightinfo/MainActivity.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/GnssStatusScreen.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/FlightParametersCard.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/gnss/NearbyCityCard.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/route/RouteCard.kt`
- `app/src/main/java/kniezrec/com/flightinfo/route/RouteModels.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/theme/Theme.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/theme/Color.kt`
- `app/src/main/java/kniezrec/com/flightinfo/ui/permission/PermissionColors.kt`
- `app/src/main/res/values/strings.xml`
- `.ai/designs/TASK-009.md`

### Legacy behavior and visual references

- `/home/ai-dev/smart-flight/app/src/main/res/xml/app_preferences_layout.xml`
- `/home/ai-dev/smart-flight/app/src/main/res/values/preference_strings.xml`
- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/settings/SettingsFragment.kt`
- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/settings/SettingsActivity.kt`
- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/common/Navigation.kt`
- `/home/ai-dev/smart-flight/app/src/main/res/menu/app_menu.xml`
- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/settings/FlightAppPreferences.kt`
- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/avionic/Speed.kt`
- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/avionic/calculators/Altitude.kt`
- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/avionic/calculators/VerticalSpeedCalculator.kt`
- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/avionic/calculators/Pressure.kt`
- `/home/ai-dev/smart-flight/app/src/main/java/kniezrec/com/flightinfo/avionic/calculators/DistanceCalculator.kt`
- `/home/ai-dev/smart-flight/promo/promo.png` (1024×500 RGBA dashboard reference)

## Behavior classification

### Observed legacy behavior

- Settings is opened from the main dashboard menu and has a back/up action.
- A `Units` preference category contains independent list preferences for
  speed, altitude, distance, vertical speed, and pressure.
- Each legacy list displays a descriptive option name and stores a small
  independent value; the first option is the default for every selector.
- The legacy dashboard recalculates visible speed, altitude, pressure,
  vertical speed, nearby distance, and route distances when preferences
  change. Route time/duration is not a display-distance preference.
- The legacy visual reference is a dark-purple dashboard with lavender text
  and cyan/purple action accents. The legacy settings itself is a platform
  preference screen, not a Compose design to reproduce literally.

### Required behavior from TASK-010

- An authorized user can open a clearly labeled Settings destination and
  return without losing dashboard state or restarting observations.
- The five selectors and exact choices/defaults are:

  | Selector | Choices, in order | Default |
  | --- | --- | --- |
  | Speed | `km/h`, `mph`, `kt` | `km/h` |
  | Altitude | `m`, `ft` | `m` |
  | Distance | `km`, `mi` | `km` |
  | Vertical speed | `m/s`, `m/min`, `ft/min` | `m/s` |
  | Pressure | `mbar`, `inHg` | `mbar` |

- Each choice persists independently across activity recreation and process
  restart. Missing, malformed, or out-of-range values fall back independently
  to that selector's metric default.
- Selecting an option applies it immediately to already-rendered valid values
  when the user confirms/selects it. No app restart is needed.
- Conversion and formatting happen at presentation boundaries. The existing
  canonical values remain km/h, m/s, m, mbar, and km in domain/controller
  state. Route endpoints, geometry, map overlays, lookups, and sensor streams
  are unchanged.
- Speed affects only the Speed row; vertical speed only the Vertical speed
  row; altitude only Altitude; pressure only Pressure. Distance affects nearby
  city distance, route fixed distance, and route remaining distance. Arrival
  time and duration remain unchanged.
- Waiting, unavailable, error, dash, and placeholder states retain their
  current meaning and text. A preference change must not create a zero value,
  `NaN`, infinity, a lookup, a map reload, or a sensor lifecycle transition.
- Settings is not required on permission onboarding and is reachable from the
  authorized dashboard only.

## Design decisions

- Use a Material 3 top app bar on the dashboard with the title `Smart Flight`
  and a trailing settings action. The action is icon-led with a visible
  `Settings` label or an equivalent clearly labeled Material action at compact
  widths; it must never be icon-only without an accessible description. This
  upgrades the current title-only header while preserving the established
  dashboard shell.
- Keep settings in the existing single-activity Compose state flow as a
  screen/destination state. Do not introduce a legacy `PreferenceFragment`, a
  second activity, or a navigation dependency solely for this task.
- Use a modal single-choice dialog for each row. It is familiar, works with
  touch, keyboard, TalkBack, RTL, and large text, and avoids a long expanded
  dropdown being clipped by a small window. Tapping an option writes the
  preference, updates the row summary, and closes that dialog. There is no
  separate global Save button or batch-commit state.
- Keep the preference store behind a typed model/persistence boundary. The UI
  observes one current preference snapshot, so one change recomposes all
  affected visible cards without recreating controllers or observers.
- Use new stable, namespaced keys for this rewrite. Do not reuse location
  permission or route preference stores, and do not expose the legacy general
  settings excluded by the task.

## Dashboard entry and navigation

### Authorized dashboard

The dashboard retains its current order and dark-purple page/card treatment.
Its top app bar occupies the existing header position and remains within the
safe drawing area. The title is aligned to the logical start; Settings is a
minimum 48dp action target aligned to the logical end. The control has a
localized visible label or tooltip-equivalent semantics such as `Settings,
opens display unit settings` and a settings icon may reinforce recognition.

Opening settings only changes the rendered screen state. It must not pause or
stop the existing authorized foreground observation, although the settings
screen itself does not display dashboard cards. Returning with the app back
action, system back, or top-app-bar back action restores the same in-memory
dashboard state, including route endpoints/details, nearby city, map state,
and scroll position as far as the existing Compose state permits.

The Settings action is not composed on permission onboarding. Android system
location settings remain the existing permission/location action and are not
re-labeled or redirected to this screen.

### Settings screen structure

Use a full-screen page on the existing Smart Flight background with a Material
3 top app bar:

1. Leading back arrow, minimum 48dp target, with `Navigate up` semantics.
2. Title `Settings` using the app-bar title style.
3. Vertically scrollable content below the app bar.
4. A localized `Units` section heading.
5. Five full-width selector rows in this order: Speed, Altitude, Distance,
   Vertical speed, Pressure.

The content column uses the current dashboard's 12dp outer gutter and a 600dp
maximum readable width, centered on wider windows. The section may be placed
inside a Material 3 surface/card that is visually related to the existing
purple cards; do not create a dense legacy preference list. A subtle divider
between rows is acceptable, but each row must remain a distinct, easily
scannable control.

Each row has a logical-start label and a logical-end current value plus a
clear affordance (trailing chevron or exposed dropdown indicator). The full
row is clickable and at least 48dp high; allow the row to grow when labels or
values wrap. Example summaries are compact visible strings: `Speed — km/h`,
`Vertical speed — m/s`, and `Pressure — mbar`. The current value is visible
without opening the control.

Suggested vertical rhythm, subject to Material 3 minimums and font scale:

- 16dp content inset inside the settings surface;
- 24dp between the app-bar content and `Units` heading;
- 8dp from section heading to first row;
- 12–16dp horizontal separation between label and value;
- 12dp vertical padding per selector row, with a 48dp minimum touch height;
- 24dp bottom content padding so the final row is not flush with navigation.

Do not make the settings screen a fixed-height layout. It must scroll when
large text, a narrow portrait window, or a translated label requires it.

## Selector dialog behavior

When a selector row is activated, show a Material 3 single-choice dialog:

- Dialog title is the full localized selector name, e.g. `Speed` or
  `Vertical speed`.
- Options use expanded localized names with compact units, not abbreviations
  alone: `Kilometres per hour (km/h)`, `Miles per hour (mph)`, `Knots (kt)`;
  `Metres (m)`, `Feet (ft)`; `Kilometres (km)`, `Miles (mi)`;
  `Metres per second (m/s)`, `Metres per minute (m/min)`, `Feet per minute
  (ft/min)`; `Millibar (mbar)`, `Inch of mercury (inHg)`.
- Exactly one option is selected and marked with the standard radio semantics
  and visual selected state. Selection is not communicated by color alone.
- Selecting an option persists that selector immediately, updates the screen
  summary, applies the new presentation to the dashboard, and dismisses the
  dialog. Dismissing with back or outside tap leaves the prior value.
- The currently selected option receives initial accessibility focus when
  practical; focus returns to the activated row after dismissal.
- No conversion preview, reset-all action, or unrelated setting is added.

## Presentation and formatting rules

Use deterministic, independently testable presentation functions. They accept
canonical finite values and a typed unit selection, and return a localized
number plus a resource-backed compact unit. Reject or safely represent any
non-finite input using the existing unavailable/dash behavior; never render
`NaN` or infinity.

Conversions must match the task's observable semantics:

| Presentation | Canonical input | Conversion |
| --- | --- | --- |
| `km/h` | km/h | × 1 |
| `mph` | km/h | × 0.621371 |
| `kt` | km/h | ÷ 1.852 (equivalent to the legacy knot conversion) |
| `m` | m | × 1 |
| `ft` | m | × 3.28084 |
| `km` | km | × 1 |
| `mi` | km | × 0.621371 |
| `m/s` | m/s | × 1 |
| `m/min` | m/s | × 60 |
| `ft/min` | m/s | × 196.850394 |
| `mbar` | mbar | × 1 |
| `inHg` | mbar | × 0.02953 |

Format valid normal values with the existing row convention of one locale-aware
decimal place, including zero. Preserve the current vertical-speed sign
behavior: negative values retain a localized minus sign and non-negative
values retain the existing explicit plus-sign behavior where currently used.
Keep date/time and duration presentation untouched. Keep accessibility text
expanded, for example `12.4 miles` or `1013.3 millibars`, while visible card
values use the compact abbreviations required by the task.

When a unit changes, a valid value currently visible in Flight parameters,
Nearby city, or Route is reformatted from the canonical state on the next
composition. A waiting/unavailable/error state remains exactly that state and
does not receive a fabricated converted value.

## Persistence and lifecycle behavior

The typed preference model owns five independent selections and metric defaults.
The persistence boundary reads all five values at initialization, sanitizes
each independently, and publishes changes to the Compose layer. Writes must
complete through the platform local store before treating the new selection as
the current persisted value; the visible UI may update from the same resulting
typed state immediately.

The dashboard and settings screen observe the same model instance for the
active activity. Returning from settings must not create a second sensor,
GNSS, compass, horizon, pressure, nearby-city, map, or route observer. A
process restart reconstructs preferences from storage and applies them before
the first valid dashboard values are presented. Preference changes never
invalidate route details or trigger city/map/network work.

## Accessibility, localization, and responsive behavior

- Every label, section title, option name, compact unit, expanded unit,
  selector summary, dialog title, action description, and unavailable text is
  resource-backed. Add plural/locale resources only where the existing
  formatting conventions require them.
- Merge each selector row's semantics so TalkBack announces its purpose and
  current value, for example `Distance, kilometres, double tap to change`.
  Expose a single-choice role in the dialog and selected state for the active
  option.
- Keep all controls at least 48dp in both dimensions. Preserve visible focus
  indicators for keyboard, switch access, and other non-touch input.
- Use logical start/end layout and text alignment; do not use absolute left or
  right placement. Compact units may remain Latin abbreviations in RTL, but
  the label and expanded option wording must mirror and localize correctly.
- At large font scales, allow labels, summaries, and options to wrap and let
  the page/dialog grow or scroll. Never ellipsize a current value, clip a
  unit, or rely on color to communicate selection.

### Portrait

Use one readable settings column and vertical scrolling. The five rows remain
in the specified order. Dialog options scroll if needed rather than being
compressed below touch size.

### Landscape and wider windows

Keep the settings content centered with a 600dp maximum width rather than
stretching selectors across the whole display. The same single column and
order are retained; do not introduce a landscape-only two-column layout that
would complicate reading order or accessibility. The dashboard keeps its
existing centered card width and scroll behavior.

## Intentional deviations from the legacy application

- The legacy entry point is an overflow/menu action that opens a separate
  `SettingsActivity` containing `PreferenceFragmentCompat`. The rewrite uses
  a clearly visible Material 3 top-app-bar Settings action and a Compose
  screen/destination in the existing single activity. This improves
  discoverability and preserves dashboard state without reproducing the old
  lifecycle.
- The legacy screen includes General preferences for notifications, keeping
  the screen on, forced map zoom, and portrait orientation. They are
  intentionally excluded because TASK-010 authorizes display units only.
- Legacy list summaries use verbose option text and platform preference
  styling. The rewrite shows compact current summaries, expanded accessible
  dialog labels, standard Material 3 single-choice semantics, and responsive
  wrapping.
- Legacy conversion/presenter code recalculates values through preference
  listeners. The rewrite keeps all canonical domain values metric and performs
  conversion only at the presentation boundary, so unit changes cannot alter
  route geometry, sensor state, or lookup timing.
- The current rewrite header is title-only and dashboard cards use a dark
  purple/cyan language. The settings screen extends that shell with Material 3
  surfaces and app-bar patterns instead of copying the legacy XML dimensions
  or platform preference styling.

## Developer verification checklist

Verify through unit and Compose/UI tests and manual checks:

- Settings appears only for the authorized dashboard, has a visible label,
  opens and returns with state intact, and supports system/up back.
- All five rows show metric defaults on a clean store, expose the exact option
  sets in the specified order, and allow exactly one selected option.
- Each preference persists independently through recreation and process
  restart; malformed values fall back only for their own selector.
- All conversion factors, one-decimal locale-aware formatting, vertical-speed
  signs, negative/zero/large values, and non-finite safeguards are covered.
- Speed, vertical speed, altitude, pressure, nearby distance, and both route
  distances update immediately; route arrival and duration do not change.
- Valid values update while already rendered; waiting, unavailable, error, and
  dash states remain unchanged and never display invalid numerics.
- A unit change does not restart or duplicate any sensor, GNSS, location,
  compass, horizon, nearby-city, map, or route observer and does not mutate
  canonical state.
- Rows/dialogs meet 48dp touch targets, expose merged current-value semantics,
  show focus/selected states, wrap at large font scales, scroll when needed,
  and mirror correctly in RTL and landscape.
- Resource-backed labels and expanded accessibility units are used; no
  notification, tracking, orientation, map zoom, network request, or unrelated
  legacy setting is introduced.

## Unresolved questions

None. TASK-010 authorizes either dropdowns or single-choice dialogs; this design
selects modal single-choice dialogs because they provide a compact, accessible
interaction for the five independent options while remaining responsive.
