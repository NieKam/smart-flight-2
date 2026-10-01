package kniezrec.com.flightinfo.map

import kniezrec.com.flightinfo.flight.FlightLocationFix

/** Pure rules shared by the map ViewModel, the map card and unit tests. */
data class MapCoordinate(
    val latitude: Double,
    val longitude: Double,
) {
    companion object {
        fun from(fix: FlightLocationFix): MapCoordinate? {
            val lat = fix.latitude ?: return null
            val lon = fix.longitude ?: return null
            return if (lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0) {
                MapCoordinate(lat, lon)
            } else {
                null
            }
        }
    }
}

data class MapViewport(
    val center: MapCoordinate,
    val zoom: Double,
)

/**
 * Normalizes a course for the map marker rotation into [0, 360) degrees, keeping the fractional
 * part (e.g. 10.5 stays 10.5). Returns null for null, NaN or infinite input.
 *
 * Intentionally differs from [kniezrec.com.flightinfo.course.normalizeCourseDegrees], which floors
 * to whole degrees for the course card text; do not replace one with the other.
 */
fun normalizeMarkerCourse(course: Double?): Float? {
    if (course == null || !course.isFinite()) return null
    val normalized = ((course % 360.0) + 360.0) % 360.0
    return normalized.toFloat()
}

/**
 * Aircraft position on the map during one observation. [firstFix] is the first valid position of
 * the observation, the one the map centers on once. [marker] is the plane marker's heading, chosen
 * by [markerRotation] from the latest valid fix ([lastFix]) and the latest compass heading
 * ([compassHeadingDegrees]).
 */
data class MapTracking(
    val position: MapCoordinate? = null,
    val firstFix: MapCoordinate? = null,
    val lastFix: FlightLocationFix? = null,
    val compassHeadingDegrees: Double? = null,
    val marker: MarkerRotation = MarkerRotation(),
) {
    /** Heading of the plane marker, degrees clockwise from north. */
    val markerHeadingDegrees: Float get() = marker.headingDegrees

    /**
     * Moves to [fix]'s position and chooses the marker heading again. A fix without a valid
     * position changes nothing, not even the heading.
     */
    fun accept(fix: FlightLocationFix): MapTracking {
        val coordinate = MapCoordinate.from(fix) ?: return this
        return copy(
            position = coordinate,
            firstFix = firstFix ?: coordinate,
            lastFix = fix,
            marker = markerRotation(fix, compassHeadingDegrees, marker),
        )
    }

    /** A new compass heading (degrees clockwise from north, display-relative); chooses the marker heading again. */
    fun acceptCompass(headingDegrees: Double): MapTracking =
        copy(
            compassHeadingDegrees = headingDegrees,
            marker = markerRotation(lastFix, headingDegrees, marker),
        )
}

/** Viewport and zoom rules of the offline map. */
object MapRules {
    val DEFAULT_CENTER = MapCoordinate(32.0, -32.0)
    const val DEFAULT_ZOOM = 3.0
    const val FOLLOW_ZOOM = 6.0
    const val STANDARD_MAX_ZOOM = 6.0
    const val LARGER_MAX_ZOOM = 9.0

    /** Viewport of the recenter button: the latest [position] closer up, or the default view. */
    fun recenter(position: MapCoordinate?): MapViewport =
        if (position == null) MapViewport(DEFAULT_CENTER, DEFAULT_ZOOM) else MapViewport(position, FOLLOW_ZOOM)

    fun maxZoom(largerMapZoom: Boolean): Double = if (largerMapZoom) LARGER_MAX_ZOOM else STANDARD_MAX_ZOOM

    /** The max-zoom tip is shown at most this many times, as in the original app. */
    const val ZOOM_TIP_LIMIT = 4

    /** [zoom] is at (or beyond) the standard maximum: reaching it is what may show the max-zoom tip. */
    fun isAtStandardMaximum(zoom: Double): Boolean = zoom >= STANDARD_MAX_ZOOM

    /**
     * The max-zoom tip ("force bigger in settings") may be shown: "larger map zoom" is off and the
     * tip was shown fewer than [ZOOM_TIP_LIMIT] times.
     */
    fun shouldShowZoomTip(
        largerMapZoom: Boolean,
        shownCount: Int,
    ): Boolean = !largerMapZoom && shownCount < ZOOM_TIP_LIMIT

    fun reconcileZoom(
        currentZoom: Double,
        largerMapZoom: Boolean,
    ): Double = currentZoom.coerceAtMost(maxZoom(largerMapZoom))
}

fun applyMapZoomPolicy(
    target: MapZoomTarget,
    largerMapZoom: Boolean,
): Boolean {
    val maxZoom = MapRules.maxZoom(largerMapZoom)
    if (target.maxZoomLevel == maxZoom) return false
    if (!largerMapZoom && target.zoomLevel > maxZoom) {
        target.setZoom(MapRules.reconcileZoom(target.zoomLevel, largerMapZoom))
    }
    target.maxZoomLevel = maxZoom
    target.invalidate()
    return true
}

interface MapZoomTarget {
    var maxZoomLevel: Double
    val zoomLevel: Double

    fun setZoom(zoom: Double)

    fun invalidate()
}
