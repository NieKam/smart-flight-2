package kniezrec.com.flightinfo.route

import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.NearbyCoordinate
import kniezrec.com.flightinfo.nearby.ellipsoidalDistanceKm
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

enum class RouteEndpoint { DEPARTURE, DESTINATION }

data class RouteOverlay(
    val departure: NearbyCoordinate,
    val destination: NearbyCoordinate,
    val departureName: String,
    val destinationName: String,
)

/**
 * Route values; the UI formats them. [fixedDistanceKm] (between the cities) exists only with a
 * departure. [remainingDistanceKm] is known with a position; [arrival] and [duration] with a position
 * and a positive speed; [arrival] is shown in [destinationZone].
 */
data class RouteDetails(
    val fixedDistanceKm: Double?,
    val remainingDistanceKm: Double?,
    val arrival: Instant?,
    val destinationZone: ZoneId,
    val duration: Duration?,
)

data class RouteState(
    val departure: NearbyCityRecord? = null,
    val destination: NearbyCityRecord? = null,
    val details: RouteDetails? = null,
    val overlay: RouteOverlay? = null,
    val error: RouteError? = null,
)

enum class RouteError {
    RESTORE,
}

data class RouteFix(
    val coordinate: NearbyCoordinate,
    val speedMetresPerSecond: Double?,
)

fun validCity(city: NearbyCityRecord): Boolean =
    NearbyCoordinate.from(city.latitude, city.longitude) != null && runCatching { ZoneId.of(city.timeZoneId) }.isSuccess

fun routeOverlay(
    departure: NearbyCityRecord?,
    destination: NearbyCityRecord?,
): RouteOverlay? =
    if (departure != null && destination != null && validCity(departure) && validCity(destination)) {
        RouteOverlay(
            NearbyCoordinate(departure.latitude, departure.longitude),
            NearbyCoordinate(destination.latitude, destination.longitude),
            departure.name,
            destination.name,
        )
    } else {
        null
    }

fun routeDistance(
    departure: NearbyCityRecord,
    destination: NearbyCityRecord,
): Double =
    ellipsoidalDistanceKm(
        NearbyCoordinate(departure.latitude, departure.longitude),
        NearbyCoordinate(destination.latitude, destination.longitude),
    )

/**
 * Details of the route to a [validCity] [destination] at [now], with the latest [fix] if any. The
 * departure is optional (as in the original app): it only adds the distance between the cities.
 */
fun routeDetails(
    departure: NearbyCityRecord?,
    destination: NearbyCityRecord,
    fix: RouteFix?,
    now: Instant,
): RouteDetails {
    val fixed = departure?.let { routeDistance(it, destination) }
    val remaining = fix?.let { ellipsoidalDistanceKm(it.coordinate, NearbyCoordinate(destination.latitude, destination.longitude)) }
    val speed = fix?.speedMetresPerSecond?.takeIf { it.isFinite() && it > 0.0 }
    val durationSeconds =
        if (remaining != null &&
            speed != null
        ) {
            (remaining * 1000.0 / speed).takeIf(Double::isFinite)?.roundToInt()?.toLong()
        } else {
            null
        }
    val duration = durationSeconds?.let(Duration::ofSeconds)
    return RouteDetails(fixed, remaining, duration?.let { now.plus(it) }, ZoneId.of(destination.timeZoneId), duration)
}
