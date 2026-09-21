package kniezrec.com.flightinfo.course

import org.junit.Assert.assertEquals
import org.junit.Test

class CourseControllerTest {
    @Test fun everyCardinalBoundaryMatchesSpecification() {
        val expected = mapOf(0 to "N", 22 to "N", 23 to "NE", 67 to "NE", 68 to "E", 112 to "E", 113 to "SE", 157 to "SE", 158 to "S", 202 to "S", 203 to "SW", 247 to "SW", 248 to "W", 292 to "W", 293 to "NW", 337 to "NW", 338 to "N", 359 to "N")
        expected.forEach { (heading, cardinal) -> assertEquals(cardinal, compassCardinal(heading)) }
    }

    @Test fun normalizationHandlesPositiveNegativeAndInvalidValues() {
        assertEquals(359, normalizeCourseDegrees(-1.0))
        assertEquals(1, normalizeCourseDegrees(361.0))
        assertEquals(0, normalizeCourseDegrees(720.9))
        assertEquals(null, normalizeCourseDegrees(Double.NaN))
        assertEquals(null, normalizeCourseDegrees(Double.POSITIVE_INFINITY))
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

    @Test fun retryAndRepeatedLifecycleEventsClearSessionAndDoNotAccumulateListeners() {
        val platform = FakePlatform(true)
        val states = mutableListOf<CourseState>()
        val controller = CourseController(platform, states::add)
        controller.start()
        platform.heading(42.0)
        controller.onGpsBearing(99.0)
        controller.retry(true)
        assertEquals(CourseState.Waiting, states.last())
        assertEquals(2, platform.registers)
        assertEquals(1, platform.unregisters)
        controller.stop()
        controller.stop()
        assertEquals(2, platform.unregisters)
        controller.retry(false)
        assertEquals(2, platform.registers)
    }

    @Test
    fun lateHeadingFromStoppedOrReplacedSessionCannotRestoreCourse() {
        val platform = FakePlatform(true)
        val states = mutableListOf<CourseState>()
        val controller = CourseController(platform, states::add)
        controller.start()
        val firstSessionCallback = platform.callback()

        controller.stop()
        firstSessionCallback(42.0)
        assertEquals(CourseState.Waiting, states.last())

        controller.retry(true)
        firstSessionCallback(99.0)
        assertEquals(CourseState.Waiting, states.last())

        platform.heading(18.0)
        assertEquals(CourseState.Available(18, null), states.last())
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
        fun callback(): (Double) -> Unit = requireNotNull(callback)
    }
}
