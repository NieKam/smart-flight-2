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

        assertArgb(0xFF111722, colors.page)
        assertArgb(0xFF1A2330, colors.card)
        assertArgb(0xFF232D3C, colors.raised)
        assertArgb(0xFF111722, colors.topBar)
        assertArgb(0xFF1E2735, colors.topBarScrolled)
        assertArgb(0xFF283245, colors.cardOutline)
        assertArgb(0xFF4A9BFD, colors.accent)
        assertArgb(0xFF1C304A, colors.accentContainer)
        assertArgb(0xFF23436B, colors.accentLight)
        assertArgb(0xFF9DAED0, colors.labelText)
        assertArgb(0xFFE8ECF5, colors.valueText)
        assertArgb(0xFFE8ECF5, colors.toolbarTitle)
        assertArgb(0xFF4A9BFD, colors.compassPlane)
        assertArgb(0xFF4CAF50, colors.satelliteUsed)
        assertArgb(0xFFFF7A6E, colors.satelliteUnused)
        assertArgb(0xFFFFB4AB, colors.error)
        assertArgb(0xFFE8ECF5, colors.inverseSurface)
        assertArgb(0xFF172340, colors.inverseOnSurface)
        assertArgb(0xFF1558C0, colors.inversePrimary)
        assertArgb(0xFF0F3D74, colors.horizonSkyTop)
        assertArgb(0xFF1C5EA0, colors.horizonSky)
        assertArgb(0xFF295744, colors.horizonGround)
        assertArgb(0xFF143839, colors.horizonGroundBottom)
        assertArgb(0xFFE8ECF5, colors.mapRoute)
        assertArgb(0xFF4A5878, colors.mapTileTint)
        assertSharedTokens(colors)
    }

    @Test
    fun lightTokensHoldTheTokenTable() {
        val colors = LightSmartFlightColors

        assertArgb(0xFFF4F6FB, colors.page)
        assertArgb(0xFFFFFFFF, colors.card)
        assertArgb(0xFFF9FAFD, colors.raised)
        assertArgb(0xFFF4F6FB, colors.topBar)
        assertArgb(0xFFE9EDF5, colors.topBarScrolled)
        assertArgb(0xFFE2E7F0, colors.cardOutline)
        assertArgb(0xFF1A66D9, colors.accent)
        assertArgb(0xFFE7F0FD, colors.accentContainer)
        assertArgb(0xFFCFE0FA, colors.accentLight)
        assertArgb(0xFF5B6785, colors.labelText)
        assertArgb(0xFF172340, colors.valueText)
        assertArgb(0xFF172340, colors.toolbarTitle)
        assertArgb(0xFF172340, colors.compassPlane)
        assertArgb(0xFF2E7D32, colors.satelliteUsed)
        assertArgb(0xFFC62828, colors.satelliteUnused)
        assertArgb(0xFFB3261E, colors.error)
        assertArgb(0xFF172340, colors.inverseSurface)
        assertArgb(0xFFF1F4FA, colors.inverseOnSurface)
        assertArgb(0xFF8DBBFF, colors.inversePrimary)
        assertArgb(0xFF2A73C9, colors.horizonSkyTop)
        assertArgb(0xFF3B87DB, colors.horizonSky)
        assertArgb(0xFF3E7F5B, colors.horizonGround)
        assertArgb(0xFF2B6249, colors.horizonGroundBottom)
        assertArgb(0xFF484685, colors.mapRoute)
        assertArgb(0xFFFFFFFF, colors.mapTileTint)
        assertSharedTokens(colors)
    }

    @Test
    fun surfaceRolesAreDistinctTones() {
        // The top bar at rest shares the page tone in both schemes (seamless top bar, TASK-039).
        for (colors in listOf(DarkSmartFlightColors, LightSmartFlightColors)) {
            val surfaces =
                listOf(colors.page, colors.card, colors.raised, colors.topBarScrolled, colors.cardOutline, colors.accentContainer)
            assertEquals(surfaces.size, surfaces.toSet().size)
            assertNotEquals(colors.page, colors.inverseSurface)
        }
    }

    @Test
    fun topBarAtRestMatchesThePage() {
        assertEquals(LightSmartFlightColors.page, LightSmartFlightColors.topBar)
        assertEquals(DarkSmartFlightColors.page, DarkSmartFlightColors.topBar)
    }

    @Test
    fun contrastRatioSpansOneToTwentyOne() {
        assertEquals(21f, contrastRatio(Color.White, Color.Black), 0.01f)
        assertEquals(21f, contrastRatio(Color.Black, Color.White), 0.01f)
        assertEquals(1f, contrastRatio(Color.Red, Color.Red), 0.0001f)
    }

    /** The map pins, plane marker and buttons look the same in every theme. */
    private fun assertSharedTokens(colors: SmartFlightColors) {
        assertArgb(0x80000000, colors.overlay50)
        assertArgb(0x33000000, colors.overlay20)
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
