package kniezrec.com.flightinfo.route.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.data.CityDataSource
import kniezrec.com.flightinfo.nearby.data.CityRepository
import kniezrec.com.flightinfo.route.RouteEndpoint
import kniezrec.com.flightinfo.route.RouteError
import kniezrec.com.flightinfo.route.RouteState
import kniezrec.com.flightinfo.route.data.RouteRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.coroutines.CoroutineContext

/** Ports every case of the former `RouteControllerTest`, over the real `route` preferences file. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RouteViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val location = FakeLocationDataSource()
    private val clock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC)
    private val preferences: SharedPreferences =
        ApplicationProvider
            .getApplicationContext<Context>()
            .getSharedPreferences("route", Context.MODE_PRIVATE)

    private val alpha = NearbyCityRecord(1L, "Alpha", "A", 0.0, 0.0, "UTC")
    private val beta = NearbyCityRecord(2L, "Beta", "B", 0.0, 1.0, "Europe/Berlin")
    private val badCoordinates = NearbyCityRecord(3L, "Bad coordinates", "C", 200.0, 0.0, "UTC")
    private val badZone = NearbyCityRecord(4L, "Bad zone", "D", 0.0, 2.0, "Not/AZone")
    private val cities = listOf(alpha, beta, badCoordinates, badZone)
    private lateinit var routeRepository: RouteRepository

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `saved endpoints are restored with overlay and details`() =
        runTest(dispatcher) {
            save(departure = alpha.id, destination = beta.id)
            val viewModel = viewModel()
            subscribe(viewModel)

            val state = viewModel.state.value
            assertEquals(alpha, state.departure)
            assertEquals(beta, state.destination)
            assertNotNull(state.overlay)
            assertEquals(111.2, state.details!!.fixedDistanceKm!!, 0.1)
            assertNull(state.details!!.remainingDistanceKm)
            assertNull(state.error)
        }

    @Test fun `an unknown saved id is dropped and removed from the saved route`() =
        runTest(dispatcher) {
            save(departure = 99L, destination = beta.id)
            val viewModel = viewModel()
            subscribe(viewModel)

            assertNull(viewModel.state.value.departure)
            assertEquals(beta, viewModel.state.value.destination)
            assertFalse(preferences.contains(DEPARTURE))
            assertEquals(beta.id, preferences.getLong(DESTINATION, Long.MIN_VALUE))
        }

    @Test fun `saved cities with bad coordinates or time zone are dropped and removed`() =
        runTest(dispatcher) {
            save(departure = badCoordinates.id, destination = badZone.id)
            val viewModel = viewModel()
            subscribe(viewModel)

            assertEquals(RouteState(), viewModel.state.value)
            assertFalse(preferences.contains(DEPARTURE))
            assertFalse(preferences.contains(DESTINATION))
        }

    @Test fun `a failed restore shows the error and retry reloads the city data`() =
        runTest(dispatcher) {
            save(departure = alpha.id, destination = beta.id)
            val source = FakeCityDataSource(cities, failuresLeft = 1)
            val viewModel = viewModel(source)
            subscribe(viewModel)

            assertEquals(RouteState(error = RouteError.RESTORE), viewModel.state.value)
            // A read failure keeps the saved route.
            assertEquals(alpha.id, preferences.getLong(DEPARTURE, Long.MIN_VALUE))

            viewModel.retryRestore()
            runCurrent()

            assertEquals(alpha, viewModel.state.value.departure)
            assertEquals(beta, viewModel.state.value.destination)
            assertNull(viewModel.state.value.error)
            assertEquals(listOf(false, true), source.reads)
        }

    @Test fun `a failed retry keeps the error`() =
        runTest(dispatcher) {
            save(departure = alpha.id, destination = null)
            val viewModel = viewModel(FakeCityDataSource(cities, failuresLeft = 2))
            subscribe(viewModel)

            viewModel.retryRestore()
            runCurrent()

            assertEquals(RouteError.RESTORE, viewModel.state.value.error)
        }

    @Test fun `a chosen route is restored by a new view model`() =
        runTest(dispatcher) {
            val first = viewModel()
            subscribe(first)
            choose(RouteEndpoint.DEPARTURE, alpha)
            choose(RouteEndpoint.DESTINATION, beta)
            runCurrent()
            assertNotNull(first.state.value.overlay)

            val next = viewModel()
            subscribe(next)

            assertEquals(alpha, next.state.value.departure)
            assertEquals(beta, next.state.value.destination)
        }

    @Test fun `a destination alone gives remaining distance and arrival, the overlay needs both endpoints`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)
            choose(RouteEndpoint.DESTINATION, beta)
            runCurrent()
            fix(elapsedSeconds = 1, longitude = 0.0, speed = 100.0)

            val destinationOnly = viewModel.state.value
            assertEquals(beta, destinationOnly.destination)
            assertNull(destinationOnly.details!!.fixedDistanceKm)
            assertEquals(111.2, destinationOnly.details!!.remainingDistanceKm!!, 0.1)
            assertEquals(Duration.ofSeconds(1_112), destinationOnly.details!!.duration)
            assertNull(destinationOnly.overlay)

            choose(RouteEndpoint.DEPARTURE, alpha)
            runCurrent()

            assertEquals(
                111.2,
                viewModel.state.value.details!!
                    .fixedDistanceKm!!,
                0.1,
            )
            assertNotNull(viewModel.state.value.overlay)
        }

    @Test fun `a departure alone has no details`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)
            choose(RouteEndpoint.DEPARTURE, alpha)
            runCurrent()
            fix(elapsedSeconds = 1, longitude = 0.5, speed = 100.0)

            assertEquals(alpha, viewModel.state.value.departure)
            assertNull(viewModel.state.value.details)
            assertNull(viewModel.state.value.overlay)
        }

    @Test fun `clearing one endpoint removes only that saved id`() =
        runTest(dispatcher) {
            val viewModel = viewModelWithRoute()

            viewModel.clear(RouteEndpoint.DEPARTURE)
            runCurrent()

            assertNull(viewModel.state.value.departure)
            assertEquals(beta, viewModel.state.value.destination)
            assertFalse(preferences.contains(DEPARTURE))
            assertEquals(beta.id, preferences.getLong(DESTINATION, Long.MIN_VALUE))
        }

    @Test fun `clearing the departure keeps remaining distance and arrival`() =
        runTest(dispatcher) {
            val viewModel = viewModelWithRoute()
            fix(elapsedSeconds = 1, longitude = 0.0, speed = 100.0)

            viewModel.clear(RouteEndpoint.DEPARTURE)
            runCurrent()

            val details = viewModel.state.value.details!!
            assertNull(details.fixedDistanceKm)
            assertEquals(111.2, details.remainingDistanceKm!!, 0.1)
            assertEquals(clock.instant().plusSeconds(1_112), details.arrival)
            assertNull(viewModel.state.value.overlay)
        }

    @Test fun `clearing the route removes both endpoints and the overlay`() =
        runTest(dispatcher) {
            val viewModel = viewModelWithRoute()

            viewModel.clearAll()
            runCurrent()

            assertEquals(RouteState(), viewModel.state.value)
            assertFalse(preferences.contains(DEPARTURE))
            assertFalse(preferences.contains(DESTINATION))
        }

    @Test fun `a fix gives remaining distance, duration and arrival`() =
        runTest(dispatcher) {
            val viewModel = viewModelWithRoute()

            fix(elapsedSeconds = 1, longitude = 0.0, speed = 100.0)

            val details = viewModel.state.value.details!!
            assertEquals(111.2, details.remainingDistanceKm!!, 0.1)
            assertEquals(Duration.ofSeconds(1_112), details.duration)
            assertEquals(clock.instant().plusSeconds(1_112), details.arrival)
        }

    @Test fun `older and invalid fixes cannot replace the newest accepted fix`() =
        runTest(dispatcher) {
            val viewModel = viewModelWithRoute()
            fix(elapsedSeconds = 20, longitude = 0.5)
            val newest =
                viewModel.state.value.details!!
                    .remainingDistanceKm!!

            fix(elapsedSeconds = 10, longitude = 0.0)
            fix(elapsedSeconds = 20, longitude = 0.0)
            assertEquals(
                newest,
                viewModel.state.value.details!!
                    .remainingDistanceKm!!,
                0.0001,
            )

            // An invalid fix is not accepted, so it does not raise the timestamp bar either.
            location.emitFix(flightFix(elapsedSeconds = 30, latitude = Double.NaN, longitude = 0.0))
            runCurrent()
            assertEquals(
                newest,
                viewModel.state.value.details!!
                    .remainingDistanceKm!!,
                0.0001,
            )
            fix(elapsedSeconds = 30, longitude = 0.0)
            assertEquals(
                111.2,
                viewModel.state.value.details!!
                    .remainingDistanceKm!!,
                0.1,
            )
        }

    @Test fun `the latest endpoint change wins over a restore still running`() =
        runTest(dispatcher) {
            save(departure = alpha.id, destination = null)
            val gate = GateDispatcher()
            val viewModel = viewModel(ioDispatcher = gate)
            val states = mutableListOf<RouteState>()
            backgroundScope.launch { viewModel.state.collect { states += it } }
            runCurrent()
            assertTrue("the restore waits at the gate", gate.pending.isNotEmpty())

            choose(RouteEndpoint.DEPARTURE, beta)
            settle(gate)

            assertEquals(beta, viewModel.state.value.departure)
            assertFalse(states.any { it.departure == alpha })
            assertEquals(beta.id, preferences.getLong(DEPARTURE, Long.MIN_VALUE))
        }

    @Test fun `collecting again within the stop timeout keeps the position`() =
        runTest(dispatcher) {
            val viewModel = viewModelWithRoute()
            val first = subscribe(viewModel)
            fix(elapsedSeconds = 1, longitude = 0.5)

            first.cancel()
            advanceTimeBy(RouteViewModel.STOP_TIMEOUT_MILLIS - 1_000)
            subscribe(viewModel)

            assertNotNull(
                viewModel.state.value.details!!
                    .remainingDistanceKm,
            )
        }

    @Test fun `observation restarted after the stop timeout waits for a new position and accepts any fix`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val first = subscribe(viewModel)
            choose(RouteEndpoint.DEPARTURE, alpha)
            choose(RouteEndpoint.DESTINATION, beta)
            runCurrent()
            fix(elapsedSeconds = 20, longitude = 0.5)

            first.cancel()
            advanceTimeBy(RouteViewModel.STOP_TIMEOUT_MILLIS + 1)
            subscribe(viewModel)

            assertEquals(alpha, viewModel.state.value.departure)
            assertNull(
                viewModel.state.value.details!!
                    .remainingDistanceKm,
            )
            // The timestamp bar starts over with the observation.
            fix(elapsedSeconds = 10, longitude = 0.0)
            assertEquals(
                111.2,
                viewModel.state.value.details!!
                    .remainingDistanceKm!!,
                0.1,
            )
        }

    @Test fun `location switched off drops the position until a new fix arrives`() =
        runTest(dispatcher) {
            val viewModel = viewModelWithRoute()
            fix(elapsedSeconds = 1, longitude = 0.5)

            location.switchLocation(false)
            runCurrent()

            assertNull(
                viewModel.state.value.details!!
                    .remainingDistanceKm,
            )
            assertEquals(alpha, viewModel.state.value.departure)
            assertEquals(0, location.fixRegistrations.activeCount)

            location.switchLocation(true)
            runCurrent()
            fix(elapsedSeconds = 2, longitude = 0.5)
            assertEquals(
                55.6,
                viewModel.state.value.details!!
                    .remainingDistanceKm!!,
                0.1,
            )
        }

    @Test fun `a failed GPS registration still restores the route`() =
        runTest(dispatcher) {
            location.failFixRegistration = true
            save(departure = alpha.id, destination = beta.id)
            val viewModel = viewModel()
            subscribe(viewModel)

            assertEquals(alpha, viewModel.state.value.departure)
            assertNull(
                viewModel.state.value.details!!
                    .remainingDistanceKm,
            )
        }

    private fun TestScope.viewModel(
        source: CityDataSource = FakeCityDataSource(cities),
        ioDispatcher: CoroutineDispatcher = dispatcher,
    ) = RouteViewModel(
        LocationRepository(location, backgroundScope),
        CityRepository(source, ioDispatcher),
        RouteRepository(preferences, backgroundScope).also { routeRepository = it },
        clock,
    )

    /** Saves [city] as [endpoint] through the latest view model's repository, as the city picker does. */
    private suspend fun choose(
        endpoint: RouteEndpoint,
        city: NearbyCityRecord,
    ) = routeRepository.set(endpoint, city.id)

    private fun TestScope.viewModelWithRoute(): RouteViewModel {
        save(departure = alpha.id, destination = beta.id)
        return viewModel().also { subscribe(it) }
    }

    private fun TestScope.subscribe(viewModel: RouteViewModel): Job =
        backgroundScope.launch { viewModel.state.collect {} }.also { runCurrent() }

    private fun TestScope.fix(
        elapsedSeconds: Long,
        longitude: Double,
        speed: Double = 100.0,
    ) {
        location.emitFix(flightFix(speedMetresPerSecond = speed, elapsedSeconds = elapsedSeconds, latitude = 0.0, longitude = longitude))
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

    /** IO dispatcher that holds work until the test releases it, so a restore can be kept running. */
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
