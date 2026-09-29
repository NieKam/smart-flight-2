package kniezrec.com.flightinfo.horizon

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure attitude mapping of the Horizon card. */
class HorizonStateTest {
    @Test fun mappingClampsExtremeValuesAndRejectsInvalidValues() {
        assertEquals(HorizonState.Available(30, -45, -0.35f, -45f), mapHorizonAttitude(90.0, -90.0))
        assertEquals(null, mapHorizonAttitude(Double.NaN, 0.0))
        assertEquals(null, mapHorizonAttitude(0.0, Double.POSITIVE_INFINITY))
    }
}
