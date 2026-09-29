package kniezrec.com.flightinfo.nearby.data

import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.NearbyCoordinate
import kniezrec.com.flightinfo.nearby.distanceKilometres
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.random.Random

class CitySpatialIndexTest {
    @Test fun `no cities gives no nearest city`() {
        assertNull(CitySpatialIndex(emptyList()).nearest(NearbyCoordinate(0.0, 0.0)))
    }

    @Test fun `a single city is nearest from anywhere, including its antipode`() {
        val only = city(1, 10.0, 20.0)
        val index = CitySpatialIndex(listOf(only))

        assertSame(only, index.nearest(NearbyCoordinate(-10.0, -160.0)))
        assertSame(only, index.nearest(NearbyCoordinate(90.0, 0.0)))
    }

    @Test fun `random cities match brute force for random points`() {
        val random = Random(20260929)
        val cities = List(3_000) { city(it.toLong(), randomLatitude(random), randomLongitude(random)) }
        val index = CitySpatialIndex(cities)
        repeat(2_000) {
            val position = NearbyCoordinate(randomLatitude(random), randomLongitude(random))
            assertSame("at $position", bruteForce(cities, position), index.nearest(position))
        }
    }

    @Test fun `clustered sparse cities match brute force far from every cluster`() {
        val random = Random(7)
        val cities =
            List(500) {
                // Two dense clusters, with open ocean everywhere else.
                if (it % 2 == 0) {
                    city(it.toLong(), 50.0 + random.nextDouble(), 10.0 + random.nextDouble())
                } else {
                    city(it.toLong(), -33.0 + random.nextDouble(), 151.0 + random.nextDouble())
                }
            }
        val index = CitySpatialIndex(cities)
        repeat(500) {
            val position = NearbyCoordinate(randomLatitude(random), randomLongitude(random))
            assertSame("at $position", bruteForce(cities, position), index.nearest(position))
        }
    }

    @Test fun `antimeridian neighbours are found across the date line`() {
        val east = city(1, 0.0, 179.9)
        val west = city(2, 0.0, -179.85)
        val farWest = city(3, 0.0, 170.0)
        val index = CitySpatialIndex(listOf(farWest, west, east))

        assertSame(west, index.nearest(NearbyCoordinate(0.0, -179.95)))
        assertSame(east, index.nearest(NearbyCoordinate(0.0, 179.99)))
        assertSame(east, index.nearest(NearbyCoordinate(0.0, 180.0)))
        assertSame(west, index.nearest(NearbyCoordinate(0.0, -179.81)))
    }

    @Test fun `near the poles longitude hardly matters`() {
        val north = city(1, 89.9, -120.0)
        val arctic = city(2, 85.0, 60.0)
        val south = city(3, -89.5, 45.0)
        val cities = listOf(north, arctic, south)
        val index = CitySpatialIndex(cities)

        assertSame(north, index.nearest(NearbyCoordinate(90.0, 0.0)))
        assertSame(north, index.nearest(NearbyCoordinate(89.95, 60.0)))
        assertSame(south, index.nearest(NearbyCoordinate(-90.0, 180.0)))
        val random = Random(3)
        repeat(300) {
            val position =
                NearbyCoordinate(
                    if (it % 2 ==
                        0
                    ) {
                        80 + random.nextDouble() * 10
                    } else {
                        -80 - random.nextDouble() * 10
                    },
                    randomLongitude(random),
                )
            assertSame("at $position", bruteForce(cities, position), index.nearest(position))
        }
    }

    @Test fun `equally distant cities resolve to the first in list order, like brute force`() {
        val first = city(5, 0.0, 1.0)
        val duplicate = city(6, 0.0, 1.0)
        val mirrored = city(7, 0.0, -1.0)
        val cities = listOf(first, duplicate, mirrored)

        assertSame(first, CitySpatialIndex(cities).nearest(NearbyCoordinate(0.0, 0.0)))
        assertSame(mirrored, CitySpatialIndex(listOf(mirrored, first)).nearest(NearbyCoordinate(0.0, 0.0)))
        assertSame(first, CitySpatialIndex(cities).nearest(NearbyCoordinate(0.0, 1.0)))
    }

    @Test fun `grid resolution does not change the answer`() {
        val random = Random(11)
        val cities = List(800) { city(it.toLong(), randomLatitude(random), randomLongitude(random)) }
        val indexes = listOf(1, 2, 7, 64, 200).map { CitySpatialIndex(cities, cellsPerAxis = it) }
        repeat(300) {
            val position = NearbyCoordinate(randomLatitude(random), randomLongitude(random))
            val expected = bruteForce(cities, position)
            indexes.forEach { assertSame("at $position", expected, it.nearest(position)) }
        }
    }

    @Test fun `cities with invalid coordinates are ignored`() {
        val valid = city(2, 45.0, 45.0)
        val index = CitySpatialIndex(listOf(city(1, Double.NaN, 0.0), city(3, 0.0, 200.0), valid))

        assertEquals(valid, index.nearest(NearbyCoordinate(0.0, 0.0)))
    }

    private fun bruteForce(
        cities: List<NearbyCityRecord>,
        position: NearbyCoordinate,
    ): NearbyCityRecord? = cities.minByOrNull { distanceKilometres(position, NearbyCoordinate(it.latitude, it.longitude)) }

    // Uniform on the sphere, so polar regions get their share of points.
    private fun randomLatitude(random: Random): Double = Math.toDegrees(kotlin.math.asin(random.nextDouble(-1.0, 1.0)))

    private fun randomLongitude(random: Random): Double = random.nextDouble(-180.0, 180.0)

    private fun city(
        id: Long,
        latitude: Double,
        longitude: Double,
    ) = NearbyCityRecord(id, "City $id", "Country", latitude, longitude, "UTC")
}
