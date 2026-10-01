package kniezrec.com.flightinfo.course

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure heading normalization and cardinal mapping of the Course card. */
class CourseStateTest {
    @Test fun everyCardinalBoundaryMatchesSpecification() {
        val expected =
            mapOf(
                0 to CompassCardinal.North,
                22 to CompassCardinal.North,
                23 to CompassCardinal.NorthEast,
                67 to CompassCardinal.NorthEast,
                68 to CompassCardinal.East,
                112 to CompassCardinal.East,
                113 to CompassCardinal.SouthEast,
                157 to CompassCardinal.SouthEast,
                158 to CompassCardinal.South,
                202 to CompassCardinal.South,
                203 to CompassCardinal.SouthWest,
                247 to CompassCardinal.SouthWest,
                248 to CompassCardinal.West,
                292 to CompassCardinal.West,
                293 to CompassCardinal.NorthWest,
                337 to CompassCardinal.NorthWest,
                338 to CompassCardinal.North,
                359 to CompassCardinal.North,
            )
        expected.forEach { (heading, cardinal) -> assertEquals(cardinal, compassCardinal(heading)) }
    }

    @Test fun normalizationHandlesPositiveNegativeAndInvalidValues() {
        assertEquals(359, normalizeCourseDegrees(-1.0))
        assertEquals(1, normalizeCourseDegrees(361.0))
        assertEquals(1, normalizeCourseDegrees(720.9))
        assertEquals(0, normalizeCourseDegrees(720.4))
        assertEquals(null, normalizeCourseDegrees(Double.NaN))
        assertEquals(null, normalizeCourseDegrees(Double.POSITIVE_INFINITY))
    }

    @Test fun normalizationRoundsAsTheOriginal() {
        assertEquals(271, normalizeCourseDegrees(271.4))
        assertEquals(272, normalizeCourseDegrees(271.6))
        assertEquals(0, normalizeCourseDegrees(359.6))
        assertEquals(0, normalizeCourseDegrees(-0.4))
        assertEquals(359, normalizeCourseDegrees(-0.6))
    }
}
