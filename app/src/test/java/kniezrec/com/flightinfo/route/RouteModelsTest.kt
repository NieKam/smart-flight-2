package kniezrec.com.flightinfo.route

import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.NearbyCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class RouteModelsTest {
    private val departure = NearbyCityRecord(1, " Alpha ", "A", 0.0, 0.0, "UTC")
    private val destination = NearbyCityRecord(2, "Beta", "B", 0.0, 1.0, "Europe/Berlin")

    @Test fun `identical endpoints have zero distance`() {
        assertEquals(0.0, routeDistance(departure, departure), 0.0001)
    }

    @Test fun `invalid speed leaves live arrival unavailable`() {
        val details = routeDetails(departure, destination, RouteFix(NearbyCoordinate(0.0, 0.0), 0.0), Instant.EPOCH)
        assertTrue(details.fixedDistanceKm > 0.0)
        assertNull(details.arrival)
        assertNull(details.duration)
        assertEquals(ZoneId.of("Europe/Berlin"), details.destinationZone)
    }

    @Test fun `no fix leaves remaining distance and arrival unavailable`() {
        val details = routeDetails(departure, destination, null, Instant.EPOCH)
        assertEquals(111.2, details.fixedDistanceKm, 0.1)
        assertNull(details.remainingDistanceKm)
        assertNull(details.arrival)
        assertNull(details.duration)
    }

    @Test fun `positive speed computes remaining distance and destination local arrival`() {
        val details =
            routeDetails(departure, destination, RouteFix(NearbyCoordinate(0.0, 0.0), 100.0), Instant.parse("2020-01-01T00:00:00Z"))
        assertTrue(details.remainingDistanceKm!! > 100.0)
        // 111.19 km at 100 m/s: 1112 s, rounded to whole seconds.
        assertEquals(Duration.ofSeconds(1_112), details.duration)
        assertEquals(Instant.parse("2020-01-01T00:18:32Z"), details.arrival)
        assertEquals(ZoneId.of("Europe/Berlin"), details.destinationZone)
    }

    @Test fun `overlay keeps endpoint names for accessible map summary`() {
        val overlay = routeOverlay(departure, destination)
        assertEquals(" Alpha ", overlay?.departureName)
        assertEquals("Beta", overlay?.destinationName)
    }

    @Test fun `invalid long press coordinate is rejected`() {
        assertNull(NearbyCoordinate.from(Double.NaN, 0.0))
        assertNull(NearbyCoordinate.from(91.0, 0.0))
        assertNull(NearbyCoordinate.from(0.0, 181.0))
    }

    @Test fun `invalid city cannot produce overlay`() {
        assertNull(routeOverlay(departure, destination.copy(latitude = 91.0)))
    }
}
