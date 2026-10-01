package kniezrec.com.flightinfo.orientation

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Circular moving average of the last [window] headings (the original app averaged the last 10).
 * Headings are averaged as unit vectors, so 359° and 1° average to 0°, not 180°: the original
 * averaged raw degrees, which jumped through south at north; that is fixed on purpose.
 */
internal class HeadingSmoother(
    private val window: Int = HEADING_SMOOTHING_WINDOW,
) {
    private val recent = ArrayDeque<Double>()

    /** Adds [headingDegrees] and returns the average in [0, 360), or the input while the average is undefined. */
    fun add(headingDegrees: Double): Double {
        recent.addLast(Math.toRadians(headingDegrees))
        if (recent.size > window) recent.removeFirst()
        val x = recent.sumOf { cos(it) }
        val y = recent.sumOf { sin(it) }
        // Opposite headings cancel out: no direction to average to.
        if (hypot(x, y) < 1e-9) return headingDegrees
        val average = Math.toDegrees(atan2(y, x))
        return if (average < 0) average + 360 else average
    }
}

/** Number of headings averaged, as in the original app. */
internal const val HEADING_SMOOTHING_WINDOW = 10

/**
 * The original compass low-pass filter on raw accelerometer or magnetometer vectors:
 * `previous * alpha + input * (1 - alpha)`. The first vector starts the filter as it is (the
 * original started from zero and needed many samples to settle).
 */
internal fun lowPass(
    previous: FloatArray?,
    input: FloatArray,
    alpha: Float = COMPASS_LOW_PASS_ALPHA,
): FloatArray =
    if (previous == null) {
        input.copyOf(3)
    } else {
        FloatArray(3) { index -> alpha * previous[index] + (1 - alpha) * input[index] }
    }

/** Filter factor of the original compass (`CourseCalculator.ALPHA`). */
internal const val COMPASS_LOW_PASS_ALPHA = 0.97f
