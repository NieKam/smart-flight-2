package kniezrec.com.flightinfo.gnss.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.gnss.GnssSatellite
import kniezrec.com.flightinfo.gnss.GnssStatusState
import kniezrec.com.flightinfo.ui.theme.DarkSmartFlightColors
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Rendered colors of the satellite chart (native graphics): used and unused satellites in their
 * tokens on the card.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SatelliteChartPixelTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun usedAndUnusedBarsAreDrawnInTheSatelliteColorsOnTheCard() {
        composeRule.setContent {
            SmartFlightTheme {
                GnssStatusCard(
                    GnssStatusState.Available(listOf(GnssSatellite(true, 30f), GnssSatellite(false, 25f))),
                    onOpenLocationSettings = {},
                    onRetry = {},
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        composeRule.waitForIdle()

        // As in DashboardColorsPixelTest: captureToImage() never completes on Robolectric
        // (robolectric/robolectric#8071), so the Compose host view is drawn into a bitmap directly.
        val host = composeRule.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val bitmap = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
        composeRule.runOnIdle { host.draw(Canvas(bitmap)) }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val colors = DarkSmartFlightColors
        assertContains("used bar", colors.satelliteUsed, pixels)
        assertContains("unused bar", colors.satelliteUnused, pixels)
        assertContains("card", colors.card, pixels)
    }

    private fun assertContains(
        area: String,
        expected: Color,
        pixels: IntArray,
    ) {
        val want = expected.toArgb()
        val count =
            pixels.count { got ->
                listOf(16, 8, 0).all { shift ->
                    abs((want shr shift and 0xFF) - (got shr shift and 0xFF)) <=
                        TOLERANCE
                }
            }
        assertTrue("$area: expected pixels of #%08X, found $count".format(want), count >= MIN_PIXELS)
    }

    private companion object {
        const val TOLERANCE = 2
        const val MIN_PIXELS = 100
    }
}
