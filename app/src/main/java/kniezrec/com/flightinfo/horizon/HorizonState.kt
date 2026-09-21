package kniezrec.com.flightinfo.horizon

import kotlin.math.roundToInt

internal sealed interface HorizonState {
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

internal fun mapHorizonAttitude(relativePitchDegrees: Double, rollDegrees: Double): HorizonState.Available? {
    if (!relativePitchDegrees.isFinite() || !rollDegrees.isFinite()) return null
    val pitch = relativePitchDegrees.coerceIn(-30.0, 30.0)
    val roll = rollDegrees.coerceIn(-45.0, 45.0)
    return HorizonState.Available(
        pitchDegrees = pitch.roundToInt(),
        rollDegrees = roll.roundToInt(),
        // Positive pitch raises the ground/horizon layer; the fixed aircraft stays centered.
        verticalOffsetFraction = (-pitch / 30.0 * 0.35).toFloat(),
        visualRollDegrees = roll.toFloat(),
    )
}
