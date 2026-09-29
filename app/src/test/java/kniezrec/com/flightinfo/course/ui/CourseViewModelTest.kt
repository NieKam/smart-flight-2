package kniezrec.com.flightinfo.course.ui

import kniezrec.com.flightinfo.course.CourseState
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.orientation.OrientationSample
import kniezrec.com.flightinfo.testutil.FakeLocationDataSource
import kniezrec.com.flightinfo.testutil.FakeOrientationDataSource
import kniezrec.com.flightinfo.testutil.flightFix
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
class CourseViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val orientation = FakeOrientationDataSource()
    private val location = FakeLocationDataSource()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `heading is floored and non-finite headings are ignored`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)

            heading(10.9)
            assertEquals(CourseState.Available(10, null), viewModel.state.value)

            heading(Double.NaN)
            assertEquals(CourseState.Available(10, null), viewModel.state.value)

            heading(-1.0)
            assertEquals(CourseState.Available(359, null), viewModel.state.value)
        }

    @Test fun `without a rotation-vector sensor the card is unavailable and nothing is registered`() =
        runTest(dispatcher) {
            orientation.available = false
            val viewModel = viewModel()
            subscribe(viewModel)

            assertEquals(CourseState.Unavailable, viewModel.state.value)
            assertEquals(0, orientation.registerCount)
        }

    @Test fun `a refused registration shows the error and keeps no heading`() =
        runTest(dispatcher) {
            orientation.failRegistration = true
            val viewModel = viewModel()
            subscribe(viewModel)

            fix(bearing = 90.0)

            assertEquals(CourseState.Error, viewModel.state.value)
        }

    @Test fun `retry after an error registers again and starts over from waiting`() =
        runTest(dispatcher) {
            orientation.failRegistration = true
            val viewModel = viewModel()
            subscribe(viewModel)
            assertEquals(CourseState.Error, viewModel.state.value)

            orientation.failRegistration = false
            viewModel.retry()
            runCurrent()
            assertEquals(CourseState.Waiting, viewModel.state.value)
            assertEquals(2, orientation.registerCount)

            heading(18.0)
            assertEquals(CourseState.Available(18, null), viewModel.state.value)
        }

    @Test fun `the GPS bearing supplements the heading and a fix without bearing clears it`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)

            heading(10.0)
            fix(bearing = 725.0)
            assertEquals(CourseState.Available(10, 5), viewModel.state.value)

            fix(bearing = null)
            assertEquals(CourseState.Available(10, null), viewModel.state.value)
        }

    @Test fun `a bearing before the first heading is shown when the heading arrives`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)

            fix(bearing = 725.0)
            assertEquals(CourseState.Waiting, viewModel.state.value)

            heading(10.0)
            assertEquals(CourseState.Available(10, 5), viewModel.state.value)
        }

    @Test fun `a fix without bearing before the first heading clears the pending bearing`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)

            fix(bearing = 99.0)
            fix(bearing = null)
            heading(10.0)

            assertEquals(CourseState.Available(10, null), viewModel.state.value)
        }

    @Test fun `retry clears heading and bearing and does not accumulate listeners`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)
            heading(42.0)
            fix(bearing = 99.0)

            viewModel.retry()
            runCurrent()
            assertEquals(CourseState.Waiting, viewModel.state.value)
            assertEquals(2, orientation.registerCount)
            assertEquals(1, orientation.activeCount)

            heading(18.0)
            assertEquals(CourseState.Available(18, null), viewModel.state.value)
        }

    @Test fun `a late heading from a replaced registration cannot restore the course`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)
            val firstRegistration = orientation.all.single()

            viewModel.retry()
            runCurrent()
            firstRegistration(OrientationSample(99.0, 0.0, 0.0))
            runCurrent()
            assertEquals(CourseState.Waiting, viewModel.state.value)

            heading(18.0)
            assertEquals(CourseState.Available(18, null), viewModel.state.value)
        }

    @Test fun `nothing is observed before the state is collected`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            runCurrent()

            assertEquals(CourseState.Waiting, viewModel.state.value)
            assertEquals(0, orientation.registerCount)
            assertEquals(0, location.fixRegistrations.registerCount)
        }

    @Test fun `the sensor is registered only while the state is collected`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val subscription = subscribe(viewModel)
            assertEquals(1, orientation.activeCount)

            subscription.cancel()
            advanceTimeBy(CourseViewModel.STOP_TIMEOUT_MILLIS + 1)
            runCurrent()

            assertEquals(0, orientation.activeCount)
            assertEquals(0, location.fixRegistrations.activeCount)
        }

    @Test fun `collecting again within the stop timeout keeps the card`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val first = subscribe(viewModel)
            heading(42.0)
            fix(bearing = 99.0)

            first.cancel()
            advanceTimeBy(CourseViewModel.STOP_TIMEOUT_MILLIS - 1_000)
            subscribe(viewModel)

            assertEquals(CourseState.Available(42, 99), viewModel.state.value)
            assertEquals(1, orientation.registerCount)
        }

    @Test fun `observation restarted after the stop timeout starts over from waiting without a bearing`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val first = subscribe(viewModel)
            heading(42.0)
            fix(bearing = 99.0)

            first.cancel()
            advanceTimeBy(CourseViewModel.STOP_TIMEOUT_MILLIS + 1)
            subscribe(viewModel)
            assertEquals(CourseState.Waiting, viewModel.state.value)

            heading(18.0)
            assertEquals(CourseState.Available(18, null), viewModel.state.value)
        }

    @Test fun `location switched off keeps the last bearing and ignores fixes until it is back on`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)
            heading(10.0)
            fix(bearing = 90.0)

            location.switchLocation(false)
            runCurrent()
            fix(bearing = 180.0)
            assertEquals(CourseState.Available(10, 90), viewModel.state.value)
            assertEquals(0, location.fixRegistrations.activeCount)

            location.switchLocation(true)
            // Let the ViewModel register for fixes again before the next one arrives.
            runCurrent()
            fix(bearing = 180.0)
            assertEquals(CourseState.Available(10, 180), viewModel.state.value)
        }

    @Test fun `a failed GPS registration keeps the compass heading`() =
        runTest(dispatcher) {
            location.failFixRegistration = true
            val viewModel = viewModel()
            subscribe(viewModel)

            heading(10.0)

            assertEquals(CourseState.Available(10, null), viewModel.state.value)
        }

    private fun TestScope.viewModel() = CourseViewModel(orientation, LocationRepository(location, backgroundScope))

    private fun TestScope.subscribe(viewModel: CourseViewModel): Job =
        backgroundScope.launch { viewModel.state.collect {} }.also { runCurrent() }

    private fun TestScope.heading(degrees: Double) {
        orientation.emit(headingDegrees = degrees)
        runCurrent()
    }

    private fun TestScope.fix(bearing: Double?) {
        location.emitFix(flightFix(bearingDegrees = bearing))
        runCurrent()
    }
}
