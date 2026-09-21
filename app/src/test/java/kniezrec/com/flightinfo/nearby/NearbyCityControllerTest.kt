package kniezrec.com.flightinfo.nearby

import kniezrec.com.flightinfo.flight.FlightLocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.concurrent.Executor

class NearbyCityControllerTest {
    private val direct = Executor { it.run() }

    @Test fun `valid current fix produces nearest city and invalid coordinates are ignored`() {
        val states = mutableListOf<NearbyCityState>()
        val controller = controller(states, listOf(city(1, "Near", 0.0, 0.0), city(2, "Far", 40.0, 40.0)))
        controller.start()
        controller.onLocationFix(FlightLocationFix(null, null, 1, latitude = Double.NaN, longitude = 0.0))
        assertEquals(NearbyCityState.WaitingForPosition, states.last())
        controller.onLocationFix(FlightLocationFix(null, null, 2, latitude = 0.1, longitude = 0.0))
        assertEquals("Near", (states.last() as NearbyCityState.Available).cityName)
    }

    @Test fun `stopped session and older result cannot replace current state`() {
        val queued = mutableListOf<Runnable>(); val worker = Executor { queued += it }; val states = mutableListOf<NearbyCityState>()
        val controller = NearbyCityController(FakeRepository(listOf(city(1, "City", 0.0, 0.0))), worker, direct, { Instant.EPOCH }, states::add)
        controller.start(); controller.onLocationFix(FlightLocationFix(null, null, 1, latitude = 0.0, longitude = 0.0)); controller.stop()
        queued.forEach(Runnable::run)
        assertEquals(NearbyCityState.WaitingForPosition, states.last())
    }

    @Test fun `distance uses geographic calculation`() {
        assertTrue(distanceKilometres(NearbyCoordinate(0.0, 0.0), NearbyCoordinate(0.0, 1.0)) in 111.0..112.0)
    }

    private fun controller(states: MutableList<NearbyCityState>, cities: List<NearbyCityRecord>) = NearbyCityController(FakeRepository(cities), direct, direct, { Instant.EPOCH }, states::add)
    private fun city(id: Long, name: String, latitude: Double, longitude: Double) = NearbyCityRecord(id, name, "Country", latitude, longitude, "UTC")
    private class FakeRepository(private val cities: List<NearbyCityRecord>) : NearbyCityRepository {
        override fun findNearest(position: NearbyCoordinate, reload: Boolean): NearbyCityRecord? = cities.minByOrNull { distanceKilometres(position, NearbyCoordinate(it.latitude, it.longitude)) }
    }
}
