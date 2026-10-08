package kniezrec.com.flightinfo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SmartFlightColorsTest {
    @Test
    fun darkTokensHoldTheTokenTable() {
        val colors = DarkSmartFlightColors

        assertArgb(0xFF38366E, colors.page)
        assertArgb(0xFF484685, colors.card)
        assertArgb(0xFF4F4D8E, colors.raised)
        assertArgb(0xFF5B5999, colors.topBar)
        assertArgb(0xFF67659F, colors.topBarScrolled)
        assertArgb(0xFF25E5FE, colors.accent)
        assertArgb(0x8025E5FE, colors.accentPressed)
        assertArgb(0xFF99E5FC, colors.accentLight)
        assertArgb(0xFFCAC9E3, colors.labelText)
        assertArgb(0xFFF1F0FA, colors.valueText)
        assertArgb(0xFFFFFFFF, colors.toolbarTitle)
        assertArgb(0xFF4CAF50, colors.satelliteUsed)
        assertArgb(0xFFFF7A6E, colors.satelliteUnused)
        assertArgb(0xFFFFC0B8, colors.error)
        assertArgb(0xFFE8E7F5, colors.inverseSurface)
        assertArgb(0xFF2C2163, colors.inverseOnSurface)
        assertArgb(0xFF00687A, colors.inversePrimary)
        assertSharedTokens(colors)
    }

    @Test
    fun lightTokensHoldTheTokenTable() {
        val colors = LightSmartFlightColors

        assertArgb(0xFFE6E4F4, colors.page)
        assertArgb(0xFFF7F6FC, colors.card)
        assertArgb(0xFFFDFCFF, colors.raised)
        assertArgb(0xFFE6E4F4, colors.topBar)
        assertArgb(0xFFDAD7EF, colors.topBarScrolled)
        assertArgb(0xFF00687A, colors.accent)
        assertArgb(0x8000687A, colors.accentPressed)
        assertArgb(0xFF99E5FC, colors.accentLight)
        assertArgb(0xFF55537D, colors.labelText)
        assertArgb(0xFF1E1C3A, colors.valueText)
        assertArgb(0xFF2C2163, colors.toolbarTitle)
        assertArgb(0xFF2E7D32, colors.satelliteUsed)
        assertArgb(0xFFC62828, colors.satelliteUnused)
        assertArgb(0xFFB3261E, colors.error)
        assertArgb(0xFF2C2163, colors.inverseSurface)
        assertArgb(0xFFF1F0FA, colors.inverseOnSurface)
        assertArgb(0xFF25E5FE, colors.inversePrimary)
        assertSharedTokens(colors)
    }

    @Test
    fun surfaceRolesAreDistinctTones() {
        // The light top bar at rest shares the page tone (seamless Material 3 top bar, TASK-038).
        val schemes =
            listOf(
                DarkSmartFlightColors to listOf(DarkSmartFlightColors.topBar),
                LightSmartFlightColors to emptyList(),
            )
        for ((colors, extraSurfaces) in schemes) {
            val surfaces = listOf(colors.page, colors.card, colors.raised, colors.topBarScrolled) + extraSurfaces
            assertEquals(surfaces.size, surfaces.toSet().size)
            assertNotEquals(colors.page, colors.inverseSurface)
        }
    }

    @Test
    fun lightTopBarAtRestMatchesThePage() {
        assertEquals(LightSmartFlightColors.page, LightSmartFlightColors.topBar)
    }

    @Test
    fun contrastRatioSpansOneToTwentyOne() {
        assertEquals(21f, contrastRatio(Color.White, Color.Black), 0.01f)
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(1f, contrastRatio(Color.Red, Color.Red), 0.0001f)
    }

    /** The map overlays and the horizon instrument look the same in every theme. */
    private fun assertSharedTokens(colors: SmartFlightColors) {
        assertArgb(0x80000000, colors.overlay50)
        assertArgb(0x33000000, colors.overlay20)
        assertArgb(0xFF7775B5, colors.horizonSky)
        assertArgb(0xFF3F3D70, colors.horizonGround)
        assertArgb(0xFFFFFFFF, colors.horizonLine)
        assertArgb(0xFF484685, colors.mapInk)
        assertArgb(0xFFD9D9ED, colors.mapHalo)
    }

    private fun assertArgb(
        expected: Long,
        actual: Color,
    ) {
        assertEquals(expected.toInt(), actual.toArgb())
    }
}
