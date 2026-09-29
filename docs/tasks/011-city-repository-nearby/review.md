# TASK-011 — Review iteration 1

## Result
**PASS.** I found no blocking findings and nothing to escalate.

## Acceptance criteria
| criterion | status | verified by |
|---|---|---|
| `CityRepository` loads once; later `nearest` calls do not touch SQLite | Met | `CityRepositoryTest` "the table is read once…" (fake `CityDataSource`, `reads == [false]`) and "answers from memory after the copy disappears". Source: `loaded` is cached under the `Mutex`. |
| Indexed nearest equals brute-force nearest on at least 1,000 random points plus edge cases, using the real asset under Robolectric | Met | `CityRepositoryTest` "indexed nearest equals brute force on the real city table": 1,000 points uniform on the sphere, 200 points near cities, 14 edge cases (both poles, ±180, date-line pairs, Point Nemo, Svalbard, Ushuaia). It uses `assertEquals` against `records.minByOrNull { distanceKilometres }`. `CitySpatialIndexTest` adds random, clustered, antimeridian, pole, tie and grid-resolution (1/2/7/64/200 cells) cases. CI is green on ae18bf2 (as reported). |
| `NearbyCityViewModel` ports the `NearbyCityControllerTest` cases (latest fix wins, stale results dropped, retry, unavailable on error) | Met | `NearbyCityViewModelTest`: queued fixes, a `GateDispatcher` stale-lookup test, retry, failed retry, retry with no position, and invalid zone / failed read / empty table giving Unavailable. Also stop-timeout, location-off and registration-failure cases. |
| `NearbyCityCardTest` updated for raw time values, with formatting in the UI | Met | en-GB "10:42 (UTC+02:00)" plus the spoken text; en-US computed with the `FormatStyle.SHORT` formatter; New York shows "19:00 (UTC−05:00)". |
| Row count and timings in the PR description | Pending (orchestrator) | CITY_METRICS rows=47317 loadMs=71.6 median=31.8 µs max=3242.5 µs are printed by the test. I checked by code review only; the PR is not written yet. |

## Blocking findings
None.

## Escalations
None.

## Your specific questions
**Search fixture (the round-1 CI failure).** The fixture was wrong, not the code. "alpha" does not contain "ma", so the old expectation could never hold. "Omaha" and "Gamma" do contain it. `CityRepository.search` (`nearby/data/CityRepository.kt:62-68`) is character-for-character the old `AndroidNearbyCityRepository.searchByName`:
- `query.trim()`
- empty query gives `emptyList()`
- `it.name.trim().contains(normalized, ignoreCase = true)`
- results in id order (`ORDER BY _id`)

The only difference is that the new version does not read the database for a blank query, which is harmless.

**A. `CitySpatialIndex` correctness: sound.**
- **Lower bound for unvisited cells.** Take a query in cell c, so q is in [c·s−1, (c+1)·s−1). A city in a cell more than `ring` cells away along some axis differs from the query by more than ring·s on that axis. So its chord is greater than ring·s.
- **Clamping.** `coerceIn` only moves values at the grid edges. The bound uses the lower edge for cells above and the upper edge for cells below, so clamping does not weaken it.
- **Stopping rule.** The search stops when `bestChord < ring·s − 1e-9`, where `bestChord = chordOf(haversine km)`. Chord grows with great-circle distance up to πR, so every unvisited city is strictly farther than the best one. A tie can therefore only come from a cell already visited. The 1e-9 margin (about 6 mm) is far above rounding error, including near the antipode.
- **Early rejection.** The cheap chord-squared check has a +1e-9 margin, so it only skips cities that are clearly farther.
- **Ties.** On equal distance the lower index wins. Cell member arrays are in ascending order. This reproduces `minByOrNull` order.
- **Termination.** The last ring is `cellsPerAxis−1`, which covers every cell, so `best` is always set when the list is non-empty.
- **Shell enumeration.** `visitShell` has no gaps. At ring 0 it visits the centre cell exactly once.
- **Poles and antimeridian.** No special cases are needed, because the index works with 3D unit vectors.

**B. Repository and asset copy: OK.**
- `CityRepository` reads the table once, under the `Mutex`, inside `withContext(@IoDispatcher)`.
- A failed read leaves `loaded` null, so the next query reads again.
- `reload()` clears the cache, then re-reads with `reextract = true`. Tests cover success, failure, and the next read (`[false, true, false]`).
- `AssetExtractor` is the old `MapArchiveCopier` body, moved: temporary file, validate, atomic move, validate again, delete the temporary file on failure.
- `MapArchiveCopier` now just calls it with the same zip validation. The only change is the rename-fallback error message ("Could not commit offline map archive" becomes "Could not commit <file name>").
- The SQLite header check matches the task (non-empty, 16-byte `SQLite format 3\0`).

**C. ViewModel: consistent with the TASK-009/010 ViewModels.**
- Fixes are gated by `confirmedLocationEnabled` via `flatMapLatest`. `LocationRegistrationException` is caught, as in `FlightParametersViewModel`.
- `stateIn(WhileSubscribed(5_000))`.
- `transformLatest` cancels a running lookup. The blocking read itself cannot be cancelled, but its result is dropped.
- `CancellationException` is rethrown in `lookUp`; other exceptions, including an invalid zone, give Unavailable.
- Retry with no position gives WaitingForPosition and reloads nothing. This matches the old controller.
- A fix that arrives during a pending retry replaces it, so the reload can be lost. The old controller did the same (a newer `pendingRequest` without reload replaced it), so this is parity.

**D. Presentation: matches the task and the original.**
- `Available(zoneId, instant, utcOffsetSeconds)`, with `utcOffsetSeconds = zone.rules.getOffset(instant)`, which equals the old `atZone(zone).offset`. DST and the "UTC+hh:mm" format are kept.
- The card uses `DateTimeFormatter.ofLocalizedTime(SHORT).withLocale(LocalConfiguration.locales[0])`.
- The original (`~/smart-flight/.../avionic/calculators/TimeCalculator.kt:26`) used `DateFormat.getTimeInstance(DateFormat.SHORT)` with the default locale, so this is equivalent.
- The instant is refreshed on every fix, the same as the old per-fix formatting.

**E. Reported behaviour changes: all acceptable.**
1. Observing while STARTED (plus 5 s) instead of RESUMED, and keeping the city across rotation, is the accepted TASK-009/010 pattern, and the characterization test pins rotation. Permission revocation no longer resets the card explicitly; it now follows the same collection lifecycle as the other cards.
2. Using the Compose configuration locale is the intended outcome of F10.
3. Detecting a corrupt copy on first read is an improvement within the task's "corrupt extracted file" risk.
4. Copying the DB to `filesDir/cities/`: the task explicitly allows two data paths to coexist for this task. A separate path is actually safer, because the legacy non-atomic `copyAsset` and the new extractor never write the same file. The roughly 3 MB duplicate is acceptable until TASK-012.

**F. Route: allowed.** Scope bullet 5 allows `RouteController` to stay on `AndroidNearbyCityRepository`. `onLocationFix` still forwards fixes to `routeController.onFix` and to the map. `cityLookupExecutor` stays for the route. `NearbyModule` still binds the legacy interface.

## Non-blocking findings
1. **`CitySpatialIndex.kt:32`:** cities with invalid coordinates are silently dropped. Brute force would throw on NaN, or accept an out-of-range longitude with a finite distance. The results can only differ on invalid rows, and the real-asset test shows the shipped table has none. A one-line KDoc note is enough.
2. **`CityRepositoryTest.kt:~117-121`:** loadMs=71.6 is measured after the preceding `readAll` has already extracted the asset. It covers SQLite read plus index build only, not the first-launch asset copy. It is also Robolectric SQLite on the CI JVM. The PR should label it that way.
3. Nothing deletes the legacy `filesDir/nearby-city/cities_info.db` after the route migration. TASK-012 does not mention it; suggest adding the cleanup there.
4. `MapArchiveCopier` rename-fallback message changed (see B). Cosmetic.

## Human checks on device
- Nearby city card shows the correct city, distance with units, and local time "hh:mm (UTC±hh:mm)" in the device/app locale; also with a 12-hour locale.
- First launch on a clean install: the card fills without a noticeable delay (asset extraction plus about 47k-row load).
- Rotation keeps the city. Background for more than 5 s and return: the card restarts from "Waiting for GPS position…" and then fills.
- "Try again" after an Unavailable state recovers.
- Location switched off keeps the last city; switched back on, it updates.
- Route search and endpoints still work (legacy path).
- Upgrade over the previous build: both `nearby-city/` and `cities/` copies exist, with no errors.

## Verification performed
- **Source review:** full diff `origin/ai-modernization...HEAD` (5 commits). I read all new and changed main files, all touched test files, the legacy `AndroidNearbyCityRepository`, the base `MainActivity` lifecycle, `FlightParametersViewModel`, `LocationRepository`, and the original `TimeCalculator.kt`. I worked through the spatial-index bound analytically.
- **CI result as reported:** PASS on ae18bf2 (run 36570946080). Round 1 on 3b59791 failed only because of the search fixture, now fixed test-only. I did not re-run or inspect CI logs myself.
- **Not verified:** runtime behaviour on a device, actual first-launch extraction time, memory footprint, the Hilt graph at runtime, and the PR description contents. I have not verified that anything works at runtime.

Relevant files:
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/nearby/data/CitySpatialIndex.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/nearby/data/CityRepository.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/nearby/data/CityDataSource.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/data/AssetExtractor.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/nearby/ui/NearbyCityViewModel.kt
- /home/ai-dev/smart-flight-2-modern/app/src/main/java/kniezrec/com/flightinfo/ui/gnss/NearbyCityCard.kt
- /home/ai-dev/smart-flight-2-modern/app/src/test/java/kniezrec/com/flightinfo/nearby/data/CityRepositoryTest.kt
