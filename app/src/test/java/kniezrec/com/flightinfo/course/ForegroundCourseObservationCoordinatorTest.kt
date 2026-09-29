package kniezrec.com.flightinfo.course

import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.flight.FlightParametersController
import kniezrec.com.flightinfo.flight.FlightParametersState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundCourseObservationCoordinatorTest {
    private val coursePlatform = FakeCoursePlatform()
    private val courseStates = mutableListOf<CourseState>()
    private val courseController = CourseController(coursePlatform, courseStates::add)
    private val flightStates = mutableListOf<FlightParametersState>()
    private val flightController = FlightParametersController(flightStates::add)
    private val coordinator = ForegroundCourseObservationCoordinator(flightController, courseController)

    @Test fun `start clears old compass data and starts a flight session and compass observation`() {
        courseController.start()
        coursePlatform.heading(42.0)

        coordinator.start()

        assertEquals(2, coursePlatform.registers)
        assertEquals(1, coursePlatform.unregisters)
        assertEquals(CourseState.Waiting, courseStates.last())
        flightController.acceptLocationFix(FIX)
        assertTrue(flightStates.last() is FlightParametersState.Readings)
    }

    @Test fun `stop ends the flight session and compass observation`() {
        coordinator.start()

        coordinator.stop()
        flightController.acceptLocationFix(FIX)

        assertEquals(FlightParametersState.Waiting, flightStates.last())
        assertEquals(1, coursePlatform.unregisters)
    }

    @Test fun `stopping foreground-only sensors keeps the flight session accepting fixes`() {
        coordinator.start()

        coordinator.stopForegroundOnly()
        flightController.acceptLocationFix(FIX)

        assertEquals(1, coursePlatform.unregisters)
        assertTrue(flightStates.last() is FlightParametersState.Readings)
    }

    private class FakeCoursePlatform : CourseOrientationPlatform {
        var registers = 0

        var unregisters = 0

        private var callback: ((Double) -> Unit)? = null

        override fun isOrientationAvailable() = true

        override fun registerOrientationListener(onHeading: (Double) -> Unit): Boolean {
            registers++
            callback = onHeading
            return true
        }

        fun heading(heading: Double) = callback?.invoke(heading)

        override fun unregisterOrientationListener() {
            unregisters++
            callback = null
        }
    }

    private companion object {
        val FIX = FlightLocationFix(10.0, 100.0, 1_000_000_000L)
    }
}
