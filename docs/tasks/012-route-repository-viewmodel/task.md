# TASK-012 — Route repository and Route card ViewModel

## Goal
Split `RouteController` into a route repository (persisted endpoint IDs as a Flow) and a `RouteViewModel` that combines the repository, the city repository and the location Flow into `RouteState`. Remove `android.content.SharedPreferences` from logic classes and move formatting of arrival/duration to the UI. No user-visible behavior change.

## Context
- Architecture review F8 (VIOLATION, MEDIUM), verified: `route/RouteController.kt:3,13` takes `SharedPreferences` and persists directly (`:140-141,162-181`); it is repository, state holder and search dispatcher at once; its test hand-rolls a full SharedPreferences fake (`RouteControllerTest.kt:194-290`); `start()` reads prefs on the worker thread while `persist()` writes (with `commit()`) on the main thread (architecture "Not verified" 6: thread-safety).
- Architecture review F10: `RouteDetails.arrival`/`duration` are formatted strings built in the model with `Locale.getDefault()` (`route/RouteModels.kt:94-107`). Direction: keep `Instant`/`ZoneId`/`Duration` in state, format in composables.
- Architecture review K2 (KEEP): `routeDetails`, `routeOverlay`, `validCity` (`route/RouteModels.kt`) are pure functions with tests; keep them pure (adapt signatures for raw values).
- Architecture review K8 (KEEP): injected clock.
- Parity MODERNIZATION (accepted, keep): arrival shown as a localized date-time in the destination zone plus "HH:MM" duration; route distance between cities; clear one endpoint / clear all.
- Current behavior to preserve (changed later): `details` only when BOTH departure and destination are set (`RouteController.kt:186-199`) — TASK-021 changes it.
- Persistence: file `route`, keys `route_departure_id`, `route_destination_id` (`Long`, `Long.MIN_VALUE` = unset). Restore error → `RouteError.RESTORE` with retry (`retryRestore`, `:158-160`). Invalid restored city (bad coordinates or zone) is dropped and persisted as removed (`:146-150`).
- `RouteController.onFix` ignores fixes whose `elapsedRealtimeNanos` is not newer than the last accepted one (`:44-53`).

## Dependencies
- TASK-011 (`CityRepository`), TASK-006 (repository conventions).

## Target conventions (from CLAUDE.md)
Repositories expose `Flow`/`suspend` and own their storage; ViewModel exposes one `StateFlow`; no Android types in logic; injected dispatchers and clock; package by feature with layers (`route/data`, `route/ui`).

## Scope
- `RouteRepository` (`@Singleton`): `val endpoints: StateFlow<RouteEndpointIds>` (departureId?, destinationId?) from SharedPreferences `route` via change listener; `suspend fun set(endpoint, id)`, `suspend fun clear(endpoint)`, `suspend fun clearAll()` using `apply()`.
- `RouteViewModel`: `endpoints` → resolve cities via `CityRepository.byId` (restore error → `RouteError.RESTORE`, `retryRestore()` reloads) → combine with latest valid, monotonic fix from `LocationRepository` → `RouteState` using the pure `routeDetails`/`routeOverlay`. Actions: `choose(endpoint, city): Boolean` (validates with `validCity`), `clear(endpoint)`, `clearAll()`.
- `RouteDetails`: raw values (`fixedDistanceKm`, `remainingDistanceKm?`, `arrival: Instant?`, `destinationZone: ZoneId`, `duration: Duration?`); `RouteCard` formats (same visible output as today: localized SHORT date-time in the destination zone, "%02d:%02d" duration).
- The route picker still uses the search/nearest code paths; move `search`/`nearest` calls to `CityRepository` through the ViewModel (a minimal `search(query)` / `nearest(coordinate)` API on `RouteViewModel` or a temporary Activity call) — the full picker state holder is TASK-013.
- Delete `RouteController`, `AndroidNearbyCityRepository`, the old `NearbyCityRepository` interface if unused, and `RouteControllerTest`'s SharedPreferences fake. Port all cases.
- `MainActivity`: remove `routeController`, `routePreferences`, `cityLookupExecutor` if no longer used.

## Out of scope
- Picker state, typed picker errors, nearest-draft bug (TASK-013). Destination-only details (TASK-021). Route card visuals (TASK-033).

## Requirements
Required:
- Same keys, same restore/drop-invalid semantics, same visible output.
- No `SharedPreferences` in `RouteViewModel` or pure functions.
- Writes never block the main thread.

## Acceptance criteria
- [ ] `RouteRepository` round trip, change emission, clear one/all — verified by: CI unit test (Robolectric SharedPreferences)
- [ ] `RouteViewModel` ports every `RouteControllerTest` case (restore, invalid drop, restore error + retry, choose/clear, fix monotonicity, details only with both endpoints) — verified by: CI unit test
- [ ] `RouteModelsTest` updated for raw values; formatting tested in a Compose/Robolectric test of `RouteCard` with a fixed locale — verified by: CI unit test
- [ ] TASK-004 scenario 7 (route restored after launch) passes — verified by: CI unit test

## Tests to add or update
- New: `RouteRepositoryTest`, `RouteViewModelTest`, `RouteCardTest` (formatting).
- Update: `RouteModelsTest`. Delete: `RouteControllerTest` after porting.

## Risks and edge cases
- Endpoint set while restore is in flight: the latest write must win (`flatMapLatest` on endpoints).
- City IDs stored as `Long`; the original app stored `Int` in another file (`LocalPrefs`) — migration is TASK-036.
