package kniezrec.com.flightinfo.ui.theme

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The theme is the fixed palette: the system dark mode (and the wallpaper) change nothing. */
@RunWith(AndroidJUnit4::class)
class SmartFlightThemeTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "+night")
    fun darkSystemThemeUsesThePalette() {
        assertPaletteScheme(expectedSystemDark = true)
    }

    @Test
    @Config(qualifiers = "+notnight")
    fun lightSystemThemeUsesThePalette() {
        assertPaletteScheme(expectedSystemDark = false)
    }

    private fun assertPaletteScheme(expectedSystemDark: Boolean) {
        var systemDark: Boolean? = null
        var scheme: ColorScheme? = null
        var tokens: SmartFlightColors? = null
        composeRule.setContent {
            systemDark = isSystemInDarkTheme()
            SmartFlightTheme {
                scheme = MaterialTheme.colorScheme
                tokens = SmartFlightTheme.colors
            }
        }
        composeRule.waitForIdle()

        // The qualifier took effect, so both system themes are really exercised.
        assertEquals(expectedSystemDark, systemDark)
        assertEquals(
            expectedSystemDark,
            composeRule.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES,
        )
        val colors = DarkSmartFlightColors
        val actual = checkNotNull(scheme)
        assertEquals(colors, tokens)
        assertEquals(colors.accent, actual.primary)
        assertEquals(colors.page, actual.onPrimary)
        assertEquals(colors.page, actual.background)
        assertEquals(colors.valueText, actual.onBackground)
        assertEquals(colors.page, actual.surface)
        assertEquals(colors.card, actual.surfaceContainer)
        assertEquals(colors.raised, actual.surfaceContainerHigh)
        assertEquals(colors.page, actual.surfaceTint)
        assertEquals(colors.valueText, actual.onSurface)
        assertEquals(colors.labelText, actual.onSurfaceVariant)
        assertEquals(colors.labelText, actual.outline)
        assertEquals(colors.error, actual.error)
        assertEquals(colors.inverseSurface, actual.inverseSurface)
        assertEquals(colors.inverseOnSurface, actual.inverseOnSurface)
        assertEquals(colors.inversePrimary, actual.inversePrimary)
    }
}
