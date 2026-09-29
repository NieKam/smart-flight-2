package kniezrec.com.flightinfo.course

import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundCourseObservationCoordinatorTest {
    private val coursePlatform = FakeCoursePlatform()
    private val courseStates = mutableListOf<CourseState>()
    private val courseController = CourseController(coursePlatform, courseStates::add)
    private val coordinator = ForegroundCourseObservationCoordinator(courseController)

    @Test fun `start clears old compass data and starts compass observation`() {
        courseController.start()
        coursePlatform.heading(42.0)

        coordinator.start()

        assertEquals(2, coursePlatform.registers)
        assertEquals(1, coursePlatform.unregisters)
        assertEquals(CourseState.Waiting, courseStates.last())
    }

    @Test fun `stop ends compass observation`() {
        coordinator.start()

        coordinator.stop()

        assertEquals(1, coursePlatform.unregisters)
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
}
