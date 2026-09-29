package kniezrec.com.flightinfo.flight

/**
 * Turns the fixes of the shared location registration into flight readings. Fixes are accepted
 * between [start] and [stop]; every session starts with an empty altitude history.
 */
internal class FlightParametersController(
    private val onStateChanged: (FlightParametersState) -> Unit,
    private val onLocationFix: (FlightLocationFix) -> Unit = {},
) {
    private var active = false
    private var previousAltitudeSample: AltitudeSample? = null
    private var hasReceivedDisplayableReading = false

    /** Starts a new session: readings reset to waiting and fixes are accepted until [stop]. */
    fun start() {
        stop()
        active = true
    }

    /** Ends the session: readings reset to waiting and later fixes are ignored. */
    fun stop() {
        active = false
        previousAltitudeSample = null
        hasReceivedDisplayableReading = false
        onStateChanged(FlightParametersState.Waiting)
    }

    fun acceptLocationFix(fix: FlightLocationFix) {
        if (active) onLocation(fix)
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
