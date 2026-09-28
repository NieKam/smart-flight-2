package kniezrec.com.flightinfo.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkerCourseTest {
    @Test fun keepsFractionalDegrees() {
        assertEquals(10.5f, normalizeMarkerCourse(10.5)!!, 0f)
        assertEquals(359.75f, normalizeMarkerCourse(-0.25)!!, 0f)
    }

    @Test fun wrapsNegativeAndLargeValuesIntoFullCircle() {
        assertEquals(270f, normalizeMarkerCourse(-90.0)!!, 0f)
        assertEquals(1f, normalizeMarkerCourse(361.0)!!, 0f)
        assertEquals(0.5f, normalizeMarkerCourse(720.5)!!, 0f)
        assertEquals(0f, normalizeMarkerCourse(360.0)!!, 0f)
    }

    @Test fun rejectsMissingAndNonFiniteValues() {
        assertNull(normalizeMarkerCourse(null))
        assertNull(normalizeMarkerCourse(Double.NaN))
        assertNull(normalizeMarkerCourse(Double.POSITIVE_INFINITY))
        assertNull(normalizeMarkerCourse(Double.NEGATIVE_INFINITY))
    }
}
