package kniezrec.com.flightinfo.orientation

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class OrientationPlatformAdaptersTest {
    @Test fun displayRotationMapsPitchRollAndHeadingForAllFourOrientations() {
        val pitchMatrix = matrixForPitch(12.0)
        val rollMatrix = matrixForRoll(21.0)
        val rotations =
            listOf(
                DisplayRotation.Portrait to ExpectedOrientation(0.0, 12.0, 0.0, 0.0, 0.0, 21.0),
                DisplayRotation.Landscape to ExpectedOrientation(270.0, 0.0, 12.0, 270.0, -21.0, 0.0),
                DisplayRotation.ReversePortrait to ExpectedOrientation(180.0, -12.0, 0.0, 180.0, 0.0, -21.0),
                DisplayRotation.ReverseLandscape to ExpectedOrientation(90.0, 0.0, -12.0, 90.0, 21.0, 0.0),
            )

        rotations.forEach { (rotation, expected) ->
            assertOrientation(
                actual = DisplayRelativeOrientation.calculate(pitchMatrix, rotation)!!,
                heading = expected.pitchHeading,
                pitch = expected.pitch,
                roll = expected.pitchRoll,
            )
            assertOrientation(
                actual = DisplayRelativeOrientation.calculate(rollMatrix, rotation)!!,
                heading = expected.rollHeading,
                pitch = expected.rollPitch,
                roll = expected.roll,
            )
        }
    }

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

    @Test fun queuedOldRegistrationEventCannotReachReplacementCourseOrHorizonSessions() {
        val dispatcher = OrientationEventDispatcher()
        var oldCourse = 0
        var oldHorizon = 0
        var replacementCourse = 0
        var replacementHorizon = 0
        val sample = OrientationSample(20.0, 3.0, -4.0)

        val oldGeneration = dispatcher.beginRegistration()
        val oldEvent =
            dispatcher.capture(
                oldGeneration,
                listOf({ _: OrientationSample -> oldCourse++ }, { _: OrientationSample -> oldHorizon++ }),
                sample,
            )!!
        dispatcher.invalidateRegistration()
        val replacementGeneration = dispatcher.beginRegistration()
        val replacementEvent =
            dispatcher.capture(
                replacementGeneration,
                listOf({ _: OrientationSample -> replacementCourse++ }, { _: OrientationSample -> replacementHorizon++ }),
                sample,
            )!!

        if (dispatcher.isCurrent(oldEvent)) oldEvent.listeners.forEach { it(oldEvent.sample) }
        if (dispatcher.isCurrent(replacementEvent)) replacementEvent.listeners.forEach { it(replacementEvent.sample) }

        assertEquals(0, oldCourse)
        assertEquals(0, oldHorizon)
        assertEquals(1, replacementCourse)
        assertEquals(1, replacementHorizon)
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

    private data class ExpectedOrientation(
        val pitchHeading: Double,
        val pitch: Double,
        val pitchRoll: Double,
        val rollHeading: Double,
        val rollPitch: Double,
        val roll: Double,
    )

    private fun matrixForPitch(degrees: Double): FloatArray {
        val radians = Math.toRadians(degrees)
        val cosine = cos(radians).toFloat()
        val sine = sin(radians).toFloat()
        return floatArrayOf(1f, 0f, 0f, 0f, cosine, sine, 0f, -sine, cosine)
    }

    private fun matrixForRoll(degrees: Double): FloatArray {
        val radians = Math.toRadians(degrees)
        val cosine = cos(radians).toFloat()
        val sine = sin(radians).toFloat()
        return floatArrayOf(cosine, 0f, sine, 0f, 1f, 0f, -sine, 0f, cosine)
    }

    private fun assertOrientation(
        actual: OrientationSample,
        heading: Double,
        pitch: Double,
        roll: Double,
    ) {
        assertEquals(heading, actual.headingDegrees, 0.001)
        assertEquals(pitch, actual.pitchDegrees, 0.001)
        assertEquals(roll, actual.rollDegrees, 0.001)
    }
}
