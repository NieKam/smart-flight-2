package kniezrec.com.flightinfo.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * Draws the decorative city skyline of the redesign (Nearby city and Route planning illustrations)
 * in [color] between [left] and [right], standing on [base] and at most [maxHeight] tall (pixels).
 */
fun DrawScope.drawSkyline(
    color: Color,
    left: Float,
    right: Float,
    base: Float,
    maxHeight: Float,
) {
    val width = right - left
    for ((x, buildingWidth, height) in SKYLINE_BUILDINGS) {
        drawRect(
            color,
            topLeft = Offset(left + width * x, base - maxHeight * height),
            size = Size(width * buildingWidth, maxHeight * height),
        )
    }
}

/** Draws a small cloud of three overlapping circles in [color] around [center], [radius] pixels tall. */
fun DrawScope.drawCloud(
    color: Color,
    center: Offset,
    radius: Float,
) {
    drawCircle(color, radius, center)
    drawCircle(color, radius * 0.75f, center + Offset(-radius * 1.1f, radius * 0.25f))
    drawCircle(color, radius * 0.75f, center + Offset(radius * 1.1f, radius * 0.25f))
}

/** Buildings as (left, width, height) fractions of the skyline's range and maximum height. */
private val SKYLINE_BUILDINGS =
    listOf(
        Triple(0f, 0.14f, 0.55f),
        Triple(0.17f, 0.11f, 0.85f),
        Triple(0.31f, 0.17f, 0.68f),
        Triple(0.51f, 0.09f, 1f),
        Triple(0.63f, 0.15f, 0.6f),
        Triple(0.81f, 0.13f, 0.9f),
    )
