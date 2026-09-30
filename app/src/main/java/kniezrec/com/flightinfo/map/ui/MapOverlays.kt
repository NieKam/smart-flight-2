package kniezrec.com.flightinfo.map.ui

import android.content.Context
import android.graphics.Paint
import androidx.core.content.ContextCompat
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.map.osmdroidMarkerRotation
import kniezrec.com.flightinfo.route.RouteOverlay
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow

/**
 * The plane marker and the route overlays of one [MapView].
 *
 * [sync] is idempotent and may run on every recomposition: the plane marker is created on the first
 * position and then only moved and rotated; the route line and its two pins are created once for a
 * route, moved when it changes and removed when it is cleared. The plane marker is always the last
 * overlay, so osmdroid draws it on top of the route line and the pins.
 */
class MapOverlays(
    private val context: Context,
    /** ARGB color of the route line, a palette token (`page`, the original `purple_dark`). */
    private val routeLineColor: Int,
) {
    /** The plane marker, once a position has been shown. */
    var planeMarker: Marker? = null
        private set

    private var routeLine: Polyline? = null
    private var departureMarker: Marker? = null
    private var destinationMarker: Marker? = null

    fun sync(
        map: MapView,
        state: MapUiState.Ready,
        routeOverlay: RouteOverlay?,
    ) {
        syncRoute(map, routeOverlay)
        syncPlane(map, state)
        keepPlaneOnTop(map)
        map.invalidate()
    }

    /** Forgets the overlays (the map they belong to is being discarded). */
    fun clear() {
        planeMarker = null
        routeLine = null
        departureMarker = null
        destinationMarker = null
    }

    private fun syncPlane(
        map: MapView,
        state: MapUiState.Ready,
    ) {
        val position = state.position ?: return
        val marker =
            planeMarker ?: Marker(map).also { marker ->
                marker.icon = ContextCompat.getDrawable(context, R.drawable.ic_plane_marker)
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                // No (empty) info window: a tap on the plane is consumed and does nothing.
                marker.setInfoWindow(null as MarkerInfoWindow?)
                marker.setOnMarkerClickListener { _, _ -> true }
                map.overlays.add(marker)
                planeMarker = marker
            }
        marker.position = GeoPoint(position.latitude, position.longitude)
        marker.rotation = osmdroidMarkerRotation(state.markerHeadingDegrees)
    }

    /** osmdroid draws overlays in list order: the plane goes last whenever something was added after it. */
    private fun keepPlaneOnTop(map: MapView) {
        val plane = planeMarker ?: return
        if (map.overlays.lastOrNull() === plane) return
        map.overlays.remove(plane)
        map.overlays.add(plane)
    }

    private fun syncRoute(
        map: MapView,
        route: RouteOverlay?,
    ) {
        if (route == null) {
            routeLine?.let { map.overlays.remove(it) }
            departureMarker?.let { map.overlays.remove(it) }
            destinationMarker?.let { map.overlays.remove(it) }
            routeLine = null
            departureMarker = null
            destinationMarker = null
            return
        }
        if (routeLine == null) {
            routeLine =
                Polyline(map).also { line ->
                    // Before setPoints: osmdroid adds the great-circle points while the points are set.
                    line.isGeodesic = true
                    line.outlinePaint.apply {
                        color = routeLineColor
                        strokeWidth = ROUTE_LINE_WIDTH_PX
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    map.overlays.add(line)
                }
            departureMarker = endpointMarker(map, R.drawable.ic_map_pin_departure)
            destinationMarker = endpointMarker(map, R.drawable.ic_map_pin_destination)
        }
        val departure = GeoPoint(route.departure.latitude, route.departure.longitude)
        val destination = GeoPoint(route.destination.latitude, route.destination.longitude)
        routeLine?.setPoints(listOf(departure, destination))
        departureMarker?.apply {
            title = context.getString(R.string.route_departure_marker, route.departureName)
            snippet = context.getString(R.string.route_departure_marker_description)
            position = departure
        }
        destinationMarker?.apply {
            title = context.getString(R.string.route_destination_marker, route.destinationName)
            snippet = context.getString(R.string.route_destination_marker_description)
            position = destination
        }
    }

    private fun endpointMarker(
        map: MapView,
        icon: Int,
    ): Marker =
        Marker(map).also { marker ->
            marker.icon = ContextCompat.getDrawable(context, icon)
            marker.setAnchor(Marker.ANCHOR_CENTER, PIN_TIP_ANCHOR_V)
            map.overlays.add(marker)
        }

    internal companion object {
        /**
         * Route line width in pixels, as the original app (`PATH_WIDTH`, raw pixels, not scaled by
         * density).
         */
        const val ROUTE_LINE_WIDTH_PX = 7f

        /** Vertical anchor of the route pins: the pin's tip (y 424.6 of 485.2 in the drawable). */
        const val PIN_TIP_ANCHOR_V = 0.875f
    }
}
