package kniezrec.com.flightinfo.ui.permission

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

internal val cardPurple = Color(0xFF5B5999)
internal val actionCyan = Color(0xFF6CF0FF)

internal fun contrastRatio(
    foreground: Color,
    background: Color,
): Float {
    fun linearized(component: Float): Float =
        if (component <= 0.04045f) {
            component / 12.92f
        } else {
            val base = (component + 0.055f) / 1.055f
            base.toDouble().pow(2.4).toFloat()
        }

    fun luminance(color: Color): Float =
        0.2126f * linearized(color.red) +
            0.7152f * linearized(color.green) +
            0.0722f * linearized(color.blue)

    val lighter = maxOf(luminance(foreground), luminance(background))
    val darker = minOf(luminance(foreground), luminance(background))
    return (lighter + 0.05f) / (darker + 0.05f)
}
