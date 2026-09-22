package kniezrec.com.flightinfo.flight

/** Narrow, testable boundary around foreground GPS location registration. */
internal interface FlightLocationPlatform {
    fun areLocationServicesEnabled(): Boolean

    fun hasGnssHardware(): Boolean

    fun registerLocationListener(onLocation: (FlightLocationFix) -> Unit): Boolean

    fun unregisterLocationListener()
}

/** Owns one foreground location listener and altitude history for one observation session. */
internal class FlightParametersController(
    private val platform: FlightLocationPlatform,
    private val onStateChanged: (FlightParametersState) -> Unit,
    private val onLocationFix: (FlightLocationFix) -> Unit = {},
    private val onRegistrationFailed: () -> Unit = {},
) {
    private var registered = false
    private var activeSession: Long? = null
    private var nextSession = 0L
    private var previousAltitudeSample: AltitudeSample? = null
    private var hasReceivedDisplayableReading = false
    private var externalSession = false

    /** Starts a foreground location session and reports whether listener registration succeeded. */
    fun start(): Boolean {
        stop()
        if (externalSession) {
            registered = true
            activeSession = ++nextSession
            return true
        }
        if (!platform.areLocationServicesEnabled() || !platform.hasGnssHardware()) return false
        val session = ++nextSession
        activeSession = session
        registered =
            try {
                platform.registerLocationListener { fix ->
                    if (registered && activeSession == session) onLocation(fix)
                }
            } catch (_: SecurityException) {
                false
            } catch (_: RuntimeException) {
                false
            }
        if (!registered) {
            activeSession = null
            onRegistrationFailed()
            return false
        }
        return true
    }

    fun stop() {
        activeSession = null
        if (registered && !externalSession) platform.unregisterLocationListener()
        registered = false
        previousAltitudeSample = null
        hasReceivedDisplayableReading = false
        onStateChanged(FlightParametersState.Waiting)
    }

    /** Accepts a fix from the service-owned monitoring session. */
    fun acceptLocationFix(fix: FlightLocationFix) {
        if (registered) onLocation(fix)
    }

    /** Marks this controller as consuming an already-registered shared session. */
    fun attachToExternalSession() {
        stop()
        externalSession = true
        registered = true
        activeSession = ++nextSession
    }

    private fun onLocation(fix: FlightLocationFix) {
        onLocationFix(fix)
        val verticalSpeed = updateVerticalSpeed(fix.altitudeMetres, fix.elapsedRealtimeNanos)
        val speed = fix.speedMetresPerSecond?.takeIf(Double::isFinite)?.let { it * KILOMETRES_PER_HOUR_PER_METRE_PER_SECOND }
        val altitude = fix.altitudeMetres?.takeIf(Double::isFinite)
        if (!hasReceivedDisplayableReading && speed == null && altitude == null) return
        hasReceivedDisplayableReading = true
        onStateChanged(
            FlightParametersState.Readings(
                speedKilometresPerHour = speed,
                verticalSpeedMetresPerSecond = verticalSpeed,
                altitudeMetres = altitude,
            ),
        )
    }

    private fun updateVerticalSpeed(
        altitude: Double?,
        elapsedRealtimeNanos: Long,
    ): Double? {
        if (altitude == null || !altitude.isFinite()) return null
        val previous = previousAltitudeSample
        if (previous == null) {
            previousAltitudeSample = AltitudeSample(altitude, elapsedRealtimeNanos)
            return null
        }
        if (elapsedRealtimeNanos <= previous.elapsedRealtimeNanos) {
            previousAltitudeSample = null
            return null
        }
        previousAltitudeSample = AltitudeSample(altitude, elapsedRealtimeNanos)
        val elapsedSeconds = (elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / NANOS_PER_SECOND
        return (altitude - previous.altitudeMetres) / elapsedSeconds
    }

    private data class AltitudeSample(
        val altitudeMetres: Double,
        val elapsedRealtimeNanos: Long,
    )

    private companion object {
        const val KILOMETRES_PER_HOUR_PER_METRE_PER_SECOND = 3.6
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
