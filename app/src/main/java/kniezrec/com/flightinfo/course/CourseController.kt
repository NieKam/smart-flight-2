package kniezrec.com.flightinfo.course

internal interface CourseOrientationPlatform {
    fun isOrientationAvailable(): Boolean
    fun registerOrientationListener(onHeading: (Double) -> Unit): Boolean
    fun unregisterOrientationListener()
}

internal class CourseController(
    private val platform: CourseOrientationPlatform,
    private val onStateChanged: (CourseState) -> Unit,
) {
    private var registered = false
    private var state: CourseState = CourseState.Waiting

    fun start() {
        stop()
        if (!platform.isOrientationAvailable()) {
            setState(CourseState.Unavailable)
            return
        }
        setState(CourseState.Waiting)
        registered = try {
            platform.registerOrientationListener(::onHeading)
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
        if (!registered) setState(CourseState.Error)
    }

    fun stop() {
        if (registered) platform.unregisterOrientationListener()
        registered = false
        setState(CourseState.Waiting)
    }

    fun retry(isForeground: Boolean) {
        if (isForeground) start()
    }

    fun onGpsBearing(bearingDegrees: Double?) {
        val available = state as? CourseState.Available ?: return
        setState(available.copy(gpsBearingDegrees = bearingDegrees?.let(::normalizeCourseDegrees)))
    }

    private fun onHeading(headingDegrees: Double) {
        val heading = normalizeCourseDegrees(headingDegrees) ?: return
        setState(CourseState.Available(heading, (state as? CourseState.Available)?.gpsBearingDegrees))
    }

    private fun setState(newState: CourseState) {
        state = newState
        onStateChanged(newState)
    }
}
