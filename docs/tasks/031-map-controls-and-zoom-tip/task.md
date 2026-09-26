# TASK-031 — Map controls with icons, a real expand, and the max-zoom tip that leads to Settings

## Goal
Use drawable icons for the map buttons, make "expand" roughly double the map height and scroll it into view, and replace the permanent max-zoom caption with the original tip (shown at most 4 times) that has a "Settings" action opening Settings with the "Force bigger map zoom" row highlighted.

## Context
- Parity finding 22 (VISUAL_MISMATCH, LOW): original drawable icons `ic_expand`/`ic_shrink` (animated swap) and `drawing_pin_icon` for "my location"; expanding doubles the card height and scrolls to it (`~/smart-flight/app/src/main/java/kniezrec/com/flightinfo/cards/map/MapCardView.kt:71-75,194-218`, `layout/map_card_layout.xml:20-40`). Rewrite: text glyphs "↕"/"◎" (`MapCard.kt:187-194` before moves; TASK-014 replaced the locale-dependent glyph choice with an explicit kind); height 240–360dp collapsed and ≤ 520dp expanded from width ratios (`:136-150`), about 1.4× on phones, no scroll.
- Parity finding 20 (MISSING, LOW): original `MapCardViewPresenter.kt:186-192` shows a top snackbar with `max_zoom_tip` ("This is max zoom. Force bigger in settings.") at most 4 times (`settings/FlightAppPreferences.kt:19-45`, key `tip_shown_count`, limit 4), not when bigger zoom is enabled; its "Settings" action opens Settings with the "Force bigger map zoom" row flashing (`settings/CustomCheckBoxPreference.kt:31-44`, extra `HIGHLIGHT_CUSTOM_SETTINGS`). Rewrite: a persistent inline caption (`MapCard.kt:113-120`) with no route to the setting.
- Parity "Not verified": map panning inside the scrolling page; TASK-014 checked it on a device. Re-check after changing the height.
- TASK-036 migrates `tip_shown_count` from the original `LocalPrefs`.

## Dependencies
- TASK-030, TASK-015 (Settings ViewModel), TASK-016.

## Original app reference
- `cards/map/MapCardView.kt`, `MapCardViewPresenter.kt`, `layout/map_card_layout.xml`, `res/drawable/ic_expand.xml`, `ic_shrink.xml`, `drawing_pin_icon.xml`, `settings/CustomCheckBoxPreference.kt`, `common/snackbar/TopSnackbar*.java`, string `max_zoom_tip`.

## Scope
- Import `ic_expand`, `ic_shrink`, `drawing_pin_icon` as vector drawables; `Icon` buttons with content descriptions; `AnimatedContent`/crossfade between expand and shrink.
- Expanded height = 2 × collapsed height (bounded by the viewport height minus header); after expanding, `bringIntoViewRequester` scrolls the map fully into view.
- Tip: when the user hits the max zoom (standard limit) and "larger map zoom" is off and the shown count < 4, show a snackbar (Material3 `SnackbarHost`, top or bottom — top matches original, pick and justify) with the tip text and a "Settings" action; increment the counter (`MapTipRepository`, SharedPreferences, key `map_zoom_tip_shown_count`).
- "Settings" action opens the Settings overlay and highlights the "Force bigger map zoom" row (brief pulsing background, 2–3 flashes) and scrolls to it.
- Remove the permanent inline caption.

## Out of scope
- Picker map (TASK-032).

## Requirements
Required:
- Icons instead of text glyphs; 2× expand with scroll-into-view; tip at most 4 times total; Settings highlight.

## Acceptance criteria
- [ ] Pure/VM tests: tip shown only when at max, bigger zoom off, count < 4; counter increments; never after 4 — verified by: CI unit test
- [ ] Compose test: expand toggles icon/description and height doubles (measured bounds) — verified by: CI unit test
- [ ] Compose test: tip action opens Settings with highlighted row (test tag/semantics state) — verified by: CI unit test
- [ ] Expand scrolls map into view; panning works — verified by: HUMAN on device

## Tests to add or update
- `MapTipRepositoryTest`, `MapViewModelTest`, `MapCardTest`, `UnitSettingsScreenTest` (highlight).

## Risks and edge cases
- Zoom events fire repeatedly at max; count once per "reached max" transition, not per event.
