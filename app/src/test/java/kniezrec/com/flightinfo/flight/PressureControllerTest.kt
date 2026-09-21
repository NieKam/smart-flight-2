package kniezrec.com.flightinfo.flight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PressureControllerTest {
    @Test fun `valid pressure is delivered and invalid values are ignored`() {
        val platform = FakePressurePlatform()
        val values = mutableListOf<Double?>()
        PressureController(platform, values::add).start()

        platform.report(Float.NaN)
        platform.report(Float.POSITIVE_INFINITY)
        platform.report(-1f)
        assertEquals(listOf(null), values)

        platform.report(1013.25f)
        platform.report(Float.NEGATIVE_INFINITY)
        platform.report(-2f)
        assertEquals(1013.25, values.last())
    }

    @Test fun `missing hardware and registration failure remain unavailable`() {
        val noSensor = FakePressurePlatform(hasSensor = false)
        val noSensorValues = mutableListOf<Double?>()
        assertFalse(PressureController(noSensor, noSensorValues::add).start())
        assertEquals(listOf(null), noSensorValues)

        val failed = FakePressurePlatform(registerResult = false)
        assertFalse(PressureController(failed, {}).start())
    }

    @Test fun `stop unregisters and rejects callbacks from an old session`() {
        val platform = FakePressurePlatform()
        val values = mutableListOf<Double?>()
        val controller = PressureController(platform, values::add)
        assertTrue(controller.start())
        val oldCallback = platform.callback()
        oldCallback(1000f)
        controller.stop()
        controller.start()
        oldCallback(900f)

        assertEquals(null, values.last())
        assertEquals(1, platform.unregisters)
    }

    private class FakePressurePlatform(
        private val hasSensor: Boolean = true,
        private val registerResult: Boolean = true,
    ) : PressurePlatform {
        var unregisters = 0
        private var callback: ((Float) -> Unit)? = null

        override fun hasPressureSensor() = hasSensor

        override fun registerPressureListener(onPressureMillibars: (Float) -> Unit): Boolean {
            callback = onPressureMillibars
            return registerResult
        }

        override fun unregisterPressureListener() {
            unregisters++
            callback = null
        }

        fun callback(): (Float) -> Unit = requireNotNull(callback)
        fun report(value: Float) { callback?.invoke(value) }
    }
}
