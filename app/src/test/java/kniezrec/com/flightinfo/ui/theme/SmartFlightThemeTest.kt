package kniezrec.com.flightinfo.ui.theme

import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.display.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The theme follows the system night mode by default; the Theme setting's Light and Dark override
 * it. Either way the colors are the app's own tokens (no dynamic color).
 */
@RunWith(AndroidJUnit4::class)
class SmartFlightThemeTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "+night")
    fun systemModeFollowsTheNightQualifier() {
        assertScheme(expectedSystemDark = true, mode = ThemeMode.SYSTEM, expected = DarkSmartFlightColors)
    }

    @Test
    @Config(qualifiers = "+notnight")
    fun systemModeFollowsTheNotNightQualifier() {
        assertScheme(expectedSystemDark = false, mode = ThemeMode.SYSTEM, expected = LightSmartFlightColors)
    }

    @Test
    @Config(qualifiers = "+night")
    fun lightOverridesADarkSystem() {
        assertScheme(expectedSystemDark = true, mode = ThemeMode.LIGHT, expected = LightSmartFlightColors)
    }

    @Test
    @Config(qualifiers = "+notnight")
    fun darkOverridesALightSystem() {
        assertScheme(expectedSystemDark = false, mode = ThemeMode.DARK, expected = DarkSmartFlightColors)
    }

    @Test
    @Config(qualifiers = "+notnight")
    fun defaultParameterFollowsTheSystem() {
        var tokens: SmartFlightColors? = null
        composeRule.setContent { SmartFlightTheme { tokens = SmartFlightTheme.colors } }
        composeRule.waitForIdle()

        assertEquals(LightSmartFlightColors, tokens)
    }

    private fun assertScheme(
        expectedSystemDark: Boolean,
        mode: ThemeMode,
        expected: SmartFlightColors,
    ) {
        var systemDark: Boolean? = null
        var scheme: ColorScheme? = null
        var tokens: SmartFlightColors? = null
        composeRule.setContent {
            systemDark = isSystemInDarkTheme()
            ThemedBy(mode) {
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
        assertEquals(expected, tokens)
        assertSchemeHoldsTheTokens(expected, checkNotNull(scheme))
    }

    /** As MainActivity: the Theme setting decides, given the system night mode. */
    @Composable
    private fun ThemedBy(
        mode: ThemeMode,
        content: @Composable () -> Unit,
    ) {
        SmartFlightTheme(darkTheme = mode.isDark(isSystemInDarkTheme()), content = content)
    }

    private fun assertSchemeHoldsTheTokens(
        colors: SmartFlightColors,
        actual: ColorScheme,
    ) {
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
        assertEquals(colors.cardOutline, actual.outlineVariant)
        assertEquals(colors.accentContainer, actual.primaryContainer)
        assertEquals(colors.accent, actual.onPrimaryContainer)
        assertEquals(colors.error, actual.error)
        assertEquals(colors.inverseSurface, actual.inverseSurface)
        assertEquals(colors.inverseOnSurface, actual.inverseOnSurface)
        assertEquals(colors.inversePrimary, actual.inversePrimary)
    }
}
