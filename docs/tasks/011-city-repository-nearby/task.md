# TASK-011 — City repository (load once, fast nearest lookup) and Nearby city ViewModel

## Goal
Create one application-scoped city repository with `suspend` queries that reads the city database once and answers nearest-city lookups without full-table I/O per GPS fix. Move the Nearby city card to a ViewModel that consumes the location Flow. Formatting of local time moves to the UI.

## Context
- Parity review finding 13 (REGRESSION, MEDIUM) and architecture review F9 (VIOLATION, MEDIUM), verified: every `findNearest`/`searchByName`/`findById` call in `nearby/AndroidNearbyCityRepository.kt:26-54` stats the file, opens SQLite and reads the whole `cities_info` table into a list; `NearbyCityController` calls `findNearest` for each accepted fix (1 Hz, `flight/AndroidFlightLocationPlatform.kt:41`), limited only by request collapsing (`nearby/NearbyCityController.kt:130-163`). The original built an in-memory R-tree once (`~/smart-flight/.../services/city/FindCityHelper.kt:17-62`).
- F9 also: the `NearbyCityRepository` interface uses `@Throws(Exception::class)` and default no-op implementations (`NearbyCityController.kt:53-69`) only so fakes can skip methods; two asset-copy strategies exist (`AndroidNearbyCityRepository.copyAsset` with `renameTo` into `filesDir`, vs. `map/MapArchiveCopier.copy` with atomic move).
- Architecture review K7 (KEEP): `MapArchiveCopier` copies atomically, validates and never exposes a partial destination; it should become the shared helper.
- Architecture review F10: `NearbyCityState.Available.localTime` is a string formatted in the controller with `Locale.getDefault()` (`NearbyCityController.kt:171`). Direction: state keeps `Instant`/`ZoneId`; format at the UI edge.
- Architecture review K2/K8 (KEEP): `distanceKilometres`, `NearbyCoordinate.from` are pure; keep the injected clock.
- Parity review MODERNIZATION (accepted, keep): nearest city chosen by true (haversine) distance instead of planar lat/lng; local time uses the `ZoneId` offset (DST bug fixed), displayed as "UTC+02:00".
- Parity "Not verified" / architecture "Not verified" 3: the row count and per-lookup cost of `cities_info.db` were not measured.
- DB schema (original `~/smart-flight/app/src/main/java/kniezrec/com/flightinfo/db/CitiesTable.kt`): table `cities_info(_id, city, latitude, longitude, timezone, country, gmt_offset)`.

## Dependencies
- TASK-008 (location Flow), TASK-005.

## Target conventions (from CLAUDE.md)
Repository with `suspend` functions on an injected IO dispatcher, single source of truth, `@Singleton`; ViewModel exposes `StateFlow`; package by feature with layers (`city/data` or `nearby/data`, `nearby/ui`).

## Scope
- `AssetExtractor` (generalized from `MapArchiveCopier`, K7): copy an asset to a destination atomically with a validation lambda; reuse for the city DB now (validation: non-empty file whose first 16 bytes are the SQLite header `SQLite format 3\u0000`). `MapArchiveCopier` becomes a thin caller or is replaced; its tests keep passing.
- `CityRepository` (`@Singleton`, replaces `NearbyCityRepository` interface without default no-ops):
  - `suspend fun nearest(position: NearbyCoordinate): NearbyCityRecord?`
  - `suspend fun search(query: String): List<NearbyCityRecord>` (same matching rule as today: trimmed, case-insensitive "contains" on name).
  - `suspend fun byId(id: Long): NearbyCityRecord?`
  - `suspend fun reload()` (re-extract the asset; today's `reload = true` on retry).
  - Load all rows once (on `@IoDispatcher`, guarded by a `Mutex`) and keep them in memory; build a simple spatial index (e.g. latitude/longitude grid buckets searched in growing rings, then exact haversine) so a nearest query does not scan every row. Result must be identical to brute-force haversine minimum.
- `NearbyCityViewModel`: `LocationRepository.fixes` → valid coordinate → `mapLatest { cityRepository.nearest(it) }` (or `conflate()` + `map`) → `NearbyCityState`. States as today: `WaitingForPosition`, `LookingUp`, `Available`, `Unavailable`; `retry()` reloads the asset and re-queries the latest position.
- `NearbyCityState.Available`: replace `localTime: String` with raw values (e.g. `zoneId: ZoneId`, `instant: Instant` from the injected clock) and keep `utcOffsetSeconds`; `NearbyCityCard` formats time with the current locale.
- `MainActivity`: remove `nearbyCityController` and its fan-out; pass the ViewModel state to the screen.
- `RouteController` keeps using `AndroidNearbyCityRepository` until TASK-012 (two data paths coexist for one task; TASK-012 deletes the old one). Alternatively, if trivial, make `RouteController` call the new repository with the same threading; do not migrate `RouteController` itself here.

## Out of scope
- Route (TASK-012/013). Ellipsoidal distances (TASK-035).

## Requirements
Required:
- No SQLite open or table read per fix after the first load.
- Nearest result equals brute-force haversine minimum for all test points (including near poles and across the antimeridian).
- Visible card behavior unchanged (texts, "UTC+hh:mm" offset format, distance with units).
- Measure and report in the PR: row count of `cities_info.db`, one-time load time, and median lookup time on the CI JVM (printed by a test; no timing assertion that could flake, except a generous upper bound such as 50 ms per lookup).

## Acceptance criteria
- [ ] `CityRepository` loads once; subsequent `nearest` calls do not touch SQLite — verified by: CI unit test (counting data-source reads with a fake loader)
- [ ] Indexed nearest == brute-force nearest on ≥ 1,000 random points plus edge cases, using the real asset under Robolectric — verified by: CI unit test
- [ ] `NearbyCityViewModel` ports `NearbyCityControllerTest` cases (latest fix wins, stale results dropped, retry, unavailable on error) — verified by: CI unit test
- [ ] `NearbyCityCardTest` updated for raw time values, formatting in the UI — verified by: CI unit test
- [ ] Row count and timings in PR description — verified by: code review

## Tests to add or update
- New: `CityRepositoryTest`, `CitySpatialIndexTest`, `AssetExtractorTest`, `NearbyCityViewModelTest`.
- Update: `MapArchiveCopierTest` (if refactored), `NearbyCityCardTest`. Delete `NearbyCityControllerTest` after porting.

## Risks and edge cases
- Memory: the whole table in memory; check row count first. If it is very large (> 500k rows), prefer an SQL bounding-box query with an index-less but narrowed `WHERE latitude BETWEEN … AND longitude BETWEEN …` instead of full caching; document the decision.
- Antimeridian and poles in the grid search.
- Corrupt extracted file: `reload()` must re-extract and validate.
