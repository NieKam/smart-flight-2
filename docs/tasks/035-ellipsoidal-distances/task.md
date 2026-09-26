# TASK-035 — Displayed distances on the WGS84 ellipsoid

## Goal
Compute displayed distances (nearby city distance, route distance between cities, distance to destination) on the WGS84 ellipsoid like the original, instead of a spherical haversine.

## Context
- Parity finding 26 (REGRESSION, LOW): original uses `Location.distanceBetween` (WGS84) in `~/smart-flight/app/src/main/java/kniezrec/com/flightinfo/avionic/calculators/DistanceCalculator.kt:81-89`; rewrite uses spherical haversine with R = 6371.0088 km (`nearby/NearbyCityController.kt:189-201` → `distanceKilometres`, kept as pure function by TASK-011). Long routes can differ by up to ~0.5% (tens of km on 9,156 km).
- Architecture review K2 (KEEP): distance functions are pure and JVM-tested; `android.location.Location` would break that. Use a pure Kotlin implementation.
- Parity MODERNIZATION (accepted, keep): nearest city chosen by true distance. Nearest-city ranking may keep haversine (ranking is insensitive to the ~0.5% error except in near-ties); displayed distances use the ellipsoid.

## Dependencies
- TASK-021 (route details signature), TASK-011.

## Original app reference
- `avionic/calculators/DistanceCalculator.kt`.

## Scope
- Pure `ellipsoidalDistanceKm(a, b)` using Vincenty's inverse formula with a fallback (e.g. haversine) for the rare non-converging near-antipodal case, or Karney's algorithm if a small, dependency-free implementation is preferred.
- Use it for all displayed distances (nearby card, route fixed distance, remaining distance, ETA computation).

## Out of scope
- Changing nearest-city selection (keep haversine in the spatial index unless trivially swappable).

## Requirements
Required:
- Results within 1 m (or 1e-6 relative) of reference values for known pairs; defined output for antipodal/identical points.

## Acceptance criteria
- [ ] Tests against reference pairs (e.g. Frankfurt–San Francisco, pole-to-pole, equatorial, identical points, near-antipodal) with values taken from a trusted source (GeographicLib online calculator or `Location.distanceBetween` under Robolectric if it uses the real algorithm) — verified by: CI unit test
- [ ] All displayed-distance call sites use the new function — verified by: code review

## Tests to add or update
- `EllipsoidalDistanceTest`; update `RouteModelsTest`, `NearbyCityViewModelTest` expected values.

## Risks and edge cases
- Vincenty non-convergence near antipodes: fallback must be tested.
