package kniezrec.com.flightinfo.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Reference distances from the GeographicLib online geodesic calculator (GeodSolve, WGS84:
 * a = 6378137 m, f = 1/298.257223563), in metres.
 */
class EllipsoidalDistanceTest {
    @Test fun `Frankfurt to San Francisco airports`() {
        assertDistance(9_171_213.457583, 50.0379, 8.5622, 37.6213, -122.379)
    }

    @Test fun `Sydney to London Heathrow`() {
        assertDistance(17_011_038.364536, -33.8688, 151.2093, 51.47, -0.4543)
    }

    @Test fun `Warsaw to New York`() {
        assertDistance(6_872_969.752030, 52.2297, 21.0122, 40.7128, -74.006)
    }

    @Test fun `one degree along the equator`() {
        assertDistance(111_319.490793, 0.0, 0.0, 0.0, 1.0)
    }

    @Test fun `one degree along a meridian from the equator`() {
        assertDistance(110_574.388558, 0.0, 0.0, 1.0, 0.0)
    }

    @Test fun `crossing the antimeridian takes the short way`() {
        assertDistance(111_319.490793, 0.0, 179.5, 0.0, -179.5)
        assertDistance(111_319.490793, 0.0, -179.5, 0.0, 179.5)
    }

    @Test fun `pole to pole is half a meridian`() {
        assertDistance(20_003_931.458625, 90.0, 0.0, -90.0, 0.0)
        assertDistance(20_003_931.458625, -90.0, 10.0, 90.0, -170.0)
    }

    @Test fun `exact equatorial antipodes go over a pole`() {
        assertDistance(20_003_931.458625, 0.0, 0.0, 0.0, 180.0)
    }

    @Test fun `identical points are zero`() {
        assertDistance(0.0, 10.0, 20.0, 10.0, 20.0)
        assertDistance(0.0, 0.0, 0.0, 0.0, 0.0)
        // The same pole written with different longitudes.
        assertDistance(0.0, 90.0, 0.0, 90.0, 45.0)
    }

    @Test fun `distance is symmetric`() {
        val there = ellipsoidalDistanceKm(NearbyCoordinate(50.0379, 8.5622), NearbyCoordinate(37.6213, -122.379))
        val back = ellipsoidalDistanceKm(NearbyCoordinate(37.6213, -122.379), NearbyCoordinate(50.0379, 8.5622))
        assertEquals(there, back, TOLERANCE_KM)
    }

    @Test fun `near antipodal pairs where Vincenty does not converge fall back to the sphere`() {
        listOf(
            // GeographicLib: 19980861.908891 m.
            Triple(NearbyCoordinate(0.0, 0.0), NearbyCoordinate(0.0, 179.5), 19_980.861908891),
            // GeographicLib: 19944127.420750 m.
            Triple(NearbyCoordinate(0.0, 0.0), NearbyCoordinate(0.5, 179.7), 19_944.127420750),
        ).forEach { (first, second, referenceKm) ->
            val distance = ellipsoidalDistanceKm(first, second)
            assertTrue("finite: $distance", distance.isFinite())
            assertEquals(distanceKilometres(first, second), distance, 0.0)
            assertTrue("within 0.5% of $referenceKm: $distance", abs(distance - referenceKm) / referenceKm < 0.005)
        }
    }

    private fun assertDistance(
        expectedMetres: Double,
        firstLatitude: Double,
        firstLongitude: Double,
        secondLatitude: Double,
        secondLongitude: Double,
    ) {
        assertEquals(
            expectedMetres / 1000.0,
            ellipsoidalDistanceKm(NearbyCoordinate(firstLatitude, firstLongitude), NearbyCoordinate(secondLatitude, secondLongitude)),
            TOLERANCE_KM,
        )
    }

    private companion object {
        /** 1 cm, well inside the 1 m requirement. */
        const val TOLERANCE_KM = 0.00001
    }
}
