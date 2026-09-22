package kniezrec.com.flightinfo.map

import kniezrec.com.flightinfo.flight.FlightLocationFix

/** Pure rules shared by the map presenter and unit tests. */
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

fun normalizeCourse(course: Double?): Float? {
    if (course == null || !course.isFinite()) return null
    val normalized = ((course % 360.0) + 360.0) % 360.0
    return normalized.toFloat()
}

class MapSessionRules {
    var latestPosition: MapCoordinate? = null
        private set
    var markerCourse: Float = 0f
        private set
    var hasCenteredOnFirstFix: Boolean = false
        private set
    private var firstFixCenterPending = false

    fun accept(fix: FlightLocationFix): Boolean {
        val coordinate = MapCoordinate.from(fix) ?: return false
        latestPosition = coordinate
        if (!hasCenteredOnFirstFix) {
            hasCenteredOnFirstFix = true
            firstFixCenterPending = true
        }
        markerCourse = normalizeCourse(fix.bearingDegrees) ?: 0f
        return true
    }

    fun consumeFirstFixCenter(): MapCoordinate? =
        if (firstFixCenterPending) {
            firstFixCenterPending = false
            latestPosition
        } else {
            null
        }

    fun reset() {
        latestPosition = null
        markerCourse = 0f
        hasCenteredOnFirstFix = false
        firstFixCenterPending = false
    }

    fun recenter(): MapViewport = MapViewport(latestPosition ?: DEFAULT_CENTER, if (latestPosition == null) DEFAULT_ZOOM else FOLLOW_ZOOM)

    companion object {
        val DEFAULT_CENTER = MapCoordinate(32.0, -32.0)
        const val DEFAULT_ZOOM = 3.0
        const val FOLLOW_ZOOM = 6.0
        const val STANDARD_MAX_ZOOM = 6.0
        const val LARGER_MAX_ZOOM = 9.0

        fun maxZoom(largerMapZoom: Boolean): Double = if (largerMapZoom) LARGER_MAX_ZOOM else STANDARD_MAX_ZOOM

        fun shouldShowMaximumZoomWarning(
            currentZoom: Double,
            largerMapZoom: Boolean,
        ): Boolean = !largerMapZoom && currentZoom >= STANDARD_MAX_ZOOM

        fun reconcileZoom(
            currentZoom: Double,
            largerMapZoom: Boolean,
        ): Double = currentZoom.coerceAtMost(maxZoom(largerMapZoom))
    }
}
