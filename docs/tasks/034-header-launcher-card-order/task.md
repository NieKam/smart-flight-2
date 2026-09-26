# TASK-034 — Toolbar with overflow menu, original launcher icon, original card order

## Goal
Restore the original app chrome: a purple top app bar with the centered title "Smart Flight" and a "⋮" overflow menu (Settings, About), the original launcher icon (white plane on purple), and the original card order.

## Context
- Parity finding 24 (VISUAL_MISMATCH, LOW), header and launcher parts:
  - Original header: `purple_main` toolbar with "⋮" overflow (`~/smart-flight/app/src/main/res/layout/activity_main.xml:11-36`, `menu/app_menu.xml`); screenshots `promo/screen-1.png`, `screen-2.png`. Rewrite: title on the page color with cyan "Settings"/"About" text buttons, overflow only on narrow screens (`GnssStatusScreen.kt:191-267` before moves).
  - Original launcher: white plane on a purple square (`mipmap-xxxhdpi/ic_launcher.png`, `drawable-v24/ic_launcher_foreground.xml`, `drawable/ic_launcher_background.xml`). Rewrite: new dark #211D46 design (`res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `mipmap-anydpi/ic_launcher*.xml`).
  - (Notification icon part is TASK-025.)
- Parity finding 27 (VISUAL_MISMATCH, LOW): original code order (`cards/adapter/CardViewContainer.kt:53-62`): Course, Horizon, Satellites, Flight parameters, Location (nearby city), Route, Map. Rewrite: GNSS, Flight parameters, Course, Horizon, Nearby, Route, Map. Note: `promo/screen-1.png` (older than the horizon card) shows Satellites, Course, Flight parameters. Planner decision: use the original code order (latest shipped behavior); README open question lets the human choose the screenshot order.
- With TASK-024, the permission card goes first when not granted.

## Dependencies
- TASK-024, TASK-017.

## Original app reference
- `layout/activity_main.xml`, `menu/app_menu.xml`, `drawable-v24/ic_launcher_foreground.xml`, `drawable/ic_launcher_background.xml`, `mipmap-*/ic_launcher*.png`, `cards/adapter/CardViewContainer.kt`, `promo/screen-1.png`, `screen-2.png`.

## Scope
- `CenterAlignedTopAppBar` (card purple container, light title) with an overflow `IconButton` (`Icons.Default.MoreVert` or a vector) → `DropdownMenu` (Settings, About); edge-to-edge insets handled.
- Launcher: adaptive icon using the original foreground vector and background color; monochrome layer for themed icons (API 33+); round icon.
- Card order: Course, Horizon, GNSS (satellites), Flight parameters, Nearby city, Route, Map.

## Out of scope
- Theme colors (TASK-018).

## Requirements
Required:
- Overflow menu always (all widths); card order as decided; original launcher artwork.

## Acceptance criteria
- [ ] Compose test: overflow menu contains Settings and About and opens them — verified by: CI unit test
- [ ] Compose test: card order (by test tags) — verified by: CI unit test
- [ ] Launcher icon looks like the original on the home screen (normal and themed) — verified by: HUMAN on device

## Tests to add or update
- `DashboardScreenTest` (order, menu), update header tests.

## Risks and edge cases
- The original foreground vector may not fit the adaptive safe zone (66dp of 108dp); add padding via `inset` if clipped.
