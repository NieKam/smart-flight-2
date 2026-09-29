package kniezrec.com.flightinfo.nearby

import java.time.Instant
import java.time.ZoneId

sealed interface NearbyCityState {
    data object WaitingForPosition : NearbyCityState

    data object LookingUp : NearbyCityState

    /**
     * The nearest city. [instant] is the moment of the lookup and [zoneId] the city's time zone; the
     * UI formats the local time from them with the current locale. [utcOffsetSeconds] is the zone's
     * offset at [instant] (daylight saving time included).
     */
    data class Available(
        val cityName: String,
        val country: String,
        val distanceKilometres: Double,
        val zoneId: ZoneId,
        val instant: Instant,
        val utcOffsetSeconds: Int,
    ) : NearbyCityState

    data object Unavailable : NearbyCityState
}

data class NearbyCoordinate(
    val latitude: Double,
    val longitude: Double,
) {
    companion object {
        fun from(
            latitude: Double?,
            longitude: Double?,
        ): NearbyCoordinate? =
            latitude?.takeIf(Double::isFinite)?.let { validLatitude ->
                longitude?.takeIf(Double::isFinite)?.takeIf { it in -180.0..180.0 }?.let { validLongitude ->
                    validLatitude.takeIf { it in -90.0..90.0 }?.let { NearbyCoordinate(it, validLongitude) }
                }
            }
    }
}

data class NearbyCityRecord(
    val id: Long,
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
)

/** Mean Earth radius used by [distanceKilometres]. */
internal const val EARTH_RADIUS_KILOMETRES = 6_371.0088

/** Great-circle (haversine) distance on a sphere of [EARTH_RADIUS_KILOMETRES]. */
internal fun distanceKilometres(
    first: NearbyCoordinate,
    second: NearbyCoordinate,
): Double {
    val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
    val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
    val a =
        kotlin.math.sin(latitudeDelta / 2).let { it * it } +
            kotlin.math.cos(Math.toRadians(first.latitude)) * kotlin.math.cos(Math.toRadians(second.latitude)) *
            kotlin.math.sin(longitudeDelta / 2).let { it * it }
    return (2 * EARTH_RADIUS_KILOMETRES * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))).takeIf(Double::isFinite)
        ?: throw IllegalArgumentException("Invalid distance")
}
