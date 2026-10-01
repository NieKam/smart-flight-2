package kniezrec.com.flightinfo.map

import kniezrec.com.flightinfo.flight.FlightLocationFix

/** Where the plane marker's heading comes from. */
enum class MarkerHeadingSource {
    /** No heading known yet: the marker points north. */
    None,

    /** GPS track (bearing of a fix while moving). */
    GpsTrack,

    /** Compass heading from the orientation sensor (standing still, or no usable track). */
    Compass,
}

/**
 * Heading of the plane marker in degrees clockwise from north, in [0, 360), and its [source]. The
 * map is always north-up, so this is the absolute heading drawn on the map; convert it with
 * [osmdroidMarkerRotation] before handing it to osmdroid.
 */
data class MarkerRotation(
    val headingDegrees: Float = 0f,
    val source: MarkerHeadingSource = MarkerHeadingSource.None,
)

/** Speed from which the GPS track is used (about 7 km/h: below it the GPS bearing is unreliable). */
const val GPS_TRACK_MIN_SPEED_METRES_PER_SECOND = 2.0

/** Speed below which a marker following the GPS track switches back to the compass (hysteresis). */
const val GPS_TRACK_RELEASE_SPEED_METRES_PER_SECOND = 1.5

/**
 * Chooses the plane marker's heading:
 * - the GPS bearing of [fix] when it has one and moves at least
 *   [GPS_TRACK_MIN_SPEED_METRES_PER_SECOND] (or, while [previous] already follows the GPS track, at
 *   least [GPS_TRACK_RELEASE_SPEED_METRES_PER_SECOND], so the source does not flicker around the
 *   threshold);
 * - otherwise the compass heading [compassHeadingDegrees] (display-relative, the Course card's value)
 *   when there is one;
 * - otherwise [previous], unchanged.
 */
fun markerRotation(
    fix: FlightLocationFix?,
    compassHeadingDegrees: Double?,
    previous: MarkerRotation,
): MarkerRotation {
    val bearing = normalizeMarkerCourse(fix?.bearingDegrees)
    val speed = fix?.speedMetresPerSecond?.takeIf { it.isFinite() }
    val threshold =
        if (previous.source == MarkerHeadingSource.GpsTrack) {
            GPS_TRACK_RELEASE_SPEED_METRES_PER_SECOND
        } else {
            GPS_TRACK_MIN_SPEED_METRES_PER_SECOND
        }
    if (bearing != null && speed != null && speed >= threshold) {
        return MarkerRotation(bearing, MarkerHeadingSource.GpsTrack)
    }
    val compass = normalizeMarkerCourse(compassHeadingDegrees)
    if (compass != null) return MarkerRotation(compass, MarkerHeadingSource.Compass)
    return previous
}

/**
 * Converts a heading (degrees clockwise from north) to osmdroid's `Marker.rotation`.
 *
 * osmdroid 6.1.20 draws a marker rotated on screen by `-mapOrientation - rotation`
 * (`Marker.draw`), and `Canvas.rotate` turns clockwise for positive angles, so `Marker.rotation`
 * turns the icon counter-clockwise. A heading of 90° (east) is therefore rotation 270°.
 */
fun osmdroidMarkerRotation(headingDegrees: Float): Float {
    if (!headingDegrees.isFinite()) return 0f
    val rotation = (360f - headingDegrees % 360f) % 360f
    return if (rotation < 0f) rotation + 360f else rotation
}
