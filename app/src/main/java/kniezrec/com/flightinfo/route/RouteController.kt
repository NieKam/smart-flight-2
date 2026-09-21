package kniezrec.com.flightinfo.route

import android.content.SharedPreferences
import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.NearbyCityRepository
import kniezrec.com.flightinfo.nearby.NearbyCoordinate
import java.time.Instant
import java.util.concurrent.Executor

class RouteController(
    private val repository: NearbyCityRepository,
    private val preferences: SharedPreferences,
    private val worker: Executor,
    private val callbackExecutor: Executor,
    private val onStateChanged: (RouteState) -> Unit,
    private val clock: () -> Instant = {
        Instant.now()
    },
) {
    private var active = false
    private var session = 0L
    private var departure: NearbyCityRecord? = null
    private var destination: NearbyCityRecord? = null
    private var fix: RouteFix? = null

    fun start() {
        active = true
        session++
        restore(session, false)
    }

    fun stop() {
        active = false
        session++
        fix = null
        publish()
    }

    fun onFix(value: FlightLocationFix) {
        if (!active) return
        val coordinate =
            NearbyCoordinate.from(value.latitude, value.longitude) ?: return
        fix = RouteFix(coordinate, value.speedMetresPerSecond)
        publish()
    }

    fun choose(
        endpoint: RouteEndpoint,
        city: NearbyCityRecord,
    ) {
        if (!validCity(city)) return
        if (endpoint ==
            RouteEndpoint.DEPARTURE
        ) {
            departure = city
        } else {
            destination = city
        }
        persist()
        publish()
    }

    fun clear(endpoint: RouteEndpoint) {
        if (endpoint ==
            RouteEndpoint.DEPARTURE
        ) {
            departure = null
        } else {
            destination = null
        }
        persist()
        publish()
    }

    fun clearRoute() {
        departure = null
        destination = null
        persist()
        publish()
    }

    fun search(
        query: String,
        reload: Boolean = false,
        result: (Result<List<NearbyCityRecord>>) -> Unit,
    ) {
        val token = session
        worker.execute {
            val answer = runCatching { repository.searchByName(normalizeCityQuery(query), reload) }
            callbackExecutor.execute {
                if (active &&
                    token == session
                ) {
                    result(answer)
                }
            }
        }
    }

    fun nearest(
        coordinate: NearbyCoordinate,
        result: (Result<NearbyCityRecord?>) -> Unit,
    ) {
        val token = session
        worker.execute {
            val answer = runCatching { repository.findNearest(coordinate) }
            callbackExecutor.execute {
                if (active &&
                    token == session
                ) {
                    result(answer)
                }
            }
        }
    }

    private fun restore(token: Long, reload: Boolean) {
        worker.execute {
            var readFailed = false
            fun resolve(id: Long): NearbyCityRecord? = try {
                repository.findById(id, reload)
            } catch (_: Exception) {
                readFailed = true
                null
            }
            val departureId = preferences.getLong(DEPARTURE, Long.MIN_VALUE)
            val destinationId = preferences.getLong(DESTINATION, Long.MIN_VALUE)
            val restoredDeparture = departureId.takeIf { it != Long.MIN_VALUE }?.let(::resolve)
            val restoredDestination = destinationId.takeIf { it != Long.MIN_VALUE }?.let(::resolve)
            callbackExecutor.execute {
                if (!active || token != session) return@execute
                if (!readFailed) {
                    departure = restoredDeparture?.takeIf(::validCity)
                    destination = restoredDestination?.takeIf(::validCity)
                    persist()
                    publish()
                } else {
                    publish(ROUTE_RESTORE_ERROR)
                }
            }
        }
    }

    fun retryRestore() {
        if (active) restore(session, true)
    }

    private fun persist() {
        preferences
            .edit()
            .apply {
                if (departure ==
                    null
                ) {
                    remove(DEPARTURE)
                } else {
                    putLong(DEPARTURE, departure!!.id)
                }
                ; if (destination ==
                    null
                ) {
                    remove(DESTINATION)
                } else {
                    putLong(DESTINATION, destination!!.id)
                }
            }.commit()
    }

    private fun publish(error: String? = null) {
        val d = departure
        val a = destination
        onStateChanged(
            RouteState(
                d,
                a,
                if (d != null &&
                    a != null
                ) {
                    routeDetails(d, a, fix, clock())
                } else {
                    null
                },
                routeOverlay(d, a),
                error,
            ),
        )
    }

    private companion object {
        const val DEPARTURE = "route_departure_id"
        const val DESTINATION = "route_destination_id"
        const val ROUTE_RESTORE_ERROR = "Unable to restore saved route. Retry to read city data."
    }
}
