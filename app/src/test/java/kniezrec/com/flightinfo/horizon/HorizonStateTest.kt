package kniezrec.com.flightinfo.horizon

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure attitude mapping of the Horizon card. */
class HorizonStateTest {
    @Test fun mappingClampsExtremeValuesAndRejectsInvalidValues() {
        assertEquals(HorizonState.Available(30, -45, 0.35f, 45f), mapHorizonAttitude(90.0, -90.0))
        assertEquals(null, mapHorizonAttitude(Double.NaN, 0.0))
        assertEquals(null, mapHorizonAttitude(0.0, Double.POSITIVE_INFINITY))
    }

    @Test fun levelIsCentered() {
        assertEquals(HorizonState.Available(0, 0, 0f, 0f), mapHorizonAttitude(0.0, 0.0))
    }

    @Test fun noseUpLowersTheHorizonAndReadsAsUp() {
        val state = mapHorizonAttitude(15.0, 0.0)!!
        assertEquals(15, state.pitchDegrees)
        assertEquals(0.175f, state.verticalOffsetFraction)
    }

    @Test fun rollingRightTurnsTheHorizonCounterClockwise() {
        val state = mapHorizonAttitude(0.0, 10.0)!!
        assertEquals(10, state.rollDegrees)
        assertEquals(-10f, state.visualRollDegrees)
    }
}
