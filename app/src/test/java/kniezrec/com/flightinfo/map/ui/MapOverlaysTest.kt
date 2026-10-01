package kniezrec.com.flightinfo.map.ui

import android.app.Activity
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.map.MapCoordinate
import kniezrec.com.flightinfo.nearby.NearbyCoordinate
import kniezrec.com.flightinfo.route.RouteOverlay
import kniezrec.com.flightinfo.ui.theme.DarkSmartFlightColors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class MapOverlaysTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private lateinit var archive: File
    private lateinit var map: MapView
    private val overlays = MapOverlays(activity, ROUTE_LINE_COLOR)

    @Before fun setUp() {
        archive = File(activity.cacheDir, "map-overlays-test.zip")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("tile.jpg"))
            zip.write(byteArrayOf(0))
            zip.closeEntry()
        }
        map = MapView(activity, OfflineTileProvider(SimpleRegisterReceiver(activity), arrayOf(archive)))
    }

    @After fun tearDown() {
        map.onDetach()
        archive.delete()
    }

    @Test fun planeMarkerIsCreatedOnTheFirstPositionAndThenOnlyMoved() {
        overlays.sync(map, MapUiState.Ready(archive), routeOverlay = null)
        assertNull(overlays.planeMarker)
        assertEquals(0, map.overlays.count { it is Marker })

        overlays.sync(map, MapUiState.Ready(archive, position = A, markerHeadingDegrees = 90f), routeOverlay = null)
        val plane = checkNotNull(overlays.planeMarker)
        assertEquals(listOf(plane), map.overlays.filterIsInstance<Marker>())
        assertAt(A, plane)
        // osmdroid rotates markers counter-clockwise: heading 90 (east) is rotation 270.
        assertEquals(270f, plane.rotation)

        overlays.sync(map, MapUiState.Ready(archive, position = B, markerHeadingDegrees = 270f), routeOverlay = null)
        assertSame(plane, overlays.planeMarker)
        assertEquals(listOf(plane), map.overlays.filterIsInstance<Marker>())
        assertAt(B, plane)
        assertEquals(90f, plane.rotation)

        // Idempotent: the same state again changes nothing.
        overlays.sync(map, MapUiState.Ready(archive, position = B, markerHeadingDegrees = 270f), routeOverlay = null)
        assertEquals(listOf(plane), map.overlays.filterIsInstance<Marker>())
    }

    @Test fun planeMarkerIsThePurplePlaneWithoutAnInfoWindow() {
        overlays.sync(map, MapUiState.Ready(archive, position = A), routeOverlay = null)
        val plane = checkNotNull(overlays.planeMarker)

        assertEquals(R.drawable.ic_plane_marker, shadowOf(plane.icon).createdFromResId)
        assertNull(plane.infoWindow)
        // A tap is consumed by the plane's own listener (osmdroid's default would open a bubble).
        val listener =
            Marker::class.java
                .getDeclaredField("mOnMarkerClickListener")
                .apply { isAccessible = true }
                .get(plane) as Marker.OnMarkerClickListener
        assertTrue(listener.onMarkerClick(plane, map))
        assertFalse(plane.isInfoWindowShown)
    }

    @Test fun planeMarkerStaysTheLastOverlayWhenTheRouteIsAddedChangedAndCleared() {
        val state = MapUiState.Ready(archive, position = A)
        overlays.sync(map, state, routeOverlay = null)
        val plane = checkNotNull(overlays.planeMarker)

        overlays.sync(map, state, ROUTE)
        assertSame(plane, map.overlays.last())
        assertEquals(1, map.overlays.count { it === plane })
        assertEquals(1, map.overlays.count { it is Polyline })
        assertEquals(3, map.overlays.count { it is Marker })

        overlays.sync(map, state, ROUTE.copy(destination = NearbyCoordinate(5.0, 6.0)))
        assertSame(plane, map.overlays.last())
        assertEquals(4, map.overlays.size)

        overlays.sync(map, state.copy(position = B), ROUTE.copy(departure = NearbyCoordinate(7.0, 8.0)))
        assertSame(plane, map.overlays.last())
        assertEquals(4, map.overlays.size)

        overlays.sync(map, state, routeOverlay = null)
        assertEquals(listOf(plane), map.overlays.toList())
    }

    @Test fun aRouteSetBeforeTheFirstFixIsDrawnBelowThePlane() {
        overlays.sync(map, MapUiState.Ready(archive), ROUTE)
        assertNull(overlays.planeMarker)
        assertEquals(3, map.overlays.size)

        overlays.sync(map, MapUiState.Ready(archive, position = A), ROUTE)
        val plane = checkNotNull(overlays.planeMarker)
        assertSame(plane, map.overlays.last())
        assertEquals(4, map.overlays.size)
    }

    @Test fun routeLineIsAGeodesicPurpleSevenPixelLine() {
        overlays.sync(map, MapUiState.Ready(archive), ROUTE_FAR)
        val line = map.overlays.filterIsInstance<Polyline>().single()

        assertTrue(line.isGeodesic)
        assertEquals(ROUTE_LINE_COLOR, line.outlinePaint.color)
        assertEquals(DarkSmartFlightColors.mapInk.toArgb(), line.outlinePaint.color)
        assertEquals(7f, line.outlinePaint.strokeWidth, 0f)
        assertEquals(Paint.Cap.ROUND, line.outlinePaint.strokeCap)
        // Frankfurt to San Francisco follows the great circle: points in between, north of both ends.
        val points = line.actualPoints
        assertTrue("great-circle points expected, got ${points.size}", points.size > 2)
        assertEquals(50.03, points.first().latitude, 0.0)
        assertEquals(-122.38, points.last().longitude, 0.0)
        assertTrue(points.any { it.latitude > 60.0 })
    }

    @Test fun routePinsAreThePalettePinsOnTheirCities() {
        overlays.sync(map, MapUiState.Ready(archive), ROUTE)
        val (departure, destination) = map.overlays.filterIsInstance<Marker>()

        assertEquals(R.drawable.ic_map_pin_departure, shadowOf(departure.icon).createdFromResId)
        assertEquals(R.drawable.ic_map_pin_destination, shadowOf(destination.icon).createdFromResId)
        assertEquals(1.0, departure.position.latitude, 0.0)
        assertEquals(4.0, destination.position.longitude, 0.0)
    }

    @Test fun routeOverlaysAreAddedAfterThePlaneMovedAndRemovedWithoutTouchingThePlane() {
        val state = MapUiState.Ready(archive, position = A)
        overlays.sync(map, state, routeOverlay = null)
        val plane = checkNotNull(overlays.planeMarker)

        overlays.sync(map, state, ROUTE)
        assertEquals(1, map.overlays.count { it is Polyline })
        assertEquals(3, map.overlays.count { it is Marker })
        val line = map.overlays.filterIsInstance<Polyline>().single()
        assertEquals(ROUTE_LINE_COLOR, line.outlinePaint.color)
        assertEquals(1.0, line.actualPoints.first().latitude, 0.0)
        assertEquals(4.0, line.actualPoints.last().longitude, 0.0)

        val moved = ROUTE.copy(destination = NearbyCoordinate(5.0, 6.0))
        overlays.sync(map, state, moved)
        assertEquals(4, map.overlays.size)
        assertSame(line, map.overlays.filterIsInstance<Polyline>().single())
        assertEquals(6.0, line.actualPoints.last().longitude, 0.0)

        overlays.sync(map, state, routeOverlay = null)
        assertEquals(listOf(plane), map.overlays.toList())
    }

    private fun assertAt(
        expected: MapCoordinate,
        marker: Marker,
    ) {
        assertEquals(expected.latitude, marker.position.latitude, 0.0)
        assertEquals(expected.longitude, marker.position.longitude, 0.0)
    }

    private companion object {
        val A = MapCoordinate(10.0, 20.0)
        val B = MapCoordinate(-30.0, 40.0)
        val ROUTE_LINE_COLOR = DarkSmartFlightColors.mapInk.toArgb()
        val ROUTE = RouteOverlay(NearbyCoordinate(1.0, 2.0), NearbyCoordinate(3.0, 4.0), "Alpha", "Beta")
        val ROUTE_FAR =
            RouteOverlay(NearbyCoordinate(50.03, 8.57), NearbyCoordinate(37.62, -122.38), "Frankfurt", "San Francisco")
    }
}
