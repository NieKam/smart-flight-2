package kniezrec.com.flightinfo.course

import kniezrec.com.flightinfo.flight.FlightParametersController
import kniezrec.com.flightinfo.nearby.NearbyCityController

/** Coordinates the shared foreground location session with compass observation. */
internal class ForegroundCourseObservationCoordinator(
    private val flightParametersController: FlightParametersController,
    private val courseController: CourseController,
    private val nearbyCityController: NearbyCityController? = null,
) {
    fun start() {
        // Clear old course data before the new session starts.
        courseController.stop()
        nearbyCityController?.start()
        flightParametersController.start()
        courseController.start()
    }

    fun stop() {
        flightParametersController.stop()
        courseController.stop()
        nearbyCityController?.stop()
    }

    // Stops foreground-only sensors while leaving the shared location session alive.
    fun stopForegroundOnly() {
        courseController.stop()
        nearbyCityController?.stop()
    }
}
