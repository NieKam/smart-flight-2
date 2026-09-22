package kniezrec.com.flightinfo.displayunits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitPresentationTest {
    @Test fun convertsAllDisplayUnits() {
        assertEquals(62.1371, convertSpeed(100.0, SpeedUnit.MILES_PER_HOUR)!!, 0.0001)
        assertEquals(53.9957, convertSpeed(100.0, SpeedUnit.KNOTS)!!, 0.0001)
        assertEquals(32.8084, convertAltitude(10.0, AltitudeUnit.FEET)!!, 0.0001)
        assertEquals(6.21371, convertDistance(10.0, DistanceUnit.MILES)!!, 0.0001)
        assertEquals(-600.0, convertVerticalSpeed(-10.0, VerticalSpeedUnit.METRES_PER_MINUTE)!!, 0.0001)
        assertEquals(1968.50394, convertVerticalSpeed(10.0, VerticalSpeedUnit.FEET_PER_MINUTE)!!, 0.0001)
        assertEquals(29.95789, convertPressure(1014.0, PressureUnit.INCHES_OF_MERCURY)!!, 0.0001)
    }

    @Test fun formatsSignsAndRejectsNonFiniteValues() {
        assertEquals("+1.0", formatUnitNumber(1.0, signed = true, locale = java.util.Locale.US))
        assertEquals("−1.0", formatUnitNumber(-1.0, signed = true, locale = java.util.Locale.US))
        assertNull(formatUnitNumber(Double.NaN))
        assertNull(convertDistance(Double.POSITIVE_INFINITY, DistanceUnit.MILES))
    }
}
