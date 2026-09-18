package kniezrec.com.flightinfo.gnss

import org.junit.Assert.assertEquals
import org.junit.Test

class GnssStatusControllerTest {
    @Test fun `enabled capable device starts waiting and registers once`() {
        val platform = FakePlatform()
        val states = mutableListOf<GnssStatusState>()
        GnssStatusController(platform, states::add).start()
        assertEquals(listOf(GnssStatusState.Waiting), states)
        assertEquals(1, platform.registers)
    }

    @Test fun `satellite reports derive available count and empty report waits`() {
        val platform = FakePlatform()
        val states = mutableListOf<GnssStatusState>()
        GnssStatusController(platform, states::add).start()
        platform.report(listOf(GnssSatellite(true), GnssSatellite(false)))
        assertEquals(GnssStatusState.Available(listOf(GnssSatellite(true), GnssSatellite(false))), states.last())
        platform.report(emptyList())
        assertEquals(GnssStatusState.Waiting, states.last())
    }

    @Test fun `disabled unavailable and failed registration do not stay registered`() {
        val states = mutableListOf<GnssStatusState>()
        val disabled = FakePlatform(enabled = false)
        GnssStatusController(disabled, states::add).start()
        assertEquals(GnssStatusState.LocationServicesDisabled, states.last())
        assertEquals(0, disabled.registers)
        val unavailable = FakePlatform(hardware = false)
        GnssStatusController(unavailable, states::add).start()
        assertEquals(GnssStatusState.Unavailable, states.last())
        val failing = FakePlatform(registerResult = false)
        GnssStatusController(failing, states::add).start()
        assertEquals(GnssStatusState.Error, states.last())
    }

    @Test fun `restart clears old report and unregisters previous callback`() {
        val platform = FakePlatform()
        val states = mutableListOf<GnssStatusState>()
        val controller = GnssStatusController(platform, states::add)
        controller.start()
        platform.report(listOf(GnssSatellite(true)))
        controller.start()
        assertEquals(GnssStatusState.Waiting, states.last())
        assertEquals(1, platform.unregisters)
        controller.stop()
        assertEquals(2, platform.unregisters)
    }

    private class FakePlatform(
        var enabled: Boolean = true,
        var hardware: Boolean = true,
        var registerResult: Boolean = true,
    ) : GnssStatusPlatform {
        var registers = 0
        var unregisters = 0
        private var callback: ((List<GnssSatellite>) -> Unit)? = null

        override fun areLocationServicesEnabled() = enabled

        override fun hasGnssHardware() = hardware

        override fun registerGnssStatusCallback(onStatus: (List<GnssSatellite>) -> Unit): Boolean {
            registers++
            callback = onStatus
            return registerResult
        }

        override fun unregisterGnssStatusCallback() {
            unregisters++
            callback = null
        }

        fun report(satellites: List<GnssSatellite>) {
            callback?.invoke(satellites)
        }
    }
}
