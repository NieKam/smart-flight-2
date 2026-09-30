package kniezrec.com.flightinfo.gnss.ui

import kniezrec.com.flightinfo.gnss.GnssSatellite
import kniezrec.com.flightinfo.gnss.GnssStatusState
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.testutil.FakeLocationDataSource
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
class GnssStatusViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val location = FakeLocationDataSource()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `waits until satellites arrive, shows them, and an empty report waits again`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)
            assertEquals(GnssStatusState.Waiting, viewModel.state.value)

            report(TWO_SATELLITES)
            assertEquals(GnssStatusState.Available(TWO_SATELLITES), viewModel.state.value)

            report(emptyList())
            assertEquals(GnssStatusState.Waiting, viewModel.state.value)
        }

    @Test fun `nothing is observed before the state is collected`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            runCurrent()

            location.emitSatellites(TWO_SATELLITES)
            runCurrent()

            assertEquals(GnssStatusState.Waiting, viewModel.state.value)
            assertEquals(0, location.satelliteRegistrations.registerCount)
        }

    @Test fun `collecting again within the stop timeout keeps the report`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val first = subscribe(viewModel)
            report(TWO_SATELLITES)

            first.cancel()
            advanceTimeBy(GnssStatusViewModel.STOP_TIMEOUT_MILLIS - 1_000)
            subscribe(viewModel)

            assertEquals(GnssStatusState.Available(TWO_SATELLITES), viewModel.state.value)
            assertEquals(1, location.satelliteRegistrations.registerCount)
        }

    @Test fun `observation restarted after the stop timeout starts over from waiting`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val first = subscribe(viewModel)
            report(TWO_SATELLITES)

            first.cancel()
            advanceTimeBy(GnssStatusViewModel.STOP_TIMEOUT_MILLIS + 1)
            runCurrent()
            assertEquals(0, location.satelliteRegistrations.activeCount)

            subscribe(viewModel)
            assertEquals(GnssStatusState.Waiting, viewModel.state.value)
        }

    @Test fun `location switched off drops the satellites and back on starts over from waiting`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)
            report(TWO_SATELLITES)

            location.switchLocation(false)
            runCurrent()
            assertEquals(GnssStatusState.LocationServicesDisabled, viewModel.state.value)
            assertEquals(0, location.satelliteRegistrations.activeCount)

            location.switchLocation(true)
            runCurrent()
            assertEquals(GnssStatusState.Waiting, viewModel.state.value)

            report(listOf(GnssSatellite(usedInFix = false)))
            assertEquals(GnssStatusState.Available(listOf(GnssSatellite(usedInFix = false))), viewModel.state.value)
        }

    @Test fun `location off when collection starts shows location services disabled`() =
        runTest(dispatcher) {
            location.locationEnabled = false
            val viewModel = viewModel()
            subscribe(viewModel)

            assertEquals(GnssStatusState.LocationServicesDisabled, viewModel.state.value)
            assertEquals(0, location.satelliteRegistrations.registerCount)
        }

    @Test fun `no GNSS hardware shows unavailable without registering`() =
        runTest(dispatcher) {
            location.gnssHardware = false
            val viewModel = viewModel()
            subscribe(viewModel)

            assertEquals(GnssStatusState.Unavailable, viewModel.state.value)
            assertEquals(0, location.satelliteRegistrations.registerCount)
        }

    @Test fun `location off takes precedence over missing GNSS hardware`() =
        runTest(dispatcher) {
            location.gnssHardware = false
            location.locationEnabled = false
            val viewModel = viewModel()
            subscribe(viewModel)

            assertEquals(GnssStatusState.LocationServicesDisabled, viewModel.state.value)
        }

    @Test fun `a failed GNSS registration shows the error and retry registers again`() =
        runTest(dispatcher) {
            location.failSatelliteRegistration = true
            val viewModel = viewModel()
            subscribe(viewModel)
            assertEquals(GnssStatusState.Error, viewModel.state.value)
            assertEquals(1, location.satelliteRegistrations.registerCount)

            location.failSatelliteRegistration = false
            viewModel.retry()
            runCurrent()
            assertEquals(GnssStatusState.Waiting, viewModel.state.value)
            assertEquals(2, location.satelliteRegistrations.registerCount)

            report(TWO_SATELLITES)
            assertEquals(GnssStatusState.Available(TWO_SATELLITES), viewModel.state.value)
        }

    @Test fun `a retry that fails again shows the error again`() =
        runTest(dispatcher) {
            location.failSatelliteRegistration = true
            val viewModel = viewModel()
            subscribe(viewModel)

            viewModel.retry()
            runCurrent()

            assertEquals(GnssStatusState.Error, viewModel.state.value)
            assertEquals(2, location.satelliteRegistrations.registerCount)
        }

    private fun TestScope.viewModel() = GnssStatusViewModel(LocationRepository(location, backgroundScope))

    private fun TestScope.subscribe(viewModel: GnssStatusViewModel): Job =
        backgroundScope.launch { viewModel.state.collect {} }.also { runCurrent() }

    private fun TestScope.report(satellites: List<GnssSatellite>) {
        location.emitSatellites(satellites)
        runCurrent()
    }

    private companion object {
        val TWO_SATELLITES = listOf(GnssSatellite(usedInFix = true, signalStrengthDbHz = 30f), GnssSatellite(usedInFix = false))
    }
}
