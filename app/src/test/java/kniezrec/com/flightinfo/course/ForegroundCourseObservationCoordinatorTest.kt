package kniezrec.com.flightinfo.course

import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.flight.FlightLocationPlatform
import kniezrec.com.flightinfo.flight.FlightParametersController
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundCourseObservationCoordinatorTest {
    @Test fun `failed location registration leaves compass cleared and unobserved`() {
        val coursePlatform = FakeCoursePlatform()
        val courseStates = mutableListOf<CourseState>()
        val courseController = CourseController(coursePlatform, courseStates::add)
        courseController.start()
        coursePlatform.heading(42.0)
        val locationPlatform = FakeLocationPlatform(registerResult = false)
        val flightController = FlightParametersController(locationPlatform, {})

        ForegroundCourseObservationCoordinator(flightController, courseController).start()

        assertEquals(1, coursePlatform.registers)
        assertEquals(1, coursePlatform.unregisters)
        assertEquals(CourseState.Waiting, courseStates.last())
    }

    private class FakeLocationPlatform(
        private val registerResult: Boolean,
    ) : FlightLocationPlatform {
        override fun areLocationServicesEnabled() = true

        override fun hasGnssHardware() = true

        override fun registerLocationListener(onLocation: (FlightLocationFix) -> Unit) = registerResult

        override fun unregisterLocationListener() = Unit
    }

    private class FakeCoursePlatform : CourseOrientationPlatform {
        var registers = 0

        var unregisters = 0
        override fun isOrientationAvailable() = true

        override fun registerOrientationListener(onHeading: (Double) -> Unit): Boolean {
            registers++
            callback = onHeading
            return true
        }

        private var callback: ((Double) -> Unit)? = null

        fun heading(heading: Double) = callback?.invoke(heading)

        override fun unregisterOrientationListener() {
            unregisters++
            callback = null
        }
    }
}
