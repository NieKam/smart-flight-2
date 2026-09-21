package kniezrec.com.flightinfo.course

import kniezrec.com.flightinfo.flight.FlightParametersController

/** Coordinates the shared foreground location session with compass observation. */
internal class ForegroundCourseObservationCoordinator(
    private val flightParametersController: FlightParametersController,
    private val courseController: CourseController,
) {
    fun start() {
        // Clear old course data before a new location registration can report its outcome.
        courseController.stop()
        if (flightParametersController.start()) courseController.start()
    }

    fun stop() {
        flightParametersController.stop()
        courseController.stop()
    }
}
