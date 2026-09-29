package kniezrec.com.flightinfo.course

import kniezrec.com.flightinfo.nearby.NearbyCityController

/**
 * Coordinates compass observation with the nearby-city lookup. GPS fixes reach both while the
 * activity collects the location repository.
 */
internal class ForegroundCourseObservationCoordinator(
    private val courseController: CourseController,
    private val nearbyCityController: NearbyCityController? = null,
) {
    fun start() {
        // Clear old course data before the new session starts.
        courseController.stop()
        nearbyCityController?.start()
        courseController.start()
    }

    fun stop() {
        courseController.stop()
        nearbyCityController?.stop()
    }
}
