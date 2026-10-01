package kniezrec.com.flightinfo.flight

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

/** One altitude measurement of the vertical-speed history. */
internal data class AltitudeSample(
    val altitudeMetres: Double,
    val elapsedRealtimeNanos: Long,
)

/** Result of [verticalSpeedStep]: the sample to remember for the next fix and the rate, if any. */
internal data class VerticalSpeedStep(
    val sample: AltitudeSample?,
    val verticalSpeedMetresPerSecond: Double?,
)

/**
 * Raw vertical speed between [previous] and a new altitude (smoothed by [averagedRates]). The first sample has no
 * rate; a missing or non-finite altitude has no rate and keeps the history; a timestamp that does
 * not move forward has no rate and clears the history.
 */
internal fun verticalSpeedStep(
    previous: AltitudeSample?,
    altitudeMetres: Double?,
    elapsedRealtimeNanos: Long,
): VerticalSpeedStep {
    if (altitudeMetres == null || !altitudeMetres.isFinite()) return VerticalSpeedStep(previous, null)
    val sample = AltitudeSample(altitudeMetres, elapsedRealtimeNanos)
    if (previous == null) return VerticalSpeedStep(sample, null)
    if (elapsedRealtimeNanos <= previous.elapsedRealtimeNanos) return VerticalSpeedStep(null, null)
    val elapsedSeconds = (elapsedRealtimeNanos - previous.elapsedRealtimeNanos) / NANOS_PER_SECOND
    return VerticalSpeedStep(sample, (altitudeMetres - previous.altitudeMetres) / elapsedSeconds)
}

/**
 * The raw rates averaged into the displayed vertical speed after [step]: the last
 * [VERTICAL_SPEED_WINDOW] rates (the original app's 3-sample moving average). A step that cleared
 * the altitude history clears them too; a step without a rate keeps them.
 */
internal fun averagedRates(
    previousRates: List<Double>,
    step: VerticalSpeedStep,
): List<Double> {
    val rate = step.verticalSpeedMetresPerSecond
    return when {
        step.sample == null -> emptyList()
        rate == null -> previousRates
        else -> (previousRates + rate).takeLast(VERTICAL_SPEED_WINDOW)
    }
}

/**
 * Flight readings of one observation session: [state] stays [FlightParametersState.Waiting] until a
 * fix has a displayable speed or altitude, then every fix produces readings. The vertical speed is
 * the average of the last [VERTICAL_SPEED_WINDOW] raw rates ([averagedRates]); a fix without a raw
 * rate has none.
 */
internal data class FlightReadingsSession(
    val previousAltitude: AltitudeSample? = null,
    val recentRates: List<Double> = emptyList(),
    val state: FlightParametersState = FlightParametersState.Waiting,
) {
    fun accept(fix: FlightLocationFix): FlightReadingsSession {
        val step = verticalSpeedStep(previousAltitude, fix.altitudeMetres, fix.elapsedRealtimeNanos)
        val rates = averagedRates(recentRates, step)
        val speed = fix.speedMetresPerSecond?.takeIf(Double::isFinite)?.let { it * KILOMETRES_PER_HOUR_PER_METRE_PER_SECOND }
        val altitude = fix.altitudeMetres?.takeIf(Double::isFinite)
        if (state is FlightParametersState.Waiting && speed == null && altitude == null) {
            return copy(previousAltitude = step.sample, recentRates = rates)
        }
        return FlightReadingsSession(
            previousAltitude = step.sample,
            recentRates = rates,
            state =
                FlightParametersState.Readings(
                    speedKilometresPerHour = speed,
                    verticalSpeedMetresPerSecond = step.verticalSpeedMetresPerSecond?.let { rates.average() },
                    altitudeMetres = altitude,
                ),
        )
    }
}

/**
 * Flight states of a session over these fixes. Every collection starts a new session: it emits
 * [FlightParametersState.Waiting] first, with an empty vertical-speed history.
 */
internal fun Flow<FlightLocationFix>.flightParameters(): Flow<FlightParametersState> =
    scan(FlightReadingsSession()) { session, fix -> session.accept(fix) }.map { it.state }

/**
 * Attaches the barometer value, with or without GPS readings (as the original app): a pressure
 * value while waiting gives readings with pressure only.
 */
internal fun FlightParametersState.withPressure(pressureMillibars: Double?): FlightParametersState =
    when (this) {
        FlightParametersState.Waiting ->
            pressureMillibars?.let {
                FlightParametersState.Readings(
                    speedKilometresPerHour = null,
                    verticalSpeedMetresPerSecond = null,
                    altitudeMetres = null,
                    pressureMillibars = it,
                )
            } ?: this
        is FlightParametersState.Readings -> copy(pressureMillibars = pressureMillibars)
    }

/** Number of raw vertical-speed rates averaged, as in the original app. */
internal const val VERTICAL_SPEED_WINDOW = 3

private const val KILOMETRES_PER_HOUR_PER_METRE_PER_SECOND = 3.6
private const val NANOS_PER_SECOND = 1_000_000_000.0
