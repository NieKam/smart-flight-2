package kniezrec.com.flightinfo.route

import android.content.SharedPreferences
import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.NearbyCityRepository
import kniezrec.com.flightinfo.nearby.NearbyCoordinate
import java.time.Instant
import java.util.concurrent.Executor

class RouteController(private val repository: NearbyCityRepository, private val preferences: SharedPreferences, private val worker: Executor, private val callbackExecutor: Executor, private val onStateChanged: (RouteState) -> Unit, private val clock: () -> Instant = { Instant.now() }) {
    private var active = false
    private var session = 0L
    private var departure: NearbyCityRecord? = null
    private var destination: NearbyCityRecord? = null
    private var fix: RouteFix? = null

    fun start() { active = true; session++; restore(session) }
    fun stop() { active = false; session++; fix = null; publish() }
    fun onFix(value: FlightLocationFix) { if (!active) return; val coordinate = NearbyCoordinate.from(value.latitude, value.longitude) ?: return; fix = RouteFix(coordinate, value.speedMetresPerSecond); publish() }
    fun choose(endpoint: RouteEndpoint, city: NearbyCityRecord) { if (!validCity(city)) return; if (endpoint == RouteEndpoint.DEPARTURE) departure = city else destination = city; persist(); publish() }
    fun clear(endpoint: RouteEndpoint) { if (endpoint == RouteEndpoint.DEPARTURE) departure = null else destination = null; persist(); publish() }
    fun clearRoute() { departure = null; destination = null; persist(); publish() }
    fun search(query: String, reload: Boolean = false, result: (Result<List<NearbyCityRecord>>) -> Unit) { val token = session; worker.execute { val answer = runCatching { repository.searchByName(normalizeCityQuery(query), reload) }; callbackExecutor.execute { if (active && token == session) result(answer) } } }
    fun nearest(coordinate: NearbyCoordinate, result: (Result<NearbyCityRecord?>) -> Unit) { val token = session; worker.execute { val answer = runCatching { repository.findNearest(coordinate) }; callbackExecutor.execute { if (active && token == session) result(answer) } } }
    private fun restore(token: Long) { worker.execute { val d = preferences.getLong(DEPARTURE, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let { runCatching { repository.findById(it) }.getOrNull() }; val a = preferences.getLong(DESTINATION, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let { runCatching { repository.findById(it) }.getOrNull() }; callbackExecutor.execute { if (!active || token != session) return@execute; departure = d?.takeIf(::validCity); destination = a?.takeIf(::validCity); persist(); publish() } } }
    private fun persist() { preferences.edit().apply { if (departure == null) remove(DEPARTURE) else putLong(DEPARTURE, departure!!.id); if (destination == null) remove(DESTINATION) else putLong(DESTINATION, destination!!.id) }.commit() }
    private fun publish() { val d = departure; val a = destination; onStateChanged(RouteState(d, a, if (d != null && a != null) routeDetails(d, a, fix, clock()) else null, routeOverlay(d, a))) }
    private companion object { const val DEPARTURE = "route_departure_id"; const val DESTINATION = "route_destination_id" }
}
