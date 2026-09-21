package kniezrec.com.flightinfo.horizon

internal interface HorizonOrientationPlatform {
    fun isOrientationAvailable(): Boolean

    fun registerOrientationListener(onAttitude: (pitchDegrees: Double, rollDegrees: Double) -> Unit): Boolean

    fun unregisterOrientationListener()
}

internal class HorizonController(
    private val platform: HorizonOrientationPlatform,
    private val onStateChanged: (HorizonState) -> Unit,
) {
    private var registered = false
    private var referencePitchDegrees: Double? = null
    private var nextSessionToken = 0L
    private var activeSessionToken: Long? = null

    fun start() {
        stop()
        if (!platform.isOrientationAvailable()) {
            setState(HorizonState.Unavailable)
            return
        }
        setState(HorizonState.Waiting)
        val session = ++nextSessionToken
        activeSessionToken = session
        registered =
            try {
                platform.registerOrientationListener { pitch, roll -> onAttitude(session, pitch, roll) }
            } catch (_: SecurityException) {
                false
            } catch (_: RuntimeException) {
                false
            }
        if (!registered) {
            activeSessionToken = null
            setState(HorizonState.Error)
        }
    }

    fun stop() {
        activeSessionToken = null
        referencePitchDegrees = null
        if (registered) platform.unregisterOrientationListener()
        registered = false
        setState(HorizonState.Waiting)
    }

    fun calibrate() {
        if (activeSessionToken == null) return
        referencePitchDegrees = null
        setState(HorizonState.Recalibrating)
    }

    fun onDisplayRotationChanged() {
        start()
    }

    fun retry(isForeground: Boolean) {
        if (isForeground) start()
    }

    private fun onAttitude(
        session: Long,
        pitchDegrees: Double,
        rollDegrees: Double,
    ) {
        if (activeSessionToken != session || !pitchDegrees.isFinite() || !rollDegrees.isFinite()) return
        val reference = referencePitchDegrees ?: pitchDegrees.also { referencePitchDegrees = it }
        mapHorizonAttitude(pitchDegrees - reference, rollDegrees)?.let(::setState)
    }

    private fun setState(newState: HorizonState) {
        onStateChanged(newState)
    }
}
