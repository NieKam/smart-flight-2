package kniezrec.com.flightinfo.route

import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.NearbyCoordinate
import kniezrec.com.flightinfo.nearby.distanceKilometres
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

enum class RouteEndpoint { DEPARTURE, DESTINATION }

data class RouteOverlay(
    val departure: NearbyCoordinate,
    val destination: NearbyCoordinate,
    val departureName: String,
    val destinationName: String,
)

data class RouteDetails(
    val fixedDistanceKm: Double,
    val remainingDistanceKm: Double?,
    val arrival: String?,
    val duration: String?,
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

fun normalizeCityQuery(query: String): String = query.trim().lowercase(Locale.ROOT)

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
    distanceKilometres(
        NearbyCoordinate(departure.latitude, departure.longitude),
        NearbyCoordinate(destination.latitude, destination.longitude),
    )

fun routeDetails(
    departure: NearbyCityRecord,
    destination: NearbyCityRecord,
    fix: RouteFix?,
    now: Instant = Instant.now(),
): RouteDetails {
    val fixed = routeDistance(departure, destination)
    val remaining = fix?.let { distanceKilometres(it.coordinate, NearbyCoordinate(destination.latitude, destination.longitude)) }
    val speed = fix?.speedMetresPerSecond?.takeIf { it.isFinite() && it > 0.0 }
    val durationSeconds =
        if (remaining != null &&
            speed != null
        ) {
            (remaining * 1000.0 / speed).takeIf(Double::isFinite)?.roundToInt()?.toLong()
        } else {
            null
        }
    val arrival =
        durationSeconds?.let {
            DateTimeFormatter
                .ofLocalizedDateTime(
                    FormatStyle.SHORT,
                ).withLocale(Locale.getDefault())
                .format(now.plusSeconds(it).atZone(ZoneId.of(destination.timeZoneId)))
        }
    val duration =
        durationSeconds?.let {
            Duration.ofSeconds(it).let { d ->
                "%02d:%02d".format(Locale.ROOT, d.toHours(), d.toMinutesPart())
            }
        }
    return RouteDetails(fixed, remaining, arrival, duration)
}

fun formatKilometres(value: Double): String =
    NumberFormat
        .getNumberInstance(Locale.getDefault())
        .apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
        }.format(value) + " km"
