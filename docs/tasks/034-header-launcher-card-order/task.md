# TASK-034 — Toolbar with overflow menu, original launcher icon, card order

## Goal
Restore the original app chrome: a purple top app bar with the centered title "Smart Flight" and a "⋮" overflow menu (Settings, About), the original launcher icon (white plane on a purple square), and a card order that starts with the satellite status.

## Context
- Parity finding 24 (VISUAL_MISMATCH, LOW), header and launcher parts:
  - Original header: `purple_main` toolbar with "⋮" overflow (`~/smart-flight/app/src/main/res/layout/activity_main.xml:11-36`, `menu/app_menu.xml`); screenshots `promo/screen-1.png`, `screen-2.png` (white bold centered title, white overflow icon, status bar in the same purple). Rewrite: title on the page color with cyan "Settings"/"About" text buttons, overflow only on narrow screens (`GnssStatusScreen.kt:191-267` before moves).
  - Rewrite launcher: new dark #211D46 design (`app/src/main/res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `mipmap-anydpi/ic_launcher.xml`, `ic_launcher_round.xml`).
  - (Notification icon part is TASK-025.)
- Original launcher icon, verified by the planner (the parity report points at the wrong files): the original manifest uses `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round` (`~/smart-flight/app/src/main/AndroidManifest.xml:11,13`), which exist only as PNGs (`mipmap-mdpi` … `mipmap-xxxhdpi/ic_launcher.png`, `ic_launcher_round.png`): a light lavender/white top-down airliner on a flat purple rounded square (round variant: purple circle) with a long diagonal shadow. There is no `mipmap-anydpi-v26` in the original, so `drawable-v24/ic_launcher_foreground.xml` and `drawable/ic_launcher_background.xml` (teal #26A69A grid) are unused Android Studio template files — do not copy them.
- Human decision: restore the original launcher icon.
- Parity finding 27 (VISUAL_MISMATCH, LOW): original code order (`cards/adapter/CardViewContainer.kt:53-62`): Course, Horizon, Satellites, Flight parameters, Location (nearby city), Route, Map. `promo/screen-1.png` (older than the horizon card, and the order shown on the store listing) shows Satellites, Course, Flight parameters. Rewrite today: GNSS, Flight parameters, Course, Horizon, Nearby, Route, Map.
- Human decision: the planner decides the card order based on what is most practical for the user. Planner decision and reasoning:
  - Order: **Satellites (GNSS), Course, Horizon, Flight parameters, Nearby city, Route, Map.**
  - Satellites first: it is the status gate for everything below it. Speed, altitude, nearby city, route progress and the map position all depend on a GPS fix; the satellite card is where the user learns that they are still waiting, that location is off (TASK-019) and that they should move closer to the window (TASK-023). Seeing it first explains empty cards and prompts the one action the user can take.
  - Course and Horizon next: they work immediately from sensors without a fix, so the top of the screen is useful from the first second; kept together as a pair, in the original relative order.
  - Then the fix-dependent data (Flight parameters, Nearby city, Route) and the Map last (tallest card; it would push everything else off-screen).
  - It is the original code order with only the Satellites card moved to the top, and it matches the order users saw in the store screenshots (`promo/screen-1.png`), so it stays recognisable.
  - When location permission is not granted (TASK-024), the permission card comes first, followed by Course and Horizon (the only cards shown then).

## Dependencies
- TASK-024, TASK-018 (palette tokens).

## Original app reference
- `layout/activity_main.xml`, `menu/app_menu.xml`, `AndroidManifest.xml`, `mipmap-*/ic_launcher.png`, `mipmap-*/ic_launcher_round.png`, `res/drawable-v21/small_plane_icon.xml` / `res/drawable/plane_icon.xml` (plane silhouette paths), `cards/adapter/CardViewContainer.kt`, `promo/screen-1.png`, `screen-2.png`.

## Scope
- `CenterAlignedTopAppBar`: container `card` #5B5999 (drawn behind the status bar, edge-to-edge), title "Smart Flight" and overflow icon in `toolbarTitle` (#FFFFFF), overflow `IconButton` (`Icons.Default.MoreVert` or a vector) → `DropdownMenu` (Settings, About) with `page` container and `valueText` items.
- Launcher icon (adaptive, minSdk 31 always uses it):
  - Background layer: solid color sampled from the original `mipmap-xxxhdpi/ic_launcher.png` (visually ≈ `purple_dark` #484685; state the sampled value in the PR and use the palette token if it matches within a few units).
  - Foreground layer: vector of the original plane (the silhouette in `small_plane_icon.xml`/`plane_icon.xml` matches the launcher plane; if not close enough, trace the PNG), light lavender/white as in the PNG, scaled into the 66dp safe zone of the 108dp canvas. Recommendation: reproduce the long diagonal shadow as a semi-transparent dark path; optional.
  - Monochrome layer (themed icons, API 33+): the plane path.
  - `mipmap-anydpi/ic_launcher.xml` and `ic_launcher_round.xml` point to the new layers; delete the #211D46 design files. Optionally also copy the original PNGs as `mipmap-*/ic_launcher.png` for tools that read legacy icons (not needed at minSdk 31; skip unless useful).
- Card order: Satellites, Course, Horizon, Flight parameters, Nearby city, Route, Map (permission card first when not granted). Keep the decision and its reasoning in a KDoc comment next to the order definition.

## Out of scope
- Theme colors (TASK-018).

## Requirements
Required:
- Overflow menu always (all widths); card order as decided above; original launcher artwork (plane on purple), palette colors only.
- Behavior/visual changes listed in PR (card order, header, launcher).

## Acceptance criteria
- [ ] Compose test: overflow menu contains Settings and About and opens them — verified by: CI unit test
- [ ] Compose test: card order by test tags (granted: Satellites, Course, Horizon, Flight parameters, Nearby city, Route, Map; not granted: Permission, Course, Horizon) — verified by: CI unit test
- [ ] Pixel check: top app bar background = #5B5999 — verified by: CI unit test (Robolectric `captureToImage`)
- [ ] Palette guard (TASK-018) passes; launcher XML colors reviewed against the PNG — verified by: CI unit test + code review
- [ ] Launcher icon looks like the original `ic_launcher.png` on the home screen (normal, round mask and themed) — verified by: HUMAN on device
- [ ] Header matches `promo/screen-1.png` — verified by: HUMAN on device

## Tests to add or update
- `DashboardScreenTest` (order, menu), update header tests.

## Risks and edge cases
- The plane must fit the adaptive safe zone (66dp of 108dp); add `inset` if clipped by circle masks.
- Tests that locate cards by index break with the new order; locate by test tag.
