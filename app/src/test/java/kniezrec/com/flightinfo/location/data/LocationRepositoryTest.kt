package kniezrec.com.flightinfo.location.data

import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.gnss.GnssSatellite
import kniezrec.com.flightinfo.testutil.FakeLocationDataSource
import kniezrec.com.flightinfo.testutil.flightFix
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationRepositoryTest {
    private val source = FakeLocationDataSource()

    @Test
    fun `two fix collectors share one registration that is released after the last one stops`() =
        runTest {
            val repository = repository()
            val first = mutableListOf<FlightLocationFix>()
            val second = mutableListOf<FlightLocationFix>()
            val firstJob = backgroundScope.launch { repository.fixes.toList(first) }
            val secondJob = backgroundScope.launch { repository.fixes.toList(second) }
            runCurrent()

            source.emitFix(FIRST_FIX)
            runCurrent()

            assertEquals(1, source.fixRegistrations.registerCount)
            assertEquals(1, source.fixRegistrations.activeCount)
            assertEquals(listOf(FIRST_FIX), first)
            assertEquals(listOf(FIRST_FIX), second)

            firstJob.cancel()
            runCurrent()
            assertEquals(1, source.fixRegistrations.activeCount)

            secondJob.cancel()
            runCurrent()
            assertEquals(0, source.fixRegistrations.activeCount)
            assertEquals(1, source.fixRegistrations.unregisterCount)
        }

    @Test
    fun `two satellite collectors share one registration that is released after the last one stops`() =
        runTest {
            val repository = repository()
            val first = mutableListOf<List<GnssSatellite>>()
            val second = mutableListOf<List<GnssSatellite>>()
            val firstJob = backgroundScope.launch { repository.satellites.toList(first) }
            val secondJob = backgroundScope.launch { repository.satellites.toList(second) }
            runCurrent()

            source.emitSatellites(SATELLITES)
            runCurrent()

            assertEquals(1, source.satelliteRegistrations.registerCount)
            assertEquals(listOf(SATELLITES), first)
            assertEquals(listOf(SATELLITES), second)

            firstJob.cancel()
            secondJob.cancel()
            runCurrent()
            assertEquals(0, source.satelliteRegistrations.activeCount)
            assertEquals(1, source.satelliteRegistrations.unregisterCount)
        }

    @Test
    fun `fixes are not replayed to a collector that joins later`() =
        runTest {
            val repository = repository()
            backgroundScope.launch { repository.fixes.collect() }
            runCurrent()
            source.emitFix(FIRST_FIX)
            runCurrent()

            val late = mutableListOf<FlightLocationFix>()
            backgroundScope.launch { repository.fixes.toList(late) }
            runCurrent()
            assertTrue(late.isEmpty())

            source.emitFix(SECOND_FIX)
            runCurrent()
            assertEquals(listOf(SECOND_FIX), late)
            assertEquals(1, source.fixRegistrations.registerCount)
        }

    @Test
    fun `satellites replay the latest report to a collector joining an active registration`() =
        runTest {
            val repository = repository()
            backgroundScope.launch { repository.satellites.collect() }
            runCurrent()
            source.emitSatellites(SATELLITES)
            runCurrent()

            val late = mutableListOf<List<GnssSatellite>>()
            backgroundScope.launch { repository.satellites.toList(late) }
            runCurrent()

            assertEquals(listOf(SATELLITES), late)
            assertEquals(1, source.satelliteRegistrations.registerCount)
        }

    @Test
    fun `satellites of a released registration are not replayed to the next collector`() =
        runTest {
            val repository = repository()
            val firstJob = backgroundScope.launch { repository.satellites.collect() }
            runCurrent()
            source.emitSatellites(SATELLITES)
            runCurrent()
            firstJob.cancel()
            runCurrent()

            val next = mutableListOf<List<GnssSatellite>>()
            backgroundScope.launch { repository.satellites.toList(next) }
            runCurrent()

            assertTrue(next.isEmpty())
            assertEquals(2, source.satelliteRegistrations.registerCount)
        }

    @Test
    fun `late callback of a released registration does not reach the next collector`() =
        runTest {
            val repository = repository()
            val firstJob = backgroundScope.launch { repository.fixes.collect() }
            runCurrent()
            val releasedCallback = source.fixRegistrations.all.single()
            firstJob.cancel()
            runCurrent()

            val received = mutableListOf<FlightLocationFix>()
            backgroundScope.launch { repository.fixes.toList(received) }
            runCurrent()
            releasedCallback(FIRST_FIX)
            runCurrent()
            assertTrue(received.isEmpty())

            source.emitFix(SECOND_FIX)
            runCurrent()
            assertEquals(listOf(SECOND_FIX), received)
        }

    @Test
    fun `fix registration failure reaches the collector and is retried by the next collection`() =
        runTest {
            val repository = repository()
            source.failFixRegistration = true

            val first = runCatching { repository.fixes.collect() }.exceptionOrNull()
            runCurrent()
            val second = runCatching { repository.fixes.collect() }.exceptionOrNull()
            runCurrent()

            assertTrue(first is LocationRegistrationException)
            assertTrue(second is LocationRegistrationException)
            assertEquals(2, source.fixRegistrations.registerCount)
            assertEquals(0, source.fixRegistrations.activeCount)
        }

    @Test
    fun `satellite registration failure reaches the collector`() =
        runTest {
            val repository = repository()
            source.failSatelliteRegistration = true

            val failure = runCatching { repository.satellites.collect() }.exceptionOrNull()

            assertTrue(failure is LocationRegistrationException)
            assertEquals(0, source.satelliteRegistrations.activeCount)
        }

    @Test
    fun `location switch is shared and follows the data source`() =
        runTest {
            val repository = repository()
            assertTrue(repository.locationEnabled.value)

            val states = mutableListOf<Boolean>()
            backgroundScope.launch { repository.locationEnabled.toList(states) }
            runCurrent()
            source.setLocationEnabled(false)
            runCurrent()

            assertFalse(repository.locationEnabled.value)
            assertEquals(listOf(true, false), states)
        }

    @Test
    fun `one-off checks read the data source`() =
        runTest {
            val repository = repository()
            assertTrue(repository.isLocationEnabled())
            assertTrue(repository.hasGnssHardware())

            source.locationEnabled = false
            source.gnssHardware = false

            assertFalse(repository.isLocationEnabled())
            assertFalse(repository.hasGnssHardware())
        }

    private fun TestScope.repository() = LocationRepository(source, backgroundScope)

    private companion object {
        val FIRST_FIX = flightFix(elapsedSeconds = 1L)
        val SECOND_FIX = flightFix(elapsedSeconds = 2L)
        val SATELLITES = listOf(GnssSatellite(usedInFix = true, signalStrengthDbHz = 30f), GnssSatellite(usedInFix = false))
    }
}
