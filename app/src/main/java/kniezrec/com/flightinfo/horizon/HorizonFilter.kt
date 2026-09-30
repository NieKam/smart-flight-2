package kniezrec.com.flightinfo.horizon

/** Filter factor of the original horizon (`avionic/calculators/Filter.ALPHA`). */
internal const val HORIZON_LOW_PASS_ALPHA = 0.25

/** Pitch and roll in degrees, as filtered by [lowPassAttitude]. */
internal data class FilteredAttitude(
    val pitchDegrees: Double,
    val rollDegrees: Double,
)

/**
 * The original horizon low-pass filter, `output + alpha * (input - output)`, applied to pitch and
 * roll. The original filtered the rotation vector starting from zero; here the first attitude
 * ([previous] null) starts the filter as it is, so the first sample, which becomes the level
 * reference, is not pulled toward zero. Roll follows the shorter arc: from 170° toward -170° it
 * passes through ±180°, not back through 0°. The result's roll is in (-180, 180].
 */
internal fun lowPassAttitude(
    previous: FilteredAttitude?,
    pitchDegrees: Double,
    rollDegrees: Double,
    alpha: Double = HORIZON_LOW_PASS_ALPHA,
): FilteredAttitude =
    if (previous == null) {
        FilteredAttitude(pitchDegrees, wrapDegrees(rollDegrees))
    } else {
        FilteredAttitude(
            pitchDegrees = previous.pitchDegrees + alpha * (pitchDegrees - previous.pitchDegrees),
            rollDegrees = wrapDegrees(previous.rollDegrees + alpha * wrapDegrees(rollDegrees - previous.rollDegrees)),
        )
    }

/** [degrees] as the equivalent angle in (-180, 180]. */
internal fun wrapDegrees(degrees: Double): Double {
    val wrapped = ((degrees + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
    return if (wrapped == -180.0) 180.0 else wrapped
}
