package kniezrec.com.flightinfo.route.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.NearbyCoordinate
import kniezrec.com.flightinfo.nearby.data.CityDataSource
import kniezrec.com.flightinfo.nearby.data.CityRepository
import kniezrec.com.flightinfo.route.RouteEndpoint
import kniezrec.com.flightinfo.route.RoutePickerError
import kniezrec.com.flightinfo.route.RoutePickerState
import kniezrec.com.flightinfo.route.data.RouteRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RoutePickerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val preferences: SharedPreferences =
        ApplicationProvider
            .getApplicationContext<Context>()
            .getSharedPreferences("route", Context.MODE_PRIVATE)

    private val alpha = NearbyCityRecord(1L, "Alpha", "A", 0.0, 0.0, "UTC")
    private val beta = NearbyCityRecord(2L, "Beta", "B", 0.0, 1.0, "Europe/Berlin")
    private val badZone = NearbyCityRecord(4L, "Bad zone", "D", 0.0, 2.0, "Not/AZone")
    private val cities = listOf(alpha, beta, badZone)

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `the picker starts closed`() =
        runTest(dispatcher) {
            assertEquals(RoutePickerState(), viewModel().state.value)
        }

    @Test fun `open selects the current city of the endpoint`() =
        runTest(dispatcher) {
            save(departure = alpha.id, destination = beta.id)
            val viewModel = viewModel()

            viewModel.open(RouteEndpoint.DESTINATION)
            runCurrent()

            assertEquals(RoutePickerState(endpoint = RouteEndpoint.DESTINATION, selected = beta), viewModel.state.value)
        }

    @Test fun `open for an endpoint without a city selects nothing`() =
        runTest(dispatcher) {
            save(departure = alpha.id, destination = null)
            val viewModel = viewModel()

            viewModel.open(RouteEndpoint.DESTINATION)
            runCurrent()

            assertEquals(RoutePickerState(endpoint = RouteEndpoint.DESTINATION), viewModel.state.value)
        }

    @Test fun `search lists matching cities and keeps the selection`() =
        runTest(dispatcher) {
            val viewModel = openedViewModel()
            viewModel.select(alpha)

            viewModel.search("  BeTa  ")
            assertTrue(viewModel.state.value.loading)
            runCurrent()

            val state = viewModel.state.value
            assertEquals(listOf(beta), state.results)
            assertFalse(state.loading)
            assertNull(state.error)
            assertEquals(alpha, state.selected)
        }

    @Test fun `a failed search shows SearchFailed and retry reloads the city data`() =
        runTest(dispatcher) {
            val source = FakeCityDataSource(cities, failuresLeft = 1)
            val viewModel = openedViewModel(source)

            viewModel.search("beta")
            runCurrent()

            assertEquals(RoutePickerError.SearchFailed, viewModel.state.value.error)
            assertFalse(viewModel.state.value.loading)
            assertEquals(emptyList<NearbyCityRecord>(), viewModel.state.value.results)

            viewModel.retry()
            assertNull(viewModel.state.value.error)
            assertTrue(viewModel.state.value.loading)
            runCurrent()

            assertEquals(listOf(beta), viewModel.state.value.results)
            assertNull(viewModel.state.value.error)
            assertFalse(viewModel.state.value.loading)
            assertEquals(listOf(false, true), source.reads)
        }

    @Test fun `a failed retry keeps SearchFailed`() =
        runTest(dispatcher) {
            val viewModel = openedViewModel(FakeCityDataSource(cities, failuresLeft = 2))
            viewModel.search("beta")
            runCurrent()

            viewModel.retry()
            runCurrent()

            assertEquals(RoutePickerError.SearchFailed, viewModel.state.value.error)
        }

    @Test fun `retry runs a failed nearest lookup again`() =
        runTest(dispatcher) {
            val viewModel = openedViewModel(FakeCityDataSource(cities, failuresLeft = 1))
            viewModel.nearest(NearbyCoordinate(0.0, 0.9))
            runCurrent()
            assertEquals(RoutePickerError.SearchFailed, viewModel.state.value.error)

            viewModel.retry()
            runCurrent()

            assertEquals(beta, viewModel.state.value.selected)
            assertEquals(listOf(beta), viewModel.state.value.results)
        }

    // TASK-013 bug fix (architecture F11): the nearest city of a map long-press is the selection.
    @Test fun `the nearest city becomes the only result and the confirmable selection`() =
        runTest(dispatcher) {
            val viewModel = openedViewModel()
            viewModel.search("a")
            runCurrent()

            viewModel.nearest(NearbyCoordinate(0.0, 0.9))
            runCurrent()

            val state = viewModel.state.value
            assertEquals(beta, state.selected)
            assertEquals(listOf(beta), state.results)
            assertNull(state.error)
            assertTrue(state.canConfirm)
        }

    @Test fun `no city near the long-press shows NoCityAtLocation and keeps the selection`() =
        runTest(dispatcher) {
            val viewModel = openedViewModel(FakeCityDataSource(emptyList()))
            viewModel.select(alpha)

            viewModel.nearest(NearbyCoordinate(0.0, 0.9))
            runCurrent()

            val state = viewModel.state.value
            assertEquals(RoutePickerError.NoCityAtLocation, state.error)
            assertEquals(emptyList<NearbyCityRecord>(), state.results)
            assertEquals(alpha, state.selected)
            assertFalse(state.loading)
        }

    @Test fun `a long-press outside valid coordinates shows NoCityAtLocation without a lookup`() =
        runTest(dispatcher) {
            val source = FakeCityDataSource(cities)
            val viewModel = openedViewModel(source)

            viewModel.nearest(null)
            runCurrent()

            assertEquals(RoutePickerError.NoCityAtLocation, viewModel.state.value.error)
            assertNull(viewModel.state.value.selected)
            assertEquals(emptyList<Boolean>(), source.reads)
        }

    @Test fun `an invalid nearest city shows InvalidCity and is not selected`() =
        runTest(dispatcher) {
            val viewModel = openedViewModel()

            viewModel.nearest(NearbyCoordinate(0.0, 2.0))
            runCurrent()

            val state = viewModel.state.value
            assertEquals(RoutePickerError.InvalidCity, state.error)
            assertEquals(emptyList<NearbyCityRecord>(), state.results)
            assertNull(state.selected)
            assertFalse(state.canConfirm)
        }

    @Test fun `confirm saves the selected city and closes the picker`() =
        runTest(dispatcher) {
            val viewModel = openedViewModel(endpoint = RouteEndpoint.DESTINATION)
            viewModel.select(beta)

            assertTrue(viewModel.confirm())
            runCurrent()

            assertEquals(RoutePickerState(), viewModel.state.value)
            assertEquals(beta.id, preferences.getLong(DESTINATION, Long.MIN_VALUE))
            assertFalse(preferences.contains(DEPARTURE))
        }

    @Test fun `an invalid selection cannot be confirmed`() =
        runTest(dispatcher) {
            val viewModel = openedViewModel()
            viewModel.select(badZone)

            assertTrue(viewModel.state.value.selectionInvalid)
            assertFalse(viewModel.state.value.canConfirm)
            assertFalse(viewModel.confirm())
            runCurrent()

            assertEquals(RouteEndpoint.DEPARTURE, viewModel.state.value.endpoint)
            assertFalse(preferences.contains(DEPARTURE))
        }

    @Test fun `confirm without a selection does nothing`() =
        runTest(dispatcher) {
            val viewModel = openedViewModel()

            assertFalse(viewModel.confirm())
            assertEquals(RouteEndpoint.DEPARTURE, viewModel.state.value.endpoint)
        }

    @Test fun `a lookup still running when the picker closes is ignored`() =
        runTest(dispatcher) {
            val gate = GateDispatcher()
            val viewModel = openedViewModel(ioDispatcher = gate)

            viewModel.nearest(NearbyCoordinate(0.0, 0.9))
            runCurrent()
            assertTrue("the lookup waits at the gate", gate.pending.isNotEmpty())
            viewModel.close()
            settle(gate)

            assertEquals(RoutePickerState(), viewModel.state.value)
        }

    @Test fun `a lookup still running when the picker reopens for the other endpoint is ignored`() =
        runTest(dispatcher) {
            val gate = GateDispatcher()
            val viewModel = openedViewModel(ioDispatcher = gate)

            viewModel.search("beta")
            runCurrent()
            viewModel.open(RouteEndpoint.DESTINATION)
            settle(gate)

            assertEquals(RoutePickerState(endpoint = RouteEndpoint.DESTINATION), viewModel.state.value)
        }

    @Test fun `a newer lookup replaces the running one`() =
        runTest(dispatcher) {
            val gate = GateDispatcher()
            val viewModel = openedViewModel(ioDispatcher = gate)

            viewModel.search("alpha")
            runCurrent()
            viewModel.search("beta")
            settle(gate)

            assertEquals(listOf(beta), viewModel.state.value.results)
            assertFalse(viewModel.state.value.loading)
        }

    @Test fun `an explicit choice wins over the current city still being read`() =
        runTest(dispatcher) {
            save(departure = alpha.id, destination = null)
            val gate = GateDispatcher()
            val viewModel = viewModel(ioDispatcher = gate)

            viewModel.open(RouteEndpoint.DEPARTURE)
            runCurrent()
            viewModel.select(beta)
            settle(gate)

            assertEquals(beta, viewModel.state.value.selected)
        }

    @Test fun `actions of a closed picker are ignored`() =
        runTest(dispatcher) {
            val source = FakeCityDataSource(cities)
            val viewModel = viewModel(source)

            viewModel.updateQuery("beta")
            viewModel.search("beta")
            viewModel.nearest(NearbyCoordinate(0.0, 0.9))
            viewModel.select(beta)
            runCurrent()

            assertEquals(RoutePickerState(), viewModel.state.value)
            assertEquals(emptyList<Boolean>(), source.reads)
        }

    @Test fun `endpoint, query and selection are restored from the saved state`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val first = viewModel(savedStateHandle = handle)
            first.open(RouteEndpoint.DESTINATION)
            first.updateQuery("Bet")
            first.select(beta)
            runCurrent()

            val restored = viewModel(savedStateHandle = handle)
            assertEquals(RoutePickerState(endpoint = RouteEndpoint.DESTINATION, query = "Bet"), restored.state.value)
            runCurrent()

            assertEquals(
                RoutePickerState(endpoint = RouteEndpoint.DESTINATION, query = "Bet", selected = beta),
                restored.state.value,
            )
        }

    @Test fun `the nearest city is restored as the selection`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val first = viewModel(savedStateHandle = handle)
            first.open(RouteEndpoint.DEPARTURE)
            first.nearest(NearbyCoordinate(0.0, 0.9))
            runCurrent()

            val restored = viewModel(savedStateHandle = handle)
            runCurrent()

            assertEquals(beta, restored.state.value.selected)
        }

    @Test fun `a closed picker stays closed after restoration`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val first = viewModel(savedStateHandle = handle)
            first.open(RouteEndpoint.DEPARTURE)
            first.updateQuery("Alpha")
            first.select(alpha)
            first.close()

            val restored = viewModel(savedStateHandle = handle)
            runCurrent()

            assertEquals(RoutePickerState(), restored.state.value)
        }

    private fun TestScope.viewModel(
        source: CityDataSource = FakeCityDataSource(cities),
        ioDispatcher: CoroutineDispatcher = dispatcher,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = RoutePickerViewModel(
        savedStateHandle,
        CityRepository(source, ioDispatcher),
        RouteRepository(preferences, backgroundScope),
    )

    private fun TestScope.openedViewModel(
        source: CityDataSource = FakeCityDataSource(cities),
        ioDispatcher: CoroutineDispatcher = dispatcher,
        endpoint: RouteEndpoint = RouteEndpoint.DEPARTURE,
    ): RoutePickerViewModel =
        viewModel(source, ioDispatcher).also {
            it.open(endpoint)
            runCurrent()
        }

    private fun save(
        departure: Long?,
        destination: Long?,
    ) {
        preferences
            .edit()
            .apply {
                departure?.let { putLong(DEPARTURE, it) }
                destination?.let { putLong(DESTINATION, it) }
            }.commit()
    }

    /** Runs gated IO work and what it resumes until nothing is left. */
    private fun TestScope.settle(gate: GateDispatcher) {
        runCurrent()
        while (gate.pending.isNotEmpty()) {
            gate.releaseAll()
            runCurrent()
        }
    }

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

    private companion object {
        const val DEPARTURE = "route_departure_id"
        const val DESTINATION = "route_destination_id"
    }
}
