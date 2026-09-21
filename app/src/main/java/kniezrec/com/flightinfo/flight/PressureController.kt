package kniezrec.com.flightinfo.flight

/** Narrow platform boundary for the optional foreground pressure sensor. */
internal interface PressurePlatform {
    fun hasPressureSensor(): Boolean
    fun registerPressureListener(onPressureMillibars: (Float) -> Unit): Boolean
    fun unregisterPressureListener()
}

/** Owns one pressure listener and rejects invalid readings for each foreground session. */
internal class PressureController(
    private val platform: PressurePlatform,
    private val onPressureChanged: (Double?) -> Unit,
) {
    private var registered = false
    private var activeSession: Long? = null
    private var nextSession = 0L

    fun start(): Boolean {
        stop()
        if (!platform.hasPressureSensor()) return false
        val session = ++nextSession
        activeSession = session
        registered =
            try {
                platform.registerPressureListener { value ->
                    if (registered && activeSession == session) onPressure(value)
                }
            } catch (_: SecurityException) {
                false
            } catch (_: RuntimeException) {
                false
            }
        if (!registered) activeSession = null
        return registered
    }

    fun stop() {
        activeSession = null
        if (registered) platform.unregisterPressureListener()
        registered = false
        onPressureChanged(null)
    }

    private fun onPressure(value: Float) {
        val pressure = value.toDouble()
        if (!pressure.isFinite() || pressure < 0.0) return
        onPressureChanged(pressure)
    }
}
