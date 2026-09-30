package kniezrec.com.flightinfo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartFlightColorsTest {
    @Test
    fun tokensKeepTheColorsThatWereInlinedInTheUi() {
        val colors = DefaultSmartFlightColors

        assertArgb(0xFF484685, colors.page)
        assertArgb(0xFF5B5999, colors.card)
        assertArgb(0xFF6CF0FF, colors.accent)
        assertArgb(0xFFD9D9ED, colors.text)
        assertArgb(0xFFFFB4AB, colors.error)
        assertArgb(0xFF7775B5, colors.horizonSky)
        assertArgb(0xFF3F3D70, colors.horizonGround)
        assertArgb(0xDD25133F, colors.mapButtonBackground)
        assertArgb(0xFFFFFFFF, colors.mapOverlayContent)
        assertArgb(0xFF211D46, colors.pickerBackground)
    }

    @Test
    fun accentHasAccessibleContrastOnTheCard() {
        assertTrue(contrastRatio(DefaultSmartFlightColors.accent, DefaultSmartFlightColors.card) >= 4.5f)
    }

    @Test
    fun contrastRatioSpansOneToTwentyOne() {
        assertEquals(21f, contrastRatio(Color.White, Color.Black), 0.01f)
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(1f, contrastRatio(Color.Red, Color.Red), 0.0001f)
    }

    private fun assertArgb(
        expected: Long,
        actual: Color,
    ) {
        assertEquals(expected.toInt(), actual.toArgb())
    }
}
