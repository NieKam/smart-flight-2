package kniezrec.com.flightinfo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartFlightColorsTest {
    @Test
    fun tokensHoldTheOriginalPaletteAndTheDocumentedExtras() {
        val colors = DefaultSmartFlightColors

        assertArgb(0xFF484685, colors.page)
        assertArgb(0xFF5B5999, colors.card)
        assertArgb(0xFF25E5FE, colors.accent)
        assertArgb(0x8025E5FE, colors.accentPressed)
        assertArgb(0xFF99E5FC, colors.accentLight)
        assertArgb(0xFFA1A0C4, colors.labelText)
        assertArgb(0xFFD9D9ED, colors.valueText)
        assertArgb(0xFF4CAF50, colors.satelliteUsed)
        assertArgb(0xFFF44336, colors.satelliteUnused)
        assertArgb(0x80000000, colors.overlay50)
        assertArgb(0x33000000, colors.overlay20)
        assertArgb(0xFF2C2163, colors.toastBackground)
        assertArgb(0xFFFFFFFF, colors.toolbarTitle)
        assertArgb(0xFFFFB4AB, colors.error)
        assertArgb(0xFF7775B5, colors.horizonSky)
        assertArgb(0xFF3F3D70, colors.horizonGround)
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
