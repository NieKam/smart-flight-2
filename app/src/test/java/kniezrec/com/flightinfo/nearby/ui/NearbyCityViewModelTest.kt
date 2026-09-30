package kniezrec.com.flightinfo.nearby.ui

import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.NearbyCityState
import kniezrec.com.flightinfo.nearby.data.CityDataSource
import kniezrec.com.flightinfo.nearby.data.CityRepository
import kniezrec.com.flightinfo.testutil.FakeLocationDataSource
import kniezrec.com.flightinfo.testutil.flightFix
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyCityViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val location = FakeLocationDataSource()
    private val clock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `valid fix produces the nearest city and invalid coordinates are ignored`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakeCityDataSource(listOf(city(1, "Near", 0.0, 0.0), city(2, "Far", 40.0, 40.0))))
            subscribe(viewModel)

            fix(latitude = Double.NaN, longitude = 0.0)
            fix(latitude = null, longitude = null)
            fix(latitude = 10.0, longitude = 181.0)
            assertEquals(NearbyCityState.WaitingForPosition, viewModel.state.value)

            fix(latitude = 0.1, longitude = 0.0)
            assertEquals("Near", available(viewModel).cityName)
        }

    @Test fun `available state has the great-circle distance and raw time values`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakeCityDataSource(listOf(city(1, "City", 0.0, 1.0, "Europe/Warsaw"))))
            subscribe(viewModel)

            fix(latitude = 0.0, longitude = 0.0)

            val state = available(viewModel)
            assertTrue(state.distanceKilometres in 111.0..112.0)
            assertEquals(ZoneId.of("Europe/Warsaw"), state.zoneId)
            assertEquals(clock.instant(), state.instant)
            assertEquals(3_600, state.utcOffsetSeconds)
        }

    @Test fun `each fix shows looking up before its result`() =
        runTest(dispatcher) {
            val gate = GateDispatcher()
            val viewModel = viewModel(FakeCityDataSource(listOf(city(1, "City", 0.0, 0.0))), gate)
            val states = subscribeRecording(viewModel)

            fix(latitude = 0.0, longitude = 0.0)
            assertEquals(NearbyCityState.LookingUp, viewModel.state.value)
            settle(gate)

            assertEquals(listOf(NearbyCityState.WaitingForPosition, NearbyCityState.LookingUp), states.take(2))
            assertEquals("City", (states.last() as NearbyCityState.Available).cityName)
        }

    @Test fun `the newest of several queued fixes wins and the table is read once`() =
        runTest(dispatcher) {
            val source = FakeCityDataSource(listOf(city(1, "One", 1.0, 1.0), city(2, "Two", 2.0, 2.0)))
            val viewModel = viewModel(source)
            subscribe(viewModel)

            location.emitFix(flightFix(latitude = 1.0, longitude = 1.0))
            location.emitFix(flightFix(latitude = 2.0, longitude = 2.0))
            runCurrent()

            assertEquals("Two", available(viewModel).cityName)
            assertEquals(listOf(false), source.reads)
        }

    @Test fun `a newer fix drops the result of a lookup still running`() =
        runTest(dispatcher) {
            val gate = GateDispatcher()
            val viewModel = viewModel(FakeCityDataSource(listOf(city(1, "One", 1.0, 1.0), city(2, "Two", 2.0, 2.0))), gate)
            val states = subscribeRecording(viewModel)

            fix(latitude = 1.0, longitude = 1.0)
            assertEquals(NearbyCityState.LookingUp, viewModel.state.value)
            assertTrue("the first lookup waits at the gate", gate.pending.isNotEmpty())

            fix(latitude = 2.0, longitude = 2.0)
            settle(gate)

            assertEquals("Two", available(viewModel).cityName)
            assertFalse(states.any { it is NearbyCityState.Available && it.cityName == "One" })
        }

    @Test fun `an invalid time zone, a failed read and no city are unavailable`() =
        runTest(dispatcher) {
            val invalidZone = viewModel(FakeCityDataSource(listOf(city(1, "Bad", 0.0, 0.0, "Not/AZone"))))
            subscribe(invalidZone)
            fix(latitude = 0.0, longitude = 0.0)
            assertEquals(NearbyCityState.Unavailable, invalidZone.state.value)

            val failing = viewModel(FakeCityDataSource(emptyList(), failuresLeft = Int.MAX_VALUE))
            subscribe(failing)
            fix(latitude = 0.0, longitude = 0.0)
            assertEquals(NearbyCityState.Unavailable, failing.state.value)

            val empty = viewModel(FakeCityDataSource(emptyList()))
            subscribe(empty)
            fix(latitude = 0.0, longitude = 0.0)
            assertEquals(NearbyCityState.Unavailable, empty.state.value)
        }

    @Test fun `retry reloads the data and looks up the latest position`() =
        runTest(dispatcher) {
            val source = FakeCityDataSource(listOf(city(1, "New York", 0.0, 0.0, "America/New_York")), failuresLeft = 1)
            val viewModel = viewModel(source)
            subscribe(viewModel)
            fix(latitude = 0.0, longitude = 0.0)
            assertEquals(NearbyCityState.Unavailable, viewModel.state.value)

            viewModel.retry()
            runCurrent()

            val state = available(viewModel)
            assertEquals("New York", state.cityName)
            assertEquals(-18_000, state.utcOffsetSeconds)
            assertEquals(listOf(false, true), source.reads)
        }

    @Test fun `a failed retry is unavailable and the next fix tries again`() =
        runTest(dispatcher) {
            val source = FakeCityDataSource(listOf(city(1, "City", 0.0, 0.0)), failuresLeft = 2)
            val viewModel = viewModel(source)
            subscribe(viewModel)
            fix(latitude = 0.0, longitude = 0.0)

            viewModel.retry()
            runCurrent()
            assertEquals(NearbyCityState.Unavailable, viewModel.state.value)

            fix(latitude = 0.0, longitude = 0.1)
            assertEquals("City", available(viewModel).cityName)
            assertEquals(listOf(false, true, false), source.reads)
        }

    @Test fun `retry without a position keeps waiting and reloads nothing`() =
        runTest(dispatcher) {
            val source = FakeCityDataSource(listOf(city(1, "City", 0.0, 0.0)))
            val viewModel = viewModel(source)
            subscribe(viewModel)

            viewModel.retry()
            runCurrent()

            assertEquals(NearbyCityState.WaitingForPosition, viewModel.state.value)
            assertEquals(emptyList<Boolean>(), source.reads)
        }

    @Test fun `nothing is observed before the state is collected`() =
        runTest(dispatcher) {
            val source = FakeCityDataSource(listOf(city(1, "City", 0.0, 0.0)))
            val viewModel = viewModel(source)
            viewModel.retry()
            runCurrent()

            assertEquals(NearbyCityState.WaitingForPosition, viewModel.state.value)
            assertEquals(0, location.fixRegistrations.registerCount)
            assertEquals(emptyList<Boolean>(), source.reads)
        }

    @Test fun `collecting again within the stop timeout keeps the city`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakeCityDataSource(listOf(city(1, "City", 0.0, 0.0))))
            val first = subscribe(viewModel)
            fix(latitude = 0.0, longitude = 0.0)

            first.cancel()
            advanceTimeBy(NearbyCityViewModel.STOP_TIMEOUT_MILLIS - 1_000)
            subscribe(viewModel)

            assertEquals("City", available(viewModel).cityName)
            assertEquals(1, location.fixRegistrations.registerCount)
        }

    @Test fun `observation restarted after the stop timeout starts over from waiting and drops old lookups`() =
        runTest(dispatcher) {
            val gate = GateDispatcher()
            val viewModel = viewModel(FakeCityDataSource(listOf(city(1, "City", 0.0, 0.0))), gate)
            val first = subscribe(viewModel)
            fix(latitude = 0.0, longitude = 0.0)
            assertEquals(NearbyCityState.LookingUp, viewModel.state.value)

            first.cancel()
            advanceTimeBy(NearbyCityViewModel.STOP_TIMEOUT_MILLIS + 1)
            subscribe(viewModel)
            settle(gate)

            assertEquals(NearbyCityState.WaitingForPosition, viewModel.state.value)
        }

    @Test fun `location switched off waits for a position and fixes count again once it is back on`() =
        runTest(dispatcher) {
            val viewModel = viewModel(FakeCityDataSource(listOf(city(1, "One", 1.0, 1.0), city(2, "Two", 2.0, 2.0))))
            subscribe(viewModel)
            fix(latitude = 1.0, longitude = 1.0)

            location.switchLocation(false)
            runCurrent()
            assertEquals(NearbyCityState.WaitingForPosition, viewModel.state.value)
            assertEquals(0, location.fixRegistrations.activeCount)

            // No position to look up while location is off.
            viewModel.retry()
            runCurrent()
            assertEquals(NearbyCityState.WaitingForPosition, viewModel.state.value)

            location.switchLocation(true)
            runCurrent()
            fix(latitude = 2.0, longitude = 2.0)
            assertEquals("Two", available(viewModel).cityName)
        }

    @Test fun `a failed GPS registration leaves the card waiting`() =
        runTest(dispatcher) {
            location.failFixRegistration = true
            val viewModel = viewModel(FakeCityDataSource(listOf(city(1, "City", 0.0, 0.0))))
            subscribe(viewModel)

            assertEquals(NearbyCityState.WaitingForPosition, viewModel.state.value)
        }

    private fun TestScope.viewModel(
        source: CityDataSource,
        ioDispatcher: CoroutineDispatcher = dispatcher,
    ) = NearbyCityViewModel(LocationRepository(location, backgroundScope), CityRepository(source, ioDispatcher), clock)

    private fun TestScope.subscribe(viewModel: NearbyCityViewModel): Job =
        backgroundScope.launch { viewModel.state.collect {} }.also { runCurrent() }

    private fun TestScope.subscribeRecording(viewModel: NearbyCityViewModel): List<NearbyCityState> {
        val states = mutableListOf<NearbyCityState>()
        backgroundScope.launch { viewModel.state.collect { states += it } }
        runCurrent()
        return states
    }

    private fun TestScope.fix(
        latitude: Double?,
        longitude: Double?,
    ) {
        location.emitFix(flightFix(latitude = latitude, longitude = longitude))
        runCurrent()
    }

    /** Runs gated IO work and what it resumes until nothing is left. */
    private fun TestScope.settle(gate: GateDispatcher) {
        runCurrent()
        while (gate.pending.isNotEmpty()) {
            gate.releaseAll()
            runCurrent()
        }
    }

    private fun available(viewModel: NearbyCityViewModel): NearbyCityState.Available =
        viewModel.state.value as? NearbyCityState.Available ?: throw AssertionError("Not available: ${viewModel.state.value}")

    private fun city(
        id: Long,
        name: String,
        latitude: Double,
        longitude: Double,
        zone: String = "UTC",
    ) = NearbyCityRecord(id, name, "Country", latitude, longitude, zone)

    private class FakeCityDataSource(
        private val cities: List<NearbyCityRecord>,
        var failuresLeft: Int = 0,
    ) : CityDataSource {
        val reads = mutableListOf<Boolean>()

        override fun readAll(reextract: Boolean): List<NearbyCityRecord> {
            reads += reextract
            if (failuresLeft > 0) {
                failuresLeft--
                throw IllegalStateException("fake read failure")
            }
            return cities
        }
    }

    /** IO dispatcher that holds work until the test releases it, so a lookup can be kept running. */
    private class GateDispatcher : CoroutineDispatcher() {
        val pending = ArrayDeque<Runnable>()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            pending += block
        }

        fun releaseAll() {
            while (pending.isNotEmpty()) pending.removeFirst().run()
        }
    }
}
