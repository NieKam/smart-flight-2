package kniezrec.com.flightinfo.flight

import org.junit.Assert.assertEquals
import org.junit.Test

class FlightParametersControllerTest {
    @Test fun `first altitude sample has no vertical speed and second computes rate`() {
        val platform = FakePlatform()
        val states = mutableListOf<FlightParametersState>()
        FlightParametersController(platform, states::add).start()

        platform.report(FlightLocationFix(10.0, 100.0, 1_000_000_000L))
        assertEquals(36.0, (states.last() as FlightParametersState.Readings).speedKilometresPerHour)
        assertEquals(null, (states.last() as FlightParametersState.Readings).verticalSpeedMetresPerSecond)

        platform.report(FlightLocationFix(10.0, 104.0, 3_000_000_000L))
        assertEquals(2.0, (states.last() as FlightParametersState.Readings).verticalSpeedMetresPerSecond)
    }

    @Test fun `missing fields and invalid intervals do not invent readings`() {
        val platform = FakePlatform()
        val states = mutableListOf<FlightParametersState>()
        FlightParametersController(platform, states::add).start()

        platform.report(FlightLocationFix(null, 100.0, 2_000_000_000L))
        platform.report(FlightLocationFix(null, 102.0, 2_000_000_000L))
        val invalidInterval = states.last() as FlightParametersState.Readings
        assertEquals(null, invalidInterval.speedKilometresPerHour)
        assertEquals(null, invalidInterval.verticalSpeedMetresPerSecond)

        platform.report(FlightLocationFix(null, 104.0, 3_000_000_000L))
        assertEquals(null, (states.last() as FlightParametersState.Readings).verticalSpeedMetresPerSecond)
    }

    @Test fun `unavailable failed and stopped sessions stay waiting and clean up`() {
        val unavailable = FakePlatform(enabled = false)
        val unavailableStates = mutableListOf<FlightParametersState>()
        FlightParametersController(unavailable, unavailableStates::add).start()
        assertEquals(listOf(FlightParametersState.Waiting), unavailableStates)
        assertEquals(0, unavailable.registers)

        val platform = FakePlatform()
        val states = mutableListOf<FlightParametersState>()
        var failures = 0
        val controller = FlightParametersController(platform, states::add) { failures++ }
        controller.start()
        controller.stop()
        assertEquals(1, platform.unregisters)
        platform.registerResult = false
        controller.start()
        assertEquals(1, failures)
        assertEquals(FlightParametersState.Waiting, states.last())
    }
    @Test
     fun `late location callback cannot update a restarted foreground session`() {
        val platform = FakePlatform()
        val states = mutableListOf<FlightParametersState>()
        val forwardedFixes = mutableListOf<FlightLocationFix>()
        val controller = FlightParametersController(platform, states::add, onLocationFix = forwardedFixes::add)
        controller.start()
        val oldCallback = platform.callback()
        controller.stop()
        controller.start()

        oldCallback(FlightLocationFix(10.0, 100.0, 1_000_000_000L, 45.0))

        assertEquals(FlightParametersState.Waiting, states.last())
        assertEquals(emptyList<FlightLocationFix>(), forwardedFixes)
    }

    private class FakePlatform(
        var enabled: Boolean = true,
        var hardware: Boolean = true,
        var registerResult: Boolean = true,
    ) : FlightLocationPlatform {
        var registers = 0
        var unregisters = 0
        private var callback: ((FlightLocationFix) -> Unit)? = null

        override fun areLocationServicesEnabled() = enabled

        override fun hasGnssHardware() = hardware

        override fun registerLocationListener(onLocation: (FlightLocationFix) -> Unit): Boolean {
            registers++
            callback = onLocation
            return registerResult
        }

        override fun unregisterLocationListener() {
            unregisters++
            callback = null
        }

        fun report(fix: FlightLocationFix) {
            callback?.invoke(fix)
        }

        fun callback(): (FlightLocationFix) -> Unit = requireNotNull(callback)
    }

    @Test fun `initial fix without a displayable field remains waiting`() {
        val platform = FakePlatform()
        val states = mutableListOf<FlightParametersState>()
        FlightParametersController(platform, states::add).start()

        platform.report(FlightLocationFix(null, null, 1_000_000_000L))
        platform.report(FlightLocationFix(Double.NaN, Double.POSITIVE_INFINITY, 2_000_000_000L))

        assertEquals(listOf(FlightParametersState.Waiting), states)
    }
}
