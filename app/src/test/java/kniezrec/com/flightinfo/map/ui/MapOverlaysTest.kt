package kniezrec.com.flightinfo.map.ui

import android.app.Activity
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.map.MapCoordinate
import kniezrec.com.flightinfo.nearby.NearbyCoordinate
import kniezrec.com.flightinfo.route.RouteOverlay
import kniezrec.com.flightinfo.ui.theme.DefaultSmartFlightColors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.robolectric.Robolectric
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

        overlays.sync(map, MapUiState.Ready(archive, position = A, markerCourseDegrees = 90f), routeOverlay = null)
        val plane = checkNotNull(overlays.planeMarker)
        assertEquals(listOf(plane), map.overlays.filterIsInstance<Marker>())
        assertAt(A, plane)
        assertEquals(90f, plane.rotation)

        overlays.sync(map, MapUiState.Ready(archive, position = B, markerCourseDegrees = 270f), routeOverlay = null)
        assertSame(plane, overlays.planeMarker)
        assertEquals(listOf(plane), map.overlays.filterIsInstance<Marker>())
        assertAt(B, plane)
        assertEquals(270f, plane.rotation)

        // Idempotent: the same state again changes nothing.
        overlays.sync(map, MapUiState.Ready(archive, position = B, markerCourseDegrees = 270f), routeOverlay = null)
        assertEquals(listOf(plane), map.overlays.filterIsInstance<Marker>())
    }

    @Test fun routeOverlaysAreAddedAfterThePlaneMovedAndRemovedWithoutTouchingThePlane() {
        val state = MapUiState.Ready(archive, position = A)
        overlays.sync(map, state, routeOverlay = null)
        val plane = checkNotNull(overlays.planeMarker)

        overlays.sync(map, state, ROUTE)
        assertSame(plane, map.overlays[0])
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
        val ROUTE_LINE_COLOR = DefaultSmartFlightColors.accent.toArgb()
        val ROUTE = RouteOverlay(NearbyCoordinate(1.0, 2.0), NearbyCoordinate(3.0, 4.0), "Alpha", "Beta")
    }
}
