package kniezrec.com.flightinfo.horizon

import org.junit.Assert.assertEquals
import org.junit.Test

/** The original horizon low-pass filter (alpha 0.25) on pitch and roll. */
class HorizonFilterTest {
    @Test fun `the first attitude starts the filter as it is`() {
        assertEquals(FilteredAttitude(12.0, -30.0), lowPassAttitude(null, 12.0, -30.0))
    }

    @Test fun `a step moves a quarter of the remaining distance per sample`() {
        var attitude = lowPassAttitude(null, 0.0, 0.0)
        val pitches = mutableListOf<Double>()
        val rolls = mutableListOf<Double>()
        repeat(4) {
            attitude = lowPassAttitude(attitude, 16.0, -32.0)
            pitches += attitude.pitchDegrees
            rolls += attitude.rollDegrees
        }

        assertEquals(listOf(4.0, 7.0, 9.25, 10.9375), pitches)
        assertEquals(listOf(-8.0, -14.0, -18.5, -21.875), rolls)
    }

    @Test fun `a step settles on the new value`() {
        var attitude = lowPassAttitude(null, 0.0, 0.0)
        repeat(100) { attitude = lowPassAttitude(attitude, 20.0, 10.0) }

        assertEquals(20.0, attitude.pitchDegrees, 1e-9)
        assertEquals(10.0, attitude.rollDegrees, 1e-9)
    }

    @Test fun `the default factor is the original one`() {
        assertEquals(0.25, HORIZON_LOW_PASS_ALPHA, 0.0)
        assertEquals(2.5, lowPassAttitude(FilteredAttitude(0.0, 0.0), 10.0, 0.0).pitchDegrees, 0.0)
    }

    @Test fun `roll crosses 180 degrees along the shorter arc`() {
        val start = lowPassAttitude(null, 0.0, 170.0)

        val next = lowPassAttitude(start, 0.0, -170.0)
        assertEquals(175.0, next.rollDegrees, 1e-9)

        val across = lowPassAttitude(FilteredAttitude(0.0, 178.0), 0.0, -170.0)
        assertEquals(-179.0, across.rollDegrees, 1e-9)

        val back = lowPassAttitude(FilteredAttitude(0.0, -178.0), 0.0, 170.0)
        assertEquals(179.0, back.rollDegrees, 1e-9)
    }

    @Test fun `angles wrap into the half-open range`() {
        assertEquals(-170.0, wrapDegrees(190.0), 0.0)
        assertEquals(170.0, wrapDegrees(-190.0), 0.0)
        assertEquals(180.0, wrapDegrees(180.0), 0.0)
        assertEquals(180.0, wrapDegrees(-180.0), 0.0)
        assertEquals(0.0, wrapDegrees(720.0), 0.0)
    }
}
