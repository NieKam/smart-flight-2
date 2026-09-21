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
        val queued = mutableListOf<Runnable>()
        val worker = Executor { queued += it }
        val states = mutableListOf<NearbyCityState>()
        val controller =
            NearbyCityController(FakeRepository(listOf(city(1, "City", 0.0, 0.0))), worker, direct, { Instant.EPOCH }, states::add)
        controller.start()
        controller.onLocationFix(FlightLocationFix(null, null, 1, latitude = 0.0, longitude = 0.0))
        controller.stop()
        queued.forEach(Runnable::run)
        assertEquals(NearbyCityState.WaitingForPosition, states.last())
    }

    @Test fun `distance uses geographic calculation`() {
        assertTrue(distanceKilometres(NearbyCoordinate(0.0, 0.0), NearbyCoordinate(0.0, 1.0)) in 111.0..112.0)
    }

    @Test fun `newest queued fix replaces a superseded lookup`() {
        val queued = mutableListOf<Runnable>()
        val positions = mutableListOf<NearbyCoordinate>()
        val worker = Executor { queued += it }
        val repository =
            object : NearbyCityRepository {
                override fun findNearest(
                    position: NearbyCoordinate,
                    reload: Boolean,
                ): NearbyCityRecord? {
                    positions += position
                    return city(1, "City", position.latitude, position.longitude)
                }
            }
        val states = mutableListOf<NearbyCityState>()
        val controller = NearbyCityController(repository, worker, direct, { Instant.EPOCH }, states::add)
        controller.start()
        controller.onLocationFix(FlightLocationFix(null, null, 1, latitude = 1.0, longitude = 1.0))
        controller.onLocationFix(FlightLocationFix(null, null, 2, latitude = 2.0, longitude = 2.0))
        assertEquals(1, queued.size)
        queued.single().run()
        assertEquals(listOf(NearbyCoordinate(2.0, 2.0)), positions)
        assertEquals("City", (states.last() as NearbyCityState.Available).cityName)
    }

    @Test fun `newer result wins while an older lookup is active`() {
        val positions = mutableListOf<NearbyCoordinate>()
        lateinit var controller: NearbyCityController
        val repository =
            object : NearbyCityRepository {
                override fun findNearest(
                    position: NearbyCoordinate,
                    reload: Boolean,
                ): NearbyCityRecord? {
                    positions += position
                    if (positions.size == 1) controller.onLocationFix(FlightLocationFix(null, null, 2, latitude = 2.0, longitude = 2.0))
                    return city(positions.size.toLong(), "City ${positions.size}", position.latitude, position.longitude)
                }
            }
        val states = mutableListOf<NearbyCityState>()
        controller = NearbyCityController(repository, direct, direct, { Instant.EPOCH }, states::add)
        controller.start()
        controller.onLocationFix(FlightLocationFix(null, null, 1, latitude = 1.0, longitude = 1.0))
        assertEquals(listOf(NearbyCoordinate(1.0, 1.0), NearbyCoordinate(2.0, 2.0)), positions)
        assertEquals("City 2", (states.last() as NearbyCityState.Available).cityName)
    }

    @Test fun `repository failures invalid zones and empty results are unavailable`() {
        val states = mutableListOf<NearbyCityState>()
        val invalidZone =
            NearbyCityController(FakeRepository(listOf(city(1, "Bad", 0.0, 0.0).copy(timeZoneId = "Not/AZone"))), direct, direct, {
                Instant.EPOCH
            }, states::add)
        invalidZone.start()
        invalidZone.onLocationFix(FlightLocationFix(null, null, 1, latitude = 0.0, longitude = 0.0))
        assertEquals(NearbyCityState.Unavailable, states.last())

        val failed =
            NearbyCityController(
                object : NearbyCityRepository {
                    override fun findNearest(
                        position: NearbyCoordinate,
                        reload: Boolean,
                    ): NearbyCityRecord? = throw IllegalStateException()
                },
                direct,
                direct,
                { Instant.EPOCH },
                states::add,
            )
        failed.start()
        failed.onLocationFix(FlightLocationFix(null, null, 1, latitude = 0.0, longitude = 0.0))
        assertEquals(NearbyCityState.Unavailable, states.last())

        val empty = NearbyCityController(FakeRepository(emptyList()), direct, direct, { Instant.EPOCH }, states::add)
        empty.start()
        empty.onLocationFix(FlightLocationFix(null, null, 1, latitude = 0.0, longitude = 0.0))
        assertEquals(NearbyCityState.Unavailable, states.last())
    }

    @Test fun `retry reloads the latest position and successful time keeps offset data`() {
        val reloads = mutableListOf<Boolean>()
        val repository =
            object : NearbyCityRepository {
                override fun findNearest(
                    position: NearbyCoordinate,
                    reload: Boolean,
                ): NearbyCityRecord? {
                    reloads += reload
                    return if (reload) city(1, "New York", 0.0, 0.0).copy(timeZoneId = "America/New_York") else null
                }
            }
        val states = mutableListOf<NearbyCityState>()
        val controller = NearbyCityController(repository, direct, direct, { Instant.parse("2020-01-01T00:00:00Z") }, states::add)
        controller.start()
        controller.onLocationFix(FlightLocationFix(null, null, 1, latitude = 0.0, longitude = 0.0))
        assertEquals(NearbyCityState.Unavailable, states.last())
        controller.retry()
        val available = states.last() as NearbyCityState.Available
        assertEquals(listOf(false, true), reloads)
        assertEquals(-18_000, available.utcOffsetSeconds)
        assertTrue(available.localTime.isNotBlank())
    }

    private fun controller(
        states: MutableList<NearbyCityState>,
        cities: List<NearbyCityRecord>,
    ) = NearbyCityController(FakeRepository(cities), direct, direct, {
        Instant.EPOCH
    }, states::add)

    private fun city(
        id: Long,
        name: String,
        latitude: Double,
        longitude: Double,
    ) = NearbyCityRecord(id, name, "Country", latitude, longitude, "UTC")

    private class FakeRepository(
        private val cities: List<NearbyCityRecord>,
    ) : NearbyCityRepository {
        override fun findNearest(
            position: NearbyCoordinate,
            reload: Boolean,
        ): NearbyCityRecord? = cities.minByOrNull { distanceKilometres(position, NearbyCoordinate(it.latitude, it.longitude)) }
    }
}
