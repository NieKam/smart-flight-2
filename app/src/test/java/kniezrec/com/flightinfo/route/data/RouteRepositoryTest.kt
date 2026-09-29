package kniezrec.com.flightinfo.route.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.route.RouteEndpoint
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RouteRepositoryTest {
    private val preferences: SharedPreferences =
        ApplicationProvider
            .getApplicationContext<Context>()
            .getSharedPreferences("route", Context.MODE_PRIVATE)

    @Test
    fun noSavedRouteHasNoEndpoints() =
        runTest {
            assertEquals(RouteEndpointIds(), repository().endpoints.value)
        }

    @Test
    fun savedIdsAreReadAndMinValueMeansUnset() =
        runTest {
            preferences
                .edit()
                .putLong(DEPARTURE, Long.MIN_VALUE)
                .putLong(DESTINATION, 7L)
                .commit()

            assertEquals(RouteEndpointIds(departureId = null, destinationId = 7L), repository().endpoints.value)
        }

    @Test
    fun setPublishesAtOnceAndPersistsUnderTheSameKeys() =
        runTest {
            val repository = repository()

            repository.set(RouteEndpoint.DEPARTURE, 11L)
            repository.set(RouteEndpoint.DESTINATION, 12L)

            assertEquals(RouteEndpointIds(11L, 12L), repository.endpoints.value)
            assertEquals(11L, preferences.getLong(DEPARTURE, Long.MIN_VALUE))
            assertEquals(12L, preferences.getLong(DESTINATION, Long.MIN_VALUE))
            // A new instance (e.g. after process restart) reads the same route back.
            assertEquals(RouteEndpointIds(11L, 12L), repository().endpoints.value)
        }

    @Test
    fun setReplacesOnlyTheGivenEndpoint() =
        runTest {
            val repository = repository()
            repository.set(RouteEndpoint.DEPARTURE, 11L)
            repository.set(RouteEndpoint.DESTINATION, 12L)

            repository.set(RouteEndpoint.DEPARTURE, 21L)

            assertEquals(RouteEndpointIds(21L, 12L), repository.endpoints.value)
            assertEquals(21L, repository.endpoints.value[RouteEndpoint.DEPARTURE])
            assertEquals(12L, repository.endpoints.value[RouteEndpoint.DESTINATION])
        }

    @Test
    fun clearRemovesOnlyThatEndpoint() =
        runTest {
            val repository = repository()
            repository.set(RouteEndpoint.DEPARTURE, 11L)
            repository.set(RouteEndpoint.DESTINATION, 12L)

            repository.clear(RouteEndpoint.DEPARTURE)

            assertEquals(RouteEndpointIds(destinationId = 12L), repository.endpoints.value)
            assertFalse(preferences.contains(DEPARTURE))
            assertEquals(12L, preferences.getLong(DESTINATION, Long.MIN_VALUE))
        }

    @Test
    fun clearAllRemovesBothEndpoints() =
        runTest {
            val repository = repository()
            repository.set(RouteEndpoint.DEPARTURE, 11L)
            repository.set(RouteEndpoint.DESTINATION, 12L)

            repository.clearAll()

            assertEquals(RouteEndpointIds(), repository.endpoints.value)
            assertFalse(preferences.contains(DEPARTURE))
            assertFalse(preferences.contains(DESTINATION))
            assertEquals(RouteEndpointIds(), repository().endpoints.value)
        }

    @Test
    fun writesThroughTheRepositoryAreEmitted() =
        runTest {
            val repository = repository()
            val emitted = mutableListOf<RouteEndpointIds>()
            backgroundScope.launch { repository.endpoints.toList(emitted) }
            runCurrent()

            repository.set(RouteEndpoint.DESTINATION, 12L)
            runCurrent()
            repository.clear(RouteEndpoint.DESTINATION)
            runCurrent()

            assertEquals(listOf(RouteEndpointIds(), RouteEndpointIds(destinationId = 12L), RouteEndpointIds()), emitted)
        }

    @Test
    fun changesWrittenElsewhereAreEmitted() =
        runTest {
            val repository = repository()
            val emitted = mutableListOf<RouteEndpointIds>()
            backgroundScope.launch { repository.endpoints.toList(emitted) }
            runCurrent()

            preferences.edit().putLong(DEPARTURE, 5L).commit()
            runCurrent()

            assertEquals(listOf(RouteEndpointIds(), RouteEndpointIds(departureId = 5L)), emitted)
        }

    private fun TestScope.repository(): RouteRepository = RouteRepository(preferences, backgroundScope).also { runCurrent() }

    private companion object {
        const val DEPARTURE = "route_departure_id"
        const val DESTINATION = "route_destination_id"
    }
}
