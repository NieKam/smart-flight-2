package kniezrec.com.flightinfo.horizon

import kotlin.math.roundToInt

sealed interface HorizonState {
    data object Waiting : HorizonState

    /** Waiting for the calibration sample that will become the new level reference. */
    data object Recalibrating : HorizonState

    data object Unavailable : HorizonState

    data object Error : HorizonState

    data class Available(
        val pitchDegrees: Int,
        val rollDegrees: Int,
        val verticalOffsetFraction: Float,
        val visualRollDegrees: Float,
    ) : HorizonState
}

/**
 * Maps the pitch relative to the level reference (positive nose-up) and the roll (positive right
 * wing down) to the horizon picture, as an attitude indicator: nose-up lowers the horizon layer
 * behind the fixed aircraft, rolling right turns it counter-clockwise.
 */
internal fun mapHorizonAttitude(
    noseUpPitchDegrees: Double,
    rollDegrees: Double,
): HorizonState.Available? {
    if (!noseUpPitchDegrees.isFinite() || !rollDegrees.isFinite()) return null
    val pitch = noseUpPitchDegrees.coerceIn(-30.0, 30.0)
    val roll = rollDegrees.coerceIn(-45.0, 45.0)
    return HorizonState.Available(
        pitchDegrees = pitch.roundToInt(),
        rollDegrees = roll.roundToInt(),
        // Positive translation moves the layer down.
        verticalOffsetFraction = (pitch / 30.0 * 0.35).toFloat(),
        // Positive rotation is clockwise; `0.0 - roll` keeps level at 0f instead of -0f.
        visualRollDegrees = (0.0 - roll).toFloat(),
    )
}
