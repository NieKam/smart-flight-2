package kniezrec.com.flightinfo.gnss

import org.junit.Assert.assertEquals
import org.junit.Test

class GnssStatusControllerTest {
    private val states = mutableListOf<GnssStatusState>()
    private val controller = GnssStatusController(states::add)

    @Test fun `start clears the previous report to waiting`() {
        controller.start()
        controller.acceptStatus(listOf(GnssSatellite(true)))

        controller.start()

        assertEquals(GnssStatusState.Waiting, states.last())
    }

    @Test fun `satellite reports derive available count and empty report waits`() {
        controller.start()
        controller.acceptStatus(listOf(GnssSatellite(true), GnssSatellite(false)))
        assertEquals(GnssStatusState.Available(listOf(GnssSatellite(true), GnssSatellite(false))), states.last())
        controller.acceptStatus(emptyList())
        assertEquals(GnssStatusState.Waiting, states.last())
    }

    @Test fun `reports before start are ignored`() {
        controller.acceptStatus(listOf(GnssSatellite(true)))

        assertEquals(emptyList<GnssStatusState>(), states)
    }

    @Test fun `stop keeps the current state and ignores later reports`() {
        controller.start()
        controller.acceptStatus(listOf(GnssSatellite(true)))

        controller.stop()
        controller.acceptStatus(listOf(GnssSatellite(false)))

        assertEquals(GnssStatusState.Available(listOf(GnssSatellite(true))), states.last())
    }
}
