package kniezrec.com.flightinfo.map

import kniezrec.com.flightinfo.flight.FlightLocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class MapStateTest {
    private fun fix(
        lat: Double?,
        lon: Double?,
        bearing: Double? = null,
        speed: Double? = MOVING,
    ) = FlightLocationFix(speed, null, 1L, bearing, lat, lon)

    @Test fun rejectsMalformedCoordinatesWithoutChangingPosition() {
        val tracking = MapTracking().accept(fix(12.0, 34.0, 90.0))
        assertSame(tracking, tracking.accept(fix(Double.NaN, 34.0, 180.0)))
        assertSame(tracking, tracking.accept(fix(null, 34.0)))
        assertSame(tracking, tracking.accept(fix(91.0, 34.0)))
        assertEquals(MapCoordinate(12.0, 34.0), tracking.position)
        assertEquals(90f, tracking.markerHeadingDegrees)
    }

    @Test fun firstFixIsKeptWhileLaterFixesMoveThePosition() {
        val first = MapTracking().accept(fix(1.0, 2.0))
        assertEquals(MapCoordinate(1.0, 2.0), first.firstFix)
        val later = first.accept(fix(3.0, 4.0))
        assertEquals(MapCoordinate(1.0, 2.0), later.firstFix)
        assertEquals(MapCoordinate(3.0, 4.0), later.position)
    }

    @Test fun recenterUsesDefaultUntilAValidFixExists() {
        assertEquals(MapViewport(MapRules.DEFAULT_CENTER, 3.0), MapRules.recenter(null))
        assertEquals(MapViewport(MapCoordinate(5.0, 6.0), 6.0), MapRules.recenter(MapCoordinate(5.0, 6.0)))
    }

    @Test fun movingFixesTurnTheMarkerAlongTheTrackAndKeepItWithoutABearing() {
        var tracking = MapTracking().accept(fix(1.0, 2.0, -90.0))
        assertEquals(MarkerRotation(270f, MarkerHeadingSource.GpsTrack), tracking.marker)
        tracking = tracking.accept(fix(1.0, 2.0))
        assertEquals(270f, tracking.markerHeadingDegrees)
        tracking = tracking.accept(fix(1.0, 2.0, Double.NaN))
        assertEquals(270f, tracking.markerHeadingDegrees)
    }

    @Test fun theCompassTurnsTheMarkerWhileStandingStill() {
        var tracking = MapTracking().acceptCompass(30.0)
        assertEquals(MarkerRotation(30f, MarkerHeadingSource.Compass), tracking.marker)

        tracking = tracking.accept(fix(1.0, 2.0, 90.0, speed = 0.5))
        assertEquals(30f, tracking.markerHeadingDegrees)

        tracking = tracking.acceptCompass(45.0)
        assertEquals(MarkerRotation(45f, MarkerHeadingSource.Compass), tracking.marker)
    }

    @Test fun theCompassDoesNotOverrideTheTrackWhileMoving() {
        var tracking = MapTracking().accept(fix(1.0, 2.0, 90.0))
        tracking = tracking.acceptCompass(10.0)
        assertEquals(MarkerRotation(90f, MarkerHeadingSource.GpsTrack), tracking.marker)

        // Slowing down below the release speed hands the marker to the compass.
        tracking = tracking.accept(fix(1.0, 2.0, 90.0, speed = 1.0))
        assertEquals(MarkerRotation(10f, MarkerHeadingSource.Compass), tracking.marker)
    }

    @Test fun newTrackingHasNoPositionAndNoFirstFix() {
        val tracking = MapTracking()
        assertNull(tracking.position)
        assertNull(tracking.firstFix)
        assertEquals(0f, tracking.markerHeadingDegrees)
    }

    private companion object {
        const val MOVING = 10.0
    }
}
