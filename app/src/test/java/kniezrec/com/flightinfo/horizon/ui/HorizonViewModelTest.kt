package kniezrec.com.flightinfo.horizon.ui

import androidx.lifecycle.SavedStateHandle
import kniezrec.com.flightinfo.horizon.HorizonState
import kniezrec.com.flightinfo.horizon.mapHorizonAttitude
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
    private val savedStateHandle = SavedStateHandle()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    // Samples give nose-up as NEGATIVE pitch; the card shows nose-up as positive.

    @Test fun `the first sample ever becomes the reference and is saved`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)

            attitude(10.0, 4.0)

            assertEquals(HorizonState.Available(0, 4, 0f, -4f), viewModel.state.value)
            assertEquals(10.0, savedStateHandle.get<Double>(HorizonViewModel.KEY_REFERENCE_PITCH))
        }

    @Test fun `samples are low-pass filtered`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)
            attitude(0.0, 0.0)

            // A quarter of the step: nose down by 2 degrees, roll right by 4.
            attitude(8.0, 16.0)

            assertEquals(mapHorizonAttitude(-2.0, 4.0), viewModel.state.value)
        }

    @Test fun `calibrate shows recalibrating and makes the next filtered sample the reference`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)
            attitude(10.0, 4.0)

            viewModel.calibrate()
            runCurrent()
            assertEquals(HorizonState.Recalibrating, viewModel.state.value)
            assertEquals(null, savedStateHandle.get<Double>(HorizonViewModel.KEY_REFERENCE_PITCH))

            // Filtered: pitch 10 + (18 - 10) / 4 = 12 becomes level; roll 4 + (-8 - 4) / 4 = 1.
            attitude(18.0, -8.0)
            assertEquals(HorizonState.Available(0, 1, 0f, -1f), viewModel.state.value)
            assertEquals(12.0, savedStateHandle.get<Double>(HorizonViewModel.KEY_REFERENCE_PITCH))

            // Filtered pitch 12 + (28 - 12) / 4 = 16: 4 degrees nose down.
            attitude(28.0, -8.0)
            assertEquals(mapHorizonAttitude(-4.0, -1.25), viewModel.state.value)
        }

    @Test fun `reset to absolute makes the reference zero and shows it at once`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)
            attitude(-10.0, 0.0)
            assertEquals(HorizonState.Available(0, 0, 0f, 0f), viewModel.state.value)

            viewModel.resetToAbsolute()
            runCurrent()

            assertEquals(HorizonState.Available(10, 0, 0.11666667f, 0f), viewModel.state.value)
            assertEquals(0.0, savedStateHandle.get<Double>(HorizonViewModel.KEY_REFERENCE_PITCH))
            attitude(-10.0, 0.0)
            assertEquals(HorizonState.Available(10, 0, 0.11666667f, 0f), viewModel.state.value)
        }

    @Test fun `reset to absolute while recalibrating shows the absolute pitch`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)
            attitude(-10.0, 0.0)
            viewModel.calibrate()
            runCurrent()

            viewModel.resetToAbsolute()
            runCurrent()

            assertEquals(HorizonState.Available(10, 0, 0.11666667f, 0f), viewModel.state.value)
        }

    @Test fun `the reference is restored from the saved state`() =
        runTest(dispatcher) {
            val viewModel = HorizonViewModel(SavedStateHandle(mapOf(HorizonViewModel.KEY_REFERENCE_PITCH to 5.0)), orientation)
            subscribe(viewModel)

            attitude(15.0, 0.0)

            assertEquals(HorizonState.Available(-10, 0, -0.11666667f, 0f), viewModel.state.value)
        }

    @Test fun `non-finite samples are ignored and never become the reference`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
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
            val viewModel = viewModel()
            subscribe(viewModel)

            assertEquals(HorizonState.Unavailable, viewModel.state.value)
            assertEquals(0, orientation.registerCount)
        }

    @Test fun `a refused registration shows the error until retry registers again`() =
        runTest(dispatcher) {
            orientation.failRegistration = true
            val viewModel = viewModel()
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

    @Test fun `a restarted observation waits for a new sample, keeps the reference and ignores the old registration`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
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

            // The filter starts over from the new sample; the reference (10) is kept.
            attitude(30.0, -4.0)
            assertEquals(mapHorizonAttitude(-20.0, -4.0), viewModel.state.value)
        }

    @Test fun `calibrate while nothing is observed makes the next sample the reference`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val first = subscribe(viewModel)
            attitude(10.0, 4.0)
            first.cancel()
            advanceTimeBy(HorizonViewModel.STOP_TIMEOUT_MILLIS + 1)
            runCurrent()

            viewModel.calibrate()
            runCurrent()
            subscribe(viewModel)
            assertEquals(HorizonState.Waiting, viewModel.state.value)

            attitude(20.0, 4.0)
            assertEquals(HorizonState.Available(0, 4, 0f, -4f), viewModel.state.value)
        }

    @Test fun `nothing is observed before the state is collected`() =
        runTest(dispatcher) {
            viewModel()
            runCurrent()

            assertEquals(0, orientation.registerCount)
        }

    @Test fun `the sensor is registered only while the state is collected`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val subscription = subscribe(viewModel)
            assertEquals(1, orientation.activeCount)

            subscription.cancel()
            advanceTimeBy(HorizonViewModel.STOP_TIMEOUT_MILLIS + 1)
            runCurrent()

            assertEquals(0, orientation.activeCount)
        }

    @Test fun `collecting again within the stop timeout keeps the observation`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val first = subscribe(viewModel)
            attitude(10.0, 0.0)

            first.cancel()
            advanceTimeBy(HorizonViewModel.STOP_TIMEOUT_MILLIS - 1_000)
            subscribe(viewModel)
            // Filtered: 10 + (20 - 10) / 4 = 12.5.
            attitude(20.0, 0.0)

            assertEquals(mapHorizonAttitude(-2.5, 0.0), viewModel.state.value)
            assertEquals(1, orientation.registerCount)
        }

    @Test fun `observation restarted after the stop timeout keeps the reference`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val first = subscribe(viewModel)
            attitude(10.0, 0.0)

            first.cancel()
            advanceTimeBy(HorizonViewModel.STOP_TIMEOUT_MILLIS + 1)
            runCurrent()
            assertEquals(0, orientation.activeCount)
            subscribe(viewModel)
            assertEquals(HorizonState.Waiting, viewModel.state.value)

            attitude(20.0, 0.0)
            assertEquals(HorizonState.Available(-10, 0, -0.11666667f, 0f), viewModel.state.value)
        }

    private fun viewModel() = HorizonViewModel(savedStateHandle, orientation)

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
