package kniezrec.com.flightinfo.map

import kniezrec.com.flightinfo.flight.FlightLocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStateTest {
    private fun fix(
        lat: Double?,
        lon: Double?,
        bearing: Double? = null,
    ) = FlightLocationFix(null, null, 1L, bearing, lat, lon)

    @Test fun rejectsMalformedCoordinatesWithoutChangingPosition() {
        val rules = MapSessionRules()
        assertTrue(rules.accept(fix(12.0, 34.0)))
        assertTrue(!rules.accept(fix(Double.NaN, 34.0)))
        assertEquals(MapCoordinate(12.0, 34.0), rules.latestPosition)
    }

    @Test fun centersFirstFixOnceAndMovesLaterFixWithoutResettingViewportRule() {
        val rules = MapSessionRules()
        rules.accept(fix(1.0, 2.0))
        assertEquals(MapCoordinate(1.0, 2.0), rules.consumeFirstFixCenter())
        rules.accept(fix(3.0, 4.0))
        assertNull(rules.consumeFirstFixCenter())
        assertEquals(MapCoordinate(3.0, 4.0), rules.latestPosition)
    }

    @Test fun recenterUsesDefaultUntilAValidFixExists() {
        val rules = MapSessionRules()
        assertEquals(MapViewport(MapSessionRules.DEFAULT_CENTER, 3.0), rules.recenter())
        rules.accept(fix(5.0, 6.0))
        assertEquals(MapViewport(MapCoordinate(5.0, 6.0), 6.0), rules.recenter())
    }

    @Test fun invalidCourseResetsToNeutralOrientationAndValidCourseNormalizes() {
        val rules = MapSessionRules()
        rules.accept(fix(1.0, 2.0, -90.0))
        assertEquals(270f, rules.markerCourse)
        rules.accept(fix(1.0, 2.0))
        assertEquals(0f, rules.markerCourse)
        rules.accept(fix(1.0, 2.0, Double.NaN))
        assertEquals(0f, rules.markerCourse)
        rules.accept(fix(1.0, 2.0, Double.POSITIVE_INFINITY))
        assertEquals(0f, rules.markerCourse)
    }

    @Test fun resetInvalidatesPositionAndFirstFix() {
        val rules = MapSessionRules()
        rules.accept(fix(1.0, 2.0))
        rules.reset()
        assertNull(rules.latestPosition)
        assertNull(rules.consumeFirstFixCenter())
    }
}
