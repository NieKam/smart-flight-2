package kniezrec.com.flightinfo.horizon.ui

import kniezrec.com.flightinfo.horizon.HorizonState
import kniezrec.com.flightinfo.orientation.OrientationSample
import kniezrec.com.flightinfo.testutil.FakeOrientationDataSource
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
class HorizonViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val orientation = FakeOrientationDataSource()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `the first sample is the reference and calibrate makes the next sample the reference`() =
        runTest(dispatcher) {
            val viewModel = HorizonViewModel(orientation)
            subscribe(viewModel)

            attitude(10.0, 4.0)
            assertEquals(HorizonState.Available(0, 4, 0f, -4f), viewModel.state.value)

            viewModel.calibrate()
            runCurrent()
            assertEquals(HorizonState.Recalibrating, viewModel.state.value)
            attitude(18.0, -6.0)
            assertEquals(HorizonState.Available(0, -6, 0f, 6f), viewModel.state.value)
            attitude(28.0, -8.0)
            assertEquals(HorizonState.Available(-10, -8, -0.11666667f, 8f), viewModel.state.value)
        }

    @Test fun `non-finite samples are ignored and never become the reference`() =
        runTest(dispatcher) {
            val viewModel = HorizonViewModel(orientation)
            subscribe(viewModel)

            attitude(Double.NaN, 4.0)
            attitude(10.0, Double.POSITIVE_INFINITY)
            assertEquals(HorizonState.Waiting, viewModel.state.value)

            attitude(10.0, 4.0)
            assertEquals(HorizonState.Available(0, 4, 0f, -4f), viewModel.state.value)
        }

    @Test fun `without a rotation-vector sensor the card is unavailable and nothing is registered`() =
        runTest(dispatcher) {
            orientation.available = false
            val viewModel = HorizonViewModel(orientation)
            subscribe(viewModel)

            assertEquals(HorizonState.Unavailable, viewModel.state.value)
            assertEquals(0, orientation.registerCount)
        }

    @Test fun `a refused registration shows the error until retry registers again`() =
        runTest(dispatcher) {
            orientation.failRegistration = true
            val viewModel = HorizonViewModel(orientation)
            subscribe(viewModel)
            assertEquals(HorizonState.Error, viewModel.state.value)

            orientation.failRegistration = false
            viewModel.retry()
            runCurrent()
            assertEquals(HorizonState.Waiting, viewModel.state.value)
            assertEquals(2, orientation.registerCount)

            attitude(5.0, 1.0)
            assertEquals(HorizonState.Available(0, 1, 0f, -1f), viewModel.state.value)
        }

    @Test fun `a restarted observation waits for and recalibrates from a new sample, ignoring the old registration`() =
        runTest(dispatcher) {
            val viewModel = HorizonViewModel(orientation)
            subscribe(viewModel)
            attitude(10.0, 2.0)
            val oldRegistration = orientation.all.single()

            viewModel.retry()
            runCurrent()
            assertEquals(HorizonState.Waiting, viewModel.state.value)
            oldRegistration(OrientationSample(0.0, 25.0, 9.0))
            runCurrent()
            assertEquals(HorizonState.Waiting, viewModel.state.value)
            assertEquals(1, orientation.activeCount)

            attitude(30.0, -4.0)
            assertEquals(HorizonState.Available(0, -4, 0f, 4f), viewModel.state.value)
            attitude(15.0, 5.0)
            assertEquals(HorizonState.Available(15, 5, 0.175f, -5f), viewModel.state.value)
        }

    @Test fun `calibrate while nothing is observed is ignored`() =
        runTest(dispatcher) {
            val viewModel = HorizonViewModel(orientation)
            viewModel.calibrate()
            runCurrent()
            assertEquals(HorizonState.Waiting, viewModel.state.value)

            subscribe(viewModel)
            assertEquals(HorizonState.Waiting, viewModel.state.value)
            attitude(10.0, 4.0)
            assertEquals(HorizonState.Available(0, 4, 0f, -4f), viewModel.state.value)
        }

    @Test fun `nothing is observed before the state is collected`() =
        runTest(dispatcher) {
            HorizonViewModel(orientation)
            runCurrent()

            assertEquals(0, orientation.registerCount)
        }

    @Test fun `the sensor is registered only while the state is collected`() =
        runTest(dispatcher) {
            val viewModel = HorizonViewModel(orientation)
            val subscription = subscribe(viewModel)
            assertEquals(1, orientation.activeCount)

            subscription.cancel()
            advanceTimeBy(HorizonViewModel.STOP_TIMEOUT_MILLIS + 1)
            runCurrent()

            assertEquals(0, orientation.activeCount)
        }

    @Test fun `collecting again within the stop timeout keeps the reference`() =
        runTest(dispatcher) {
            val viewModel = HorizonViewModel(orientation)
            val first = subscribe(viewModel)
            attitude(10.0, 0.0)

            first.cancel()
            advanceTimeBy(HorizonViewModel.STOP_TIMEOUT_MILLIS - 1_000)
            subscribe(viewModel)
            attitude(20.0, 0.0)

            assertEquals(HorizonState.Available(-10, 0, -0.11666667f, 0f), viewModel.state.value)
            assertEquals(1, orientation.registerCount)
        }

    @Test fun `observation restarted after the stop timeout captures a new reference`() =
        runTest(dispatcher) {
            val viewModel = HorizonViewModel(orientation)
            val first = subscribe(viewModel)
            attitude(10.0, 0.0)

            first.cancel()
            advanceTimeBy(HorizonViewModel.STOP_TIMEOUT_MILLIS + 1)
            subscribe(viewModel)
            assertEquals(HorizonState.Waiting, viewModel.state.value)

            attitude(20.0, 0.0)
            assertEquals(HorizonState.Available(0, 0, 0f, 0f), viewModel.state.value)
        }

    private fun TestScope.subscribe(viewModel: HorizonViewModel): Job =
        backgroundScope.launch { viewModel.state.collect {} }.also { runCurrent() }

    private fun TestScope.attitude(
        pitch: Double,
        roll: Double,
    ) {
        orientation.emit(pitchDegrees = pitch, rollDegrees = roll)
        runCurrent()
    }
}
