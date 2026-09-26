# TASK-007 — Location and GNSS repository with callbackFlow; the foreground service collects it

## Goal
Create one application-scoped source of truth for GPS fixes, GNSS satellite status and "location services enabled", built on `callbackFlow`. Switch `LocationForegroundService` from `LocationGnssMonitoringSession` to collecting this repository. The Activity still receives data through `BackgroundMonitoringBridge` in this task (the bridge is removed in TASK-008). No behavior change.

## Context
- Architecture review F4 (VIOLATION, HIGH): callback/Executor async model; `FlightLocationPlatform.registerLocationListener` (`flight/FlightParametersController.kt:9`), `GnssStatusPlatform.registerGnssStatusCallback` (`gnss/GnssStatusController.kt:10`); hand-rolled generation tokens (`LocationGnssMonitoringSession.kt:14`). Direction: each `Android*Platform` becomes a `callbackFlow` data source with unregistration in `awaitClose`.
- Architecture review F3 (VIOLATION, HIGH): location and GNSS registrations exist twice (service `LocationForegroundService.kt:200-216`, Activity `MainActivity.kt:492,499`). Direction: one application-scoped repository shared with `shareIn`/`stateIn(WhileSubscribed)`; the service stays the owner of background lifetime and notification.
- Architecture review K1 (KEEP): keep the Platform-interface seam; turn the interfaces into `callbackFlow`-based data-source interfaces, do not delete them.
- Architecture review K6 (KEEP): the foreground service as owner of background monitoring, eligibility checks and notification logic is correct; keep the service, change what it collects from.
- Observed: `AndroidFlightLocationPlatform` requests updates every 1 s (`flight/AndroidFlightLocationPlatform.kt:41`); keep that request configuration.
- Observed: service start fails and stops itself if location services are disabled, there is no GNSS hardware, or registration fails (`LocationGnssMonitoringSession.start()` returns false; `LocationForegroundService.kt:76-83`).

## Dependencies
- TASK-005 (dispatchers, `@ApplicationScope`, `LocationManager` injection).

## Target conventions (from CLAUDE.md)
Package by feature with layer sub-packages; repositories expose `Flow`/`suspend`; Android callbacks wrapped with `callbackFlow` + `awaitClose`; injectable dispatchers; structured concurrency (no `GlobalScope`); Android types stay in data-source implementations.

## Scope
- Data-source interfaces (evolved from `FlightLocationPlatform` / `GnssStatusPlatform`, K1), e.g. in `location/data/`:
  - `fun locationFixes(): Flow<FlightLocationFix>`; `fun satellites(): Flow<List<GnssSatellite>>`; `fun isLocationEnabled(): Boolean`; `fun hasGnssHardware(): Boolean`; `fun locationEnabledChanges(): Flow<Boolean>` (`PROVIDERS_CHANGED_ACTION` receiver via `callbackFlow`).
  - Registration failure (returns false or throws `SecurityException`/`RuntimeException`) closes the flow with a typed exception (e.g. `LocationRegistrationException`), so consumers can react.
  - Android implementations reuse the code in `AndroidFlightLocationPlatform` and `AndroidGnssStatusPlatform` (same request parameters, same satellite mapping including `signalStrengthDbHz`).
- `LocationRepository` (`@Singleton`): `val fixes: SharedFlow<FlightLocationFix>`, `val satellites: SharedFlow<List<GnssSatellite>>` via `shareIn(@ApplicationScope, SharingStarted.WhileSubscribed(), replay = 1 or 0 — choose and justify)`, `val locationEnabled: StateFlow<Boolean>`, and the hardware check. Exactly one platform registration exists no matter how many collectors.
- `LocationForegroundService`: replace `createMonitoringSession(generation)` with collection of the repository flows in a service-owned `CoroutineScope(SupervisorJob() + main dispatcher)` cancelled in `stopMonitoring()`/`onDestroy`. Forward to `BackgroundMonitoringBridge.forwardLocation/forwardGnssStatus` exactly as today. Registration failure → same stop behavior as today. Keep a test seam replacing the repository (constructor-injected fake or `protected open` factory).
- Delete `LocationGnssMonitoringSession` and `MonitoringSession` if no longer used, and `LocationGnssMonitoringSessionTest` (port its meaningful cases to repository/data-source tests).
- Keep `AndroidFlightLocationPlatform`/`AndroidGnssStatusPlatform` only if the Activity's (dead) foreground controllers still need them to compile; they are deleted in TASK-008.

## Out of scope
- Removing `BackgroundMonitoringBridge` and changing how the Activity gets data (TASK-008).
- GPS-disabled UI (TASK-019).

## Requirements
Required:
- Behavior identical for the user: same data on the dashboard, same service lifetime rules (stop on first usable fix in background, stop when ineligible, notification texts).
- One `LocationManager` registration per data type while the service runs.
- When the last collector stops, the platform listener is unregistered (`awaitClose`).
- No `GlobalScope`; no raw threads.

Recommendations:
- Use `callbackFlow` with `trySend` and `awaitClose { unregister }`; use `conflate()` or a small buffer for satellites.

## Acceptance criteria
- [ ] Data sources are `callbackFlow`-based; registration happens on first collection and unregistration on cancellation — verified by: CI unit test (Robolectric `ShadowLocationManager`: listener count 1 with two collectors, 0 after cancel)
- [ ] Registration failure propagates as an exception/typed signal — verified by: CI unit test with a fake that fails
- [ ] `LocationForegroundServiceTest` and `LocationForegroundServiceEligibilityTest` pass (updated to the new seam) — verified by: CI unit test
- [ ] TASK-004 characterization tests pass unchanged — verified by: CI unit test
- [ ] Background notification + first-fix stop still work on a device — verified by: HUMAN on device

## Tests to add or update
- New: `LocationRepositoryTest` (fake data source, `runTest`, shared registration, replay semantics), Robolectric tests for the Android data sources.
- Update: `LocationForegroundServiceTest`, `LocationForegroundServiceEligibilityTest`. Delete `LocationGnssMonitoringSessionTest` after porting its cases.

## Risks and edge cases
- `shareIn` with `WhileSubscribed()` and replay: a replayed stale fix could be delivered to a new collector after a long pause. Prefer `replay = 0` for fixes unless a consumer needs the last value; document the choice.
- Location callbacks must be delivered on a looper; keep `mainExecutor`/main looper for registration as today.
- Thread-safety: flows remove the unsynchronized shared state of `LocationGnssMonitoringSession`.
