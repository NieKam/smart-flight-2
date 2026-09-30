package kniezrec.com.flightinfo.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.AppScaffold
import kniezrec.com.flightinfo.dashboard.ui.DashboardHeader
import kniezrec.com.flightinfo.flight.FlightParametersState
import kniezrec.com.flightinfo.flight.ui.FlightParametersCard
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Rendered colors of the dashboard chrome (the app's scaffold, top bar and a card) sampled from a
 * native-graphics capture: page #484685, card #5B5999, top bar #5B5999.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardColorsPixelTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pageCardAndTopBarAreDrawnInThePaletteColors() {
        composeRule.setContent {
            SmartFlightTheme {
                AppScaffold(remember { SnackbarHostState() }) { contentModifier ->
                    Column(contentModifier.fillMaxSize()) {
                        DashboardHeader(onOpenSettings = {}, onOpenAbout = {})
                        FlightParametersCard(FlightParametersState.Waiting, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        val density = composeRule.density.density
        val inset = (4 * density).roundToInt()
        val header = composeRule.onNodeWithText("Smart Flight").fetchSemanticsNode().boundsInRoot
        val cardTitle = composeRule.onNodeWithText("Flight parameters").fetchSemanticsNode().boundsInRoot

        // Top bar: left of the centered title, clear of the actions on the right.
        assertColor("top bar", DefaultSmartFlightColors.card, pixels, inset, header.center.y.roundToInt())
        // Card: inside its left padding (card starts 12dp in), level with the centered title.
        assertColor("card", DefaultSmartFlightColors.card, pixels, (18 * density).roundToInt(), cardTitle.center.y.roundToInt())
        // Page: bottom of the screen, far from any card and its shadow.
        assertColor("page", DefaultSmartFlightColors.page, pixels, pixels.width / 2, pixels.height - inset)
    }

    private fun assertColor(
        area: String,
        expected: Color,
        pixels: PixelMap,
        x: Int,
        y: Int,
    ) {
        val actual = pixels[x, y]
        val channels = listOf(expected.red to actual.red, expected.green to actual.green, expected.blue to actual.blue)
        val close = channels.all { (want, got) -> abs((want * 255).roundToInt() - (got * 255).roundToInt()) <= TOLERANCE }
        assertTrue("$area at ($x, $y): expected ${hex(expected)}, was ${hex(actual)}", close)
    }

    private fun hex(color: Color) =
        "#%02X%02X%02X".format((color.red * 255).roundToInt(), (color.green * 255).roundToInt(), (color.blue * 255).roundToInt())

    private companion object {
        // Native graphics may differ from device rendering by a few units per channel.
        const val TOLERANCE = 2
    }
}
