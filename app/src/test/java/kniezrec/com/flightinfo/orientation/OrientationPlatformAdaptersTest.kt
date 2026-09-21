package kniezrec.com.flightinfo.orientation

import org.junit.Assert.assertEquals
import org.junit.Test

class OrientationPlatformAdaptersTest {
    @Test fun courseAndHorizonShareOneUnderlyingSourceSubscription() {
        val source = FakeSource()
        var heading: Double? = null
        var attitude: Pair<Double, Double>? = null
        val course = SharedCourseOrientationPlatform(source)
        val horizon = SharedHorizonOrientationPlatform(source)

        course.registerOrientationListener { heading = it }
        horizon.registerOrientationListener { pitch, roll -> attitude = pitch to roll }
        assertEquals(1, source.activeRegistrationCount)

        source.emit(OrientationSample(20.0, 3.0, -4.0))
        assertEquals(20.0, heading)
        assertEquals(3.0 to -4.0, attitude)

        course.unregisterOrientationListener()
        assertEquals(1, source.activeRegistrationCount)
        horizon.unregisterOrientationListener()
        assertEquals(0, source.activeRegistrationCount)
    }

    private class FakeSource : OrientationSource {
        private val listeners = linkedSetOf<(OrientationSample) -> Unit>()
        var activeRegistrationCount = 0
            private set

        override fun isAvailable() = true

        override fun addListener(listener: (OrientationSample) -> Unit): Boolean {
            if (listeners.isEmpty()) activeRegistrationCount++
            listeners += listener
            return true
        }

        override fun removeListener(listener: (OrientationSample) -> Unit) {
            listeners -= listener
            if (listeners.isEmpty()) activeRegistrationCount--
        }

        fun emit(sample: OrientationSample) = listeners.forEach { it(sample) }
    }
}
