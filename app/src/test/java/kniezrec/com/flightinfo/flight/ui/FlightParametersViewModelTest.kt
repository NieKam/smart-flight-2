package kniezrec.com.flightinfo.flight.ui

import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.flight.FlightParametersState
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.testutil.FakeLocationDataSource
import kniezrec.com.flightinfo.testutil.FakePressureDataSource
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlightParametersViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val location = FakeLocationDataSource()
    private val pressure = FakePressureDataSource()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `first fix has no vertical speed and the second computes it in km per h and m per s`() =
        runTest(dispatcher) {
            val viewModel = viewModel(location)
            subscribe(viewModel)

            fix(FlightLocationFix(10.0, 100.0, 1_000_000_000L))
            assertEquals(FlightParametersState.Readings(36.0, null, 100.0), viewModel.state.value)

            fix(FlightLocationFix(10.0, 104.0, 3_000_000_000L))
            assertEquals(FlightParametersState.Readings(36.0, 2.0, 104.0), viewModel.state.value)
        }

    @Test fun `missing fields and invalid intervals do not invent readings`() =
        runTest(dispatcher) {
            val viewModel = viewModel(location)
            subscribe(viewModel)

            fix(FlightLocationFix(null, 100.0, 2_000_000_000L))
            fix(FlightLocationFix(null, 102.0, 2_000_000_000L))
            assertEquals(FlightParametersState.Readings(null, null, 102.0), viewModel.state.value)

            fix(FlightLocationFix(null, 104.0, 3_000_000_000L))
            assertEquals(FlightParametersState.Readings(null, null, 104.0), viewModel.state.value)
        }

    @Test fun `fixes without a displayable speed or altitude keep the card waiting`() =
        runTest(dispatcher) {
            val viewModel = viewModel(location)
            val states = subscribeRecording(viewModel)

            fix(FlightLocationFix(null, null, 1_000_000_000L))
            fix(FlightLocationFix(Double.NaN, Double.POSITIVE_INFINITY, 2_000_000_000L))

            assertEquals(listOf(FlightParametersState.Waiting), states)
        }

    @Test fun `once readable, a fix without values still shows readings`() =
        runTest(dispatcher) {
            val viewModel = viewModel(location)
            subscribe(viewModel)

            fix(FlightLocationFix(10.0, null, 1_000_000_000L))
            fix(FlightLocationFix(null, null, 2_000_000_000L))

            assertEquals(FlightParametersState.Readings(null, null, null), viewModel.state.value)
        }

    @Test fun `nothing is observed before the state is collected`() =
        runTest(dispatcher) {
            val viewModel = viewModel(location)
            runCurrent()

            location.emitFix(FlightLocationFix(10.0, 100.0, 1_000_000_000L))
            runCurrent()

            assertEquals(FlightParametersState.Waiting, viewModel.state.value)
            assertEquals(0, location.fixRegistrations.registerCount)
            assertEquals(0, pressure.registerCount)
        }

    @Test fun `pressure is attached to readings only`() =
        runTest(dispatcher) {
            val viewModel = viewModel(location)
            subscribe(viewModel)

            pressure.emit(1013.25)
            runCurrent()
            assertEquals(FlightParametersState.Waiting, viewModel.state.value)

            fix(FlightLocationFix(10.0, 100.0, 1_000_000_000L))
            assertEquals(FlightParametersState.Readings(36.0, null, 100.0, 1013.25), viewModel.state.value)

            pressure.emit(1000.5)
            runCurrent()
            assertEquals(FlightParametersState.Readings(36.0, null, 100.0, 1000.5), viewModel.state.value)
        }

    @Test fun `readings have no pressure without a barometer value`() =
        runTest(dispatcher) {
            val viewModel = viewModel(location)
            subscribe(viewModel)

            fix(FlightLocationFix(10.0, 100.0, 1_000_000_000L))

            assertEquals(FlightParametersState.Readings(36.0, null, 100.0, null), viewModel.state.value)
        }

    @Test fun `pressure sensor and GPS are registered only while the state is collected`() =
        runTest(dispatcher) {
            val viewModel = viewModel(location)
            val subscription = subscribe(viewModel)
            assertEquals(1, pressure.activeCount)
            assertEquals(1, location.fixRegistrations.activeCount)

            subscription.cancel()
            advanceTimeBy(FlightParametersViewModel.STOP_TIMEOUT_MILLIS + 1)
            runCurrent()

            assertEquals(0, pressure.activeCount)
            assertEquals(0, location.fixRegistrations.activeCount)
        }

    @Test fun `collecting again within the stop timeout keeps readings and history`() =
        runTest(dispatcher) {
            val viewModel = viewModel(location)
            val first = subscribe(viewModel)
            fix(FlightLocationFix(10.0, 100.0, 1_000_000_000L))

            first.cancel()
            advanceTimeBy(FlightParametersViewModel.STOP_TIMEOUT_MILLIS - 1_000)
            subscribe(viewModel)
            assertEquals(FlightParametersState.Readings(36.0, null, 100.0), viewModel.state.value)

            fix(FlightLocationFix(10.0, 104.0, 3_000_000_000L))
            assertEquals(FlightParametersState.Readings(36.0, 2.0, 104.0), viewModel.state.value)
            assertEquals(1, location.fixRegistrations.registerCount)
            assertEquals(1, pressure.registerCount)
        }

    @Test fun `observation restarted after the stop timeout starts over from waiting with an empty history`() =
        runTest(dispatcher) {
            val viewModel = viewModel(location)
            val first = subscribe(viewModel)
            pressure.emit(1013.25)
            fix(FlightLocationFix(10.0, 100.0, 1_000_000_000L))

            first.cancel()
            advanceTimeBy(FlightParametersViewModel.STOP_TIMEOUT_MILLIS + 1)
            subscribe(viewModel)
            assertEquals(FlightParametersState.Waiting, viewModel.state.value)

            fix(FlightLocationFix(10.0, 104.0, 3_000_000_000L))
            // No vertical speed from the old session, and the old pressure value is gone.
            assertEquals(FlightParametersState.Readings(36.0, null, 104.0, null), viewModel.state.value)
        }

    @Test fun `location switched off resets to waiting until it is back on, with an empty history`() =
        runTest(dispatcher) {
            val viewModel = viewModel(location)
            subscribe(viewModel)
            fix(FlightLocationFix(10.0, 100.0, 1_000_000_000L))

            location.switchLocation(false)
            runCurrent()
            assertEquals(FlightParametersState.Waiting, viewModel.state.value)
            assertEquals(0, location.fixRegistrations.activeCount)

            location.switchLocation(true)
            fix(FlightLocationFix(10.0, 104.0, 3_000_000_000L))
            assertEquals(FlightParametersState.Readings(36.0, null, 104.0), viewModel.state.value)
        }

    @Test fun `a stale off value replayed to a new collector does not stop the readings`() =
        runTest(dispatcher) {
            // The repository caches "off"; the location is on again before the card is collected.
            val source = FakeLocationDataSource(locationEnabled = false)
            val viewModel = viewModel(source)
            source.locationEnabled = true
            subscribe(viewModel)

            source.emitFix(FlightLocationFix(10.0, 100.0, 1_000_000_000L))
            runCurrent()

            assertEquals(FlightParametersState.Readings(36.0, null, 100.0), viewModel.state.value)
        }

    @Test fun `a failed GPS registration leaves the card as it is`() =
        runTest(dispatcher) {
            location.failFixRegistration = true
            val viewModel = viewModel(location)
            subscribe(viewModel)
            pressure.emit(1013.25)
            runCurrent()

            assertEquals(FlightParametersState.Waiting, viewModel.state.value)
            assertEquals(1, pressure.activeCount)
        }

    private fun TestScope.viewModel(source: FakeLocationDataSource) =
        FlightParametersViewModel(LocationRepository(source, backgroundScope), pressure)

    private fun TestScope.subscribe(viewModel: FlightParametersViewModel): Job =
        backgroundScope.launch { viewModel.state.collect {} }.also { runCurrent() }

    private fun TestScope.subscribeRecording(viewModel: FlightParametersViewModel): List<FlightParametersState> {
        val states = mutableListOf<FlightParametersState>()
        backgroundScope.launch { viewModel.state.collect { states += it } }
        runCurrent()
        return states
    }

    private fun TestScope.fix(fix: FlightLocationFix) {
        location.emitFix(fix)
        runCurrent()
    }
}
