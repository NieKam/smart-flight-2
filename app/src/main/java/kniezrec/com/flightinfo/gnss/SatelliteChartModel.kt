package kniezrec.com.flightinfo.gnss

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** One bar of the satellite chart: the satellite's C/N0 (0 when unknown) and whether it is used in the fix. */
data class SatelliteBar(
    val signalDbHz: Float,
    val usedInFix: Boolean,
)

/**
 * The satellite signal chart of the original app (MPAndroidChart bar chart): one bar per satellite
 * in report order, a Y axis in dB-Hz starting at [Y_AXIS_MINIMUM] with [Y_LABEL_COUNT] evenly spaced
 * integer labels up to [yAxisMaximum], and X labels (satellite index from 0) every [xLabelStep].
 *
 * @property strongestDbHz the strongest known signal, null when no satellite reports one.
 */
data class SatelliteChartModel(
    val bars: List<SatelliteBar>,
    val yLabels: List<Int>,
    val xLabels: List<Int>,
    val usedCount: Int,
    val strongestDbHz: Float?,
) {
    val yAxisMaximum: Int get() = yLabels.last()

    val xLabelStep: Int get() = if (xLabels.size < 2) 1 else xLabels[1] - xLabels[0]

    /** Height of [bar] as a fraction of the plot height (bars start at [Y_AXIS_MINIMUM], as in the original). */
    fun heightFraction(bar: SatelliteBar): Float = ((bar.signalDbHz - Y_AXIS_MINIMUM) / (yAxisMaximum - Y_AXIS_MINIMUM)).coerceIn(0f, 1f)
}

/**
 * Builds the chart of [satellites]. A missing, negative or non-finite C/N0 counts as 0 (a bar
 * without height). The Y axis leaves about [Y_HEADROOM] above the strongest signal, as the original
 * `spaceTop = 15f`; its step is a whole number of dB-Hz so every label is an integer (a strongest
 * signal of 19.2 dB-Hz gives 1, 4, … 22, as in the original screenshot).
 */
fun satelliteChartModel(satellites: List<GnssSatellite>): SatelliteChartModel {
    val bars =
        satellites.map { satellite ->
            val signal = satellite.signalStrengthDbHz?.takeIf { it.isFinite() && it > 0f } ?: 0f
            SatelliteBar(signal, satellite.usedInFix)
        }
    val strongest = bars.maxOfOrNull { it.signalDbHz }?.takeIf { it > 0f }
    val top = (strongest ?: 0f) * (1f + Y_HEADROOM)
    var yStep = max(1, ((top - Y_AXIS_MINIMUM) / (Y_LABEL_COUNT - 1)).roundToInt())
    // Rounding may take off part of the headroom, never the strongest bar.
    if (Y_AXIS_MINIMUM + (Y_LABEL_COUNT - 1) * yStep < (strongest ?: 0f)) yStep++
    val yLabels = List(Y_LABEL_COUNT) { index -> Y_AXIS_MINIMUM + index * yStep }
    val xStep = max(1, ceil(bars.size / MAX_X_LABELS.toDouble()).toInt())
    val xLabels = bars.indices.filter { it % xStep == 0 }
    return SatelliteChartModel(bars, yLabels, xLabels, satellites.count { it.usedInFix }, strongest)
}

/** The lowest value of the Y axis (`axisMinimum = 1f` in the original). */
const val Y_AXIS_MINIMUM = 1

/** Number of Y labels (`setLabelCount(8, true)` in the original). */
const val Y_LABEL_COUNT = 8

/** Space above the strongest bar, as a fraction of it (`spaceTop = 15f` in the original). */
const val Y_HEADROOM = 0.15f

/** At most this many X labels, so they never overlap (every 3rd of 20 satellites, as in the screenshot). */
const val MAX_X_LABELS = 7
