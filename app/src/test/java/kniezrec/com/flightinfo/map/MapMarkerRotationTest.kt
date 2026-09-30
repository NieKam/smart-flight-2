package kniezrec.com.flightinfo.map

import kniezrec.com.flightinfo.flight.FlightLocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MapMarkerRotationTest {
    private fun fix(
        bearing: Double?,
        speed: Double?,
    ) = FlightLocationFix(speed, null, 1L, bearing, 1.0, 2.0)

    private val gps = MarkerHeadingSource.GpsTrack
    private val compass = MarkerHeadingSource.Compass

    @Test fun bearingWhileMovingAtLeastTwoMetresPerSecondIsTheGpsTrack() {
        assertEquals(MarkerRotation(90f, gps), markerRotation(fix(90.0, 2.0), 10.0, MarkerRotation()))
        assertEquals(MarkerRotation(270f, gps), markerRotation(fix(-90.0, 250.0), 10.0, MarkerRotation()))
    }

    @Test fun bearingWhileSlowUsesTheCompass() {
        assertEquals(MarkerRotation(10f, compass), markerRotation(fix(90.0, 1.99), 10.0, MarkerRotation()))
        assertEquals(MarkerRotation(10f, compass), markerRotation(fix(90.0, 0.0), 10.0, MarkerRotation()))
    }

    @Test fun bearingWithoutASpeedUsesTheCompass() {
        assertEquals(MarkerRotation(10f, compass), markerRotation(fix(90.0, null), 10.0, MarkerRotation()))
        assertEquals(MarkerRotation(10f, compass), markerRotation(fix(90.0, Double.NaN), 10.0, MarkerRotation()))
    }

    @Test fun noBearingWithACompassUsesTheCompass() {
        assertEquals(MarkerRotation(10f, compass), markerRotation(fix(null, 50.0), 10.0, MarkerRotation(90f, gps)))
        assertEquals(MarkerRotation(10f, compass), markerRotation(fix(Double.NaN, 50.0), 10.0, MarkerRotation()))
        assertEquals(MarkerRotation(350f, compass), markerRotation(null, -10.0, MarkerRotation()))
    }

    @Test fun neitherKeepsThePreviousRotation() {
        val previous = MarkerRotation(123f, gps)
        assertSame(previous, markerRotation(fix(null, 50.0), null, previous))
        assertSame(previous, markerRotation(fix(90.0, 1.0), null, previous))
        assertSame(previous, markerRotation(null, Double.NaN, previous))
        assertEquals(MarkerRotation(0f, MarkerHeadingSource.None), markerRotation(null, null, MarkerRotation()))
    }

    @Test fun theGpsTrackIsKeptDownToOneAndAHalfMetresPerSecond() {
        val following = markerRotation(fix(90.0, 3.0), 10.0, MarkerRotation())
        assertEquals(gps, following.source)

        // Between the two thresholds: stays on the track once following it...
        assertEquals(MarkerRotation(95f, gps), markerRotation(fix(95.0, 1.7), 10.0, following))
        assertEquals(MarkerRotation(95f, gps), markerRotation(fix(95.0, 1.5), 10.0, following))
        // ...but does not start following it from the compass.
        val onCompass = MarkerRotation(10f, compass)
        assertEquals(onCompass, markerRotation(fix(95.0, 1.7), 10.0, onCompass))

        // Below the release threshold: back to the compass.
        assertEquals(MarkerRotation(10f, compass), markerRotation(fix(95.0, 1.49), 10.0, following))
    }

    @Test fun headingIsConvertedToOsmdroidsCounterClockwiseRotation() {
        assertEquals(0f, osmdroidMarkerRotation(0f), 0f)
        assertEquals(270f, osmdroidMarkerRotation(90f), 0f)
        assertEquals(180f, osmdroidMarkerRotation(180f), 0f)
        assertEquals(90f, osmdroidMarkerRotation(270f), 0f)
        assertEquals(0f, osmdroidMarkerRotation(360f), 0f)
        assertEquals(90f, osmdroidMarkerRotation(-90f), 0f)
        assertEquals(320f, osmdroidMarkerRotation(400f), 0f)
        assertEquals(0f, osmdroidMarkerRotation(Float.NaN), 0f)
    }
}
