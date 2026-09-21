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
    private var nextSessionToken = 0L
    private var activeSessionToken: Long? = null

    fun start() {
        stop()
        if (!platform.isOrientationAvailable()) {
            setState(CourseState.Unavailable)
            return
        }
        setState(CourseState.Waiting)
        val sessionToken = ++nextSessionToken
        activeSessionToken = sessionToken
        registered = try {
            platform.registerOrientationListener { heading -> onHeading(sessionToken, heading) }
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
        if (!registered) {
            activeSessionToken = null
            setState(CourseState.Error)
        }
    }

    fun stop() {
        activeSessionToken = null
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

    private fun onHeading(sessionToken: Long, headingDegrees: Double) {
        if (activeSessionToken != sessionToken) return
        val heading = normalizeCourseDegrees(headingDegrees) ?: return
        setState(CourseState.Available(heading, (state as? CourseState.Available)?.gpsBearingDegrees))
    }

    private fun setState(newState: CourseState) {
        state = newState
        onStateChanged(newState)
    }
}
