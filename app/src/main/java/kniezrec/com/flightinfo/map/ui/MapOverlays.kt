package kniezrec.com.flightinfo.map.ui

import android.content.Context
import androidx.core.content.ContextCompat
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.route.RouteOverlay
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * The plane marker and the route overlays of one [MapView].
 *
 * [sync] is idempotent and may run on every recomposition: the plane marker is created on the first
 * position and then only moved and rotated; the route line and its two markers are created once
 * for a route, moved when it changes and removed when it is cleared. Overlays are added in the
 * order they first appear (a plane marker before a route added later), as before.
 */
class MapOverlays(
    private val context: Context,
    /** ARGB color of the route line, a palette token. */
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
        syncPlane(map, state)
        syncRoute(map, routeOverlay)
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
                marker.icon = ContextCompat.getDrawable(context, R.drawable.ic_plane_map)
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                map.overlays.add(marker)
                planeMarker = marker
            }
        marker.position = GeoPoint(position.latitude, position.longitude)
        marker.rotation = state.markerCourseDegrees
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
                Polyline(map).also {
                    it.color = routeLineColor
                    map.overlays.add(it)
                }
            departureMarker = endpointMarker(map, R.drawable.ic_route_departure)
            destinationMarker = endpointMarker(map, R.drawable.ic_route_destination)
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
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            map.overlays.add(marker)
        }
}
