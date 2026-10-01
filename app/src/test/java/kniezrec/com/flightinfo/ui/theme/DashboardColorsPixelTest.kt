package kniezrec.com.flightinfo.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.AppScaffold
import kniezrec.com.flightinfo.dashboard.ui.DashboardHeader
import kniezrec.com.flightinfo.dashboard.ui.DashboardTopBar
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
 * native-graphics rendering: page, card and top bar in their tokens, and the top bar's scrolled
 * tone once the card list scrolls under it.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardColorsPixelTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pageCardAndTopBarAreDrawnInTheDarkTokens() {
        assertDashboardColors(DarkSmartFlightColors, darkTheme = true)
    }

    @Test
    fun pageCardAndTopBarAreDrawnInTheLightTokens() {
        assertDashboardColors(LightSmartFlightColors, darkTheme = false)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun topBarTakesItsScrolledToneWhenTheListScrollsUnderIt() {
        val colors = DarkSmartFlightColors
        composeRule.setContent {
            SmartFlightTheme(darkTheme = true) {
                val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
                Column(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
                    DashboardTopBar(onOpenSettings = {}, onOpenAbout = {}, scrollBehavior = scrollBehavior)
                    Column(Modifier.weight(1f).testTag(LIST_TAG).verticalScroll(rememberScrollState())) {
                        repeat(4) { FlightParametersCard(FlightParametersState.Waiting, modifier = Modifier.padding(12.dp)) }
                        Column(Modifier.fillMaxWidth().height(2000.dp)) {}
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val inset = (4 * composeRule.density.density).roundToInt()
        val titleY = {
            composeRule
                .onNodeWithText("Smart Flight")
                .fetchSemanticsNode()
                .boundsInRoot.center.y
                .roundToInt()
        }

        assertColor("top bar at rest", colors.topBar, render(), inset, titleY())

        composeRule.onNodeWithTag(LIST_TAG).performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        assertColor("scrolled top bar", colors.topBarScrolled, render(), inset, titleY())
    }

    private fun assertDashboardColors(
        colors: SmartFlightColors,
        darkTheme: Boolean,
    ) {
        composeRule.setContent {
            SmartFlightTheme(darkTheme = darkTheme) {
                AppScaffold(remember { SnackbarHostState() }) { contentModifier ->
                    Column(contentModifier.fillMaxSize()) {
                        DashboardHeader(onOpenSettings = {}, onOpenAbout = {})
                        FlightParametersCard(FlightParametersState.Waiting, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
        composeRule.waitForIdle()

        val pixels = render()
        val density = composeRule.density.density
        val inset = (4 * density).roundToInt()
        val header = composeRule.onNodeWithText("Smart Flight").fetchSemanticsNode().boundsInRoot
        val cardTitle = composeRule.onNodeWithText("Flight parameters").fetchSemanticsNode().boundsInRoot

        // Top bar: left of the centered title, clear of the actions on the right.
        assertColor("top bar", colors.topBar, pixels, inset, header.center.y.roundToInt())
        // Card: inside its left padding (card starts 12dp in), level with the centered title.
        assertColor("card", colors.card, pixels, (18 * density).roundToInt(), cardTitle.center.y.roundToInt())
        // Page: bottom of the screen, far from any card.
        assertColor("page", colors.page, pixels, pixels.width / 2, pixels.height - inset)
    }

    /**
     * Compose's captureToImage() waits for a frame-commit callback that Robolectric never fires
     * (robolectric/robolectric#8071), so the Compose host view is drawn into a bitmap directly;
     * native graphics render it as on a device.
     */
    private fun render(): Bitmap {
        val host = composeRule.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val pixels = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
        composeRule.runOnIdle { host.draw(Canvas(pixels)) }
        return pixels
    }

    private fun assertColor(
        area: String,
        expected: Color,
        pixels: Bitmap,
        x: Int,
        y: Int,
    ) {
        val want = expected.toArgb()
        val got = pixels.getPixel(x, y)
        val close = listOf(16, 8, 0).all { shift -> abs((want shr shift and 0xFF) - (got shr shift and 0xFF)) <= TOLERANCE }
        assertTrue("$area at ($x, $y): expected ${hex(want)}, was ${hex(got)}", close)
    }

    private fun hex(argb: Int) = "#%08X".format(argb)

    private companion object {
        // Native graphics may differ from device rendering by a few units per channel.
        const val TOLERANCE = 2
        const val LIST_TAG = "card-list"
    }
}
