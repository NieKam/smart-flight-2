package kniezrec.com.flightinfo.course

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseControllerTest {
    @Test fun cardinalBoundariesAndNormalizationMatchSpecification() {
        assertEquals("N", compassCardinal(0))
        assertEquals("N", compassCardinal(22))
        assertEquals("NE", compassCardinal(23))
        assertEquals("E", compassCardinal(68))
        assertEquals("SE", compassCardinal(113))
        assertEquals("S", compassCardinal(158))
        assertEquals("SW", compassCardinal(203))
        assertEquals("W", compassCardinal(248))
        assertEquals("NW", compassCardinal(293))
        assertEquals("N", compassCardinal(338))
        assertEquals(359, normalizeCourseDegrees(-1.0))
        assertEquals(1, normalizeCourseDegrees(361.0))
    }

    @Test fun unavailableAndFailedRegistrationNeverKeepHeading() {
        val states = mutableListOf<CourseState>()
        val unavailable = FakePlatform(false)
        CourseController(unavailable, states::add).start()
        assertEquals(CourseState.Unavailable, states.last())
        assertEquals(0, unavailable.registers)

        val failed = FakePlatform(true, false)
        CourseController(failed, states::add).start()
        assertEquals(CourseState.Error, states.last())
    }

    @Test fun bearingIsSupplementaryAndClearedByNewSession() {
        val platform = FakePlatform(true)
        val states = mutableListOf<CourseState>()
        val controller = CourseController(platform, states::add)
        controller.start()
        platform.heading(10.0)
        controller.onGpsBearing(725.0)
        assertEquals(CourseState.Available(10, 5), states.last())
        controller.onGpsBearing(null)
        assertEquals(CourseState.Available(10, null), states.last())
        controller.stop()
        assertEquals(CourseState.Waiting, states.last())
        assertEquals(1, platform.unregisters)
    }

    private class FakePlatform(val available: Boolean, val result: Boolean = true) : CourseOrientationPlatform {
        var registers = 0
        var unregisters = 0
        private var callback: ((Double) -> Unit)? = null
        override fun isOrientationAvailable() = available
        override fun registerOrientationListener(onHeading: (Double) -> Unit): Boolean {
            registers++
            callback = onHeading
            return result
        }
        override fun unregisterOrientationListener() { unregisters++; callback = null }
        fun heading(value: Double) { callback?.invoke(value) }
    }
}
