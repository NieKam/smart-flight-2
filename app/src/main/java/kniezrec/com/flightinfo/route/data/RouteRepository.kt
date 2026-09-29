package kniezrec.com.flightinfo.route.data

import android.content.SharedPreferences
import kniezrec.com.flightinfo.data.observedState
import kniezrec.com.flightinfo.di.ApplicationScope
import kniezrec.com.flightinfo.route.RouteEndpoint
import kniezrec.com.flightinfo.route.data.di.RoutePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** City ids of the saved route endpoints; null when an endpoint is not set. */
data class RouteEndpointIds(
    val departureId: Long? = null,
    val destinationId: Long? = null,
) {
    operator fun get(endpoint: RouteEndpoint): Long? =
        when (endpoint) {
            RouteEndpoint.DEPARTURE -> departureId
            RouteEndpoint.DESTINATION -> destinationId
        }
}

/**
 * The saved route: the city ids of its departure and destination, in the `route` file.
 *
 * Writes use `apply()` (the disk write happens off the calling thread); [endpoints] holds a written
 * value as soon as the writing function returns. Changes of the file made elsewhere are emitted too.
 */
@Singleton
class RouteRepository
    @Inject
    constructor(
        @RoutePreferences private val preferences: SharedPreferences,
        @ApplicationScope scope: CoroutineScope,
    ) {
        private val state = preferences.observedState(scope) { readIds() }

        val endpoints: StateFlow<RouteEndpointIds> = state.asStateFlow()

        suspend fun set(
            endpoint: RouteEndpoint,
            id: Long,
        ) {
            edit { putLong(endpoint.key, id) }
        }

        suspend fun clear(endpoint: RouteEndpoint) {
            edit { remove(endpoint.key) }
        }

        suspend fun clearAll() {
            edit {
                remove(KEY_DEPARTURE)
                remove(KEY_DESTINATION)
            }
        }

        private fun edit(change: SharedPreferences.Editor.() -> Unit) {
            synchronized(this) {
                preferences.edit().apply(change).apply()
                // apply() updates the in-memory values at once; the disk write follows in the background.
                state.value = preferences.readIds()
            }
        }

        private companion object {
            const val KEY_DEPARTURE = "route_departure_id"
            const val KEY_DESTINATION = "route_destination_id"

            /** Read default of a missing key; a stored [UNSET] also means "not set". */
            const val UNSET = Long.MIN_VALUE

            val RouteEndpoint.key: String
                get() =
                    when (this) {
                        RouteEndpoint.DEPARTURE -> KEY_DEPARTURE
                        RouteEndpoint.DESTINATION -> KEY_DESTINATION
                    }

            fun SharedPreferences.readIds(): RouteEndpointIds =
                RouteEndpointIds(
                    departureId = readId(KEY_DEPARTURE),
                    destinationId = readId(KEY_DESTINATION),
                )

            fun SharedPreferences.readId(key: String): Long? = getLong(key, UNSET).takeIf { it != UNSET }
        }
    }
