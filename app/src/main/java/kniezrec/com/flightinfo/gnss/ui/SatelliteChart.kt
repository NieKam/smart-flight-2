package kniezrec.com.flightinfo.gnss.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.gnss.SatelliteChartModel
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme
import kotlin.math.max
import kotlin.math.roundToInt

/** Height of the chart area; the card does not grow with the number of satellites. */
internal val SATELLITE_CHART_HEIGHT = 200.dp

/**
 * The original signal-strength bar chart: one bar per satellite, [SmartFlightColors.satelliteUsed]
 * when used in the fix and [SmartFlightColors.satelliteUnused] otherwise, Y labels in dB-Hz on the
 * left with horizontal grid lines, satellite indexes below. Bars get thinner with more satellites
 * (all of them stay on the chart). For accessibility the chart is one summary description, not a
 * list of focusable bars.
 */
@Composable
internal fun SatelliteChart(
    chart: SatelliteChartModel,
    modifier: Modifier = Modifier,
) {
    val colors = SmartFlightTheme.colors
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = colors.valueText, fontSize = 12.sp)
    val visible = chart.bars.size
    val description =
        chart.strongestDbHz?.let { strongest ->
            pluralStringResource(R.plurals.gnss_chart_description, visible, visible, chart.usedCount, strongest.roundToInt())
        } ?: pluralStringResource(R.plurals.gnss_chart_description_no_signal, visible, visible, chart.usedCount)
    Canvas(modifier.semantics { contentDescription = description }) {
        val yLabelWidth = chart.yLabels.maxOf { textMeasurer.measure(it.toString(), labelStyle).size.width } + 6.dp.toPx()
        val xLabelHeight = textMeasurer.measure("0", labelStyle).size.height + 4.dp.toPx()
        val plotLeft = yLabelWidth
        val plotBottom = size.height - xLabelHeight
        val plotTop = textMeasurer.measure("0", labelStyle).size.height / 2f
        val plotWidth = size.width - plotLeft
        val plotHeight = plotBottom - plotTop
        val lineColor = colors.labelText.copy(alpha = GRID_ALPHA)

        chart.yLabels.forEachIndexed { index, label ->
            val y = plotBottom - plotHeight * index / (chart.yLabels.size - 1)
            drawLine(lineColor, Offset(plotLeft, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            drawLabel(textMeasurer, label.toString(), labelStyle, rightX = plotLeft - 6.dp.toPx(), centerY = y)
        }
        drawLine(lineColor, Offset(plotLeft, plotTop), Offset(plotLeft, plotBottom), strokeWidth = 1.dp.toPx())

        if (visible == 0) return@Canvas
        val slot = plotWidth / visible
        // Bar width 0.9 of a slot (as the original), never thinner than 2dp; spacing shrinks first.
        val barWidth = max(slot * BAR_WIDTH_FRACTION, minOf(slot, 2.dp.toPx()))
        chart.bars.forEachIndexed { index, bar ->
            val height = plotHeight * chart.heightFraction(bar)
            if (height > 0f) {
                val left = plotLeft + slot * index + (slot - barWidth) / 2f
                drawRect(
                    color = if (bar.usedInFix) colors.satelliteUsed else colors.satelliteUnused,
                    topLeft = Offset(left, plotBottom - height),
                    size = Size(barWidth, height),
                )
            }
        }
        chart.xLabels.forEach { index ->
            val text = textMeasurer.measure(index.toString(), labelStyle)
            val centerX = plotLeft + slot * index + slot / 2f
            drawText(text, topLeft = Offset(centerX - text.size.width / 2f, plotBottom + 4.dp.toPx()))
        }
    }
}

private fun DrawScope.drawLabel(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    rightX: Float,
    centerY: Float,
) {
    val layout = textMeasurer.measure(text, style)
    drawText(layout, topLeft = Offset(rightX - layout.size.width, centerY - layout.size.height / 2f))
}

private const val BAR_WIDTH_FRACTION = 0.9f
private const val GRID_ALPHA = 0.5f
