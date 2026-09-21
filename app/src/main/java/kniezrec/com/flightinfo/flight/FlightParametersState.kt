package kniezrec.com.flightinfo.flight

/** UI-safe values for the current foreground GPS observation session. */
sealed interface FlightParametersState {
    data object Waiting : FlightParametersState

    data class Readings(
        val speedKilometresPerHour: Double?,
        val verticalSpeedMetresPerSecond: Double?,
        val altitudeMetres: Double?,
        val pressureMillibars: Double? = null,
    ) : FlightParametersState
}

/** Small framework-independent representation of a GPS location update. */
data class FlightLocationFix(
    val speedMetresPerSecond: Double?,
    val altitudeMetres: Double?,
    val elapsedRealtimeNanos: Long,
    val bearingDegrees: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
