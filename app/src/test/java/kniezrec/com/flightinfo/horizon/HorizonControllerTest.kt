package kniezrec.com.flightinfo.horizon

import org.junit.Assert.assertEquals
import org.junit.Test

class HorizonControllerTest {
    @Test fun mappingClampsExtremeValuesAndRejectsInvalidValues() {
        assertEquals(HorizonState.Available(30, -45, -0.35f, -45f), mapHorizonAttitude(90.0, -90.0))
        assertEquals(null, mapHorizonAttitude(Double.NaN, 0.0))
        assertEquals(null, mapHorizonAttitude(0.0, Double.POSITIVE_INFINITY))
    }

    @Test fun firstSampleCalibratesAndCalibrateUsesNextSampleAsReference() {
        val platform = FakePlatform()
        val states = mutableListOf<HorizonState>()
        val controller = HorizonController(platform, states::add)
        controller.start()
        platform.attitude(10.0, 4.0)
        assertEquals(HorizonState.Available(0, 4, 0f, 4f), states.last())

        controller.calibrate()
        assertEquals(HorizonState.Waiting, states.last())
        platform.attitude(18.0, -6.0)
        assertEquals(HorizonState.Available(0, -6, 0f, -6f), states.last())
        platform.attitude(28.0, -8.0)
        assertEquals(HorizonState.Available(10, -8, -0.11666667f, -8f), states.last())
    }

    @Test fun unavailableFailureRetryAndStaleCallbacksAreSafe() {
        val unavailableStates = mutableListOf<HorizonState>()
        val unavailable = FakePlatform(available = false)
        HorizonController(unavailable, unavailableStates::add).start()
        assertEquals(HorizonState.Unavailable, unavailableStates.last())
        assertEquals(0, unavailable.registers)

        val platform = FakePlatform(result = false)
        val states = mutableListOf<HorizonState>()
        val controller = HorizonController(platform, states::add)
        controller.start()
        assertEquals(HorizonState.Error, states.last())
        controller.retry(false)
        assertEquals(1, platform.registers)

        platform.result = true
        controller.retry(true)
        val stale = platform.callback()
        controller.stop()
        stale(1.0, 1.0)
        assertEquals(HorizonState.Waiting, states.last())
        assertEquals(1, platform.unregisters)
    }

    private class FakePlatform(
        private val available: Boolean = true,
        var result: Boolean = true,
    ) : HorizonOrientationPlatform {
        var registers = 0
        var unregisters = 0
        private var listener: ((Double, Double) -> Unit)? = null

        override fun isOrientationAvailable() = available

        override fun registerOrientationListener(onAttitude: (Double, Double) -> Unit): Boolean {
            registers++
            listener = onAttitude
            return result
        }

        override fun unregisterOrientationListener() {
            unregisters++
            listener = null
        }

        fun attitude(pitch: Double, roll: Double) = listener?.invoke(pitch, roll)

        fun callback(): (Double, Double) -> Unit = requireNotNull(listener)
    }
}
