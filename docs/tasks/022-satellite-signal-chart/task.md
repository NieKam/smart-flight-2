# TASK-022 — Satellite signal-strength bar chart (green used / red unused)

## Goal
Restore the GNSS card's main visualization: one bar per satellite showing its C/N0 signal strength, green when used in the fix, red when not, with a Y axis in dB-Hz and satellite index on the X axis. Replace the text list that makes the card thousands of dp tall.

## Context
- Parity finding 1 (REGRESSION, HIGH), verified:
  - Original: `~/smart-flight/app/src/main/java/kniezrec/com/flightinfo/cards/satellites/SatellitesCardView.kt:95-142` draws an MPAndroidChart bar per satellite (`BarEntry(i, sat.signalStrength)`), colors `satellite_green #4caf50` / `satellite_red #f44336` by `usedInFix`; `setupChart` (`:46-93`): left Y axis with 8 labels starting at 1 (`setLabelCount(8, true)`, `axisMinimum = 1f`, `spaceTop = 15f`), X axis at bottom with granularity 1, no grid lines on X, no legend, no touch/zoom, bar width 0.9. Title "Connected to N satellites" (plural `connected_satellites`).
  - Rewrite: one text `SatelliteRow` per satellite ("Satellite N" + "Used/Not used for position"); `GnssSatellite.signalStrengthDbHz` is filled (`gnss/AndroidGnssStatusPlatform.kt:25`) but never rendered.
- Parity inventory: "Connected to N satellites" (used count) is MODERNIZATION and present (`GnssStatusScreen.kt:367,383` before moves) — keep the plural string.
- Screenshot: `~/smart-flight/promo/screen-1.png` (chart with 1–22 Y scale, 0–18 X, green and one red bar).
- TASK-017/018 provide theme tokens; add satellite green/red tokens.

## Dependencies
- TASK-018.

## Original app reference
- `cards/satellites/SatellitesCardView.kt`, `SatellitesCardViewPresenter.kt`, `layout/satellites_card_layout.xml`, `res/values/colors.xml` (`satellite_green`, `satellite_red`), `promo/screen-1.png`.

## Scope
- Compose `Canvas` bar chart (no chart library): bars in satellite list order, height ∝ C/N0, Y axis 8 evenly spaced integer labels from 1 to a rounded max (max C/N0 + ~15% headroom), X axis index labels at a readable step (every 3 as in the screenshot, adaptive to count), horizontal grid lines.
- Colors (palette tokens from TASK-018 only; compare with `promo/screen-1.png`): card container `card`; title "Connected to N satellites" `labelText`; axis labels `valueText`; grid lines and axis lines `labelText` at reduced alpha; bars `satelliteUsed` / `satelliteUnused`.
- Pure function `satelliteChartModel(satellites, …)` computing bars, colors (used/unused) and axis ticks — unit tested.
- Accessibility: one content description summarizing "N satellites visible, M used, strongest X dB-Hz"; the chart is not a list of focusable items.
- Keep the card title and the used-count text.
- Card height fixed (e.g. ~200–240dp chart area) regardless of satellite count; bars get thinner with more satellites (min width ~2dp; if more satellites than fit, keep all bars with minimal spacing).

## Out of scope
- No-satellites animation/tip (TASK-023).

## Requirements
Required:
- Green `#4CAF50` used / red `#F44336` unused (token values).
- Bar height reflects `signalStrengthDbHz`; missing/non-finite values render as 0-height bars.
- Card height does not grow with satellite count.

Recommendations:
- Animate bar height changes briefly (≤ 200 ms) — optional.

## Acceptance criteria
- [ ] `satelliteChartModel` tests: colors by `usedInFix`, tick generation, max scaling, empty and non-finite input — verified by: CI unit test
- [ ] Compose test: 40 satellites → card height unchanged vs. 5 satellites; content description contains counts — verified by: CI unit test
- [ ] Pixel check: Robolectric `@GraphicsMode(NATIVE)` `captureToImage()` of a chart with one used and one unused satellite contains #4CAF50 and #F44336 pixels on a #5B5999 background — verified by: CI unit test
- [ ] Visual match with `promo/screen-1.png` (bars, colors, axes, muted title) — verified by: HUMAN on device
- [ ] Remove `satellite_used`/`satellite_not_used`/`satellite_number` strings if unused — verified by: code review

## Tests to add or update
- New: `SatelliteChartModelTest`, `GnssStatusCardTest` chart cases. Update previous text-row assertions.

## Risks and edge cases
- Very large satellite counts (multi-constellation, 60+): ensure labels do not overlap.
