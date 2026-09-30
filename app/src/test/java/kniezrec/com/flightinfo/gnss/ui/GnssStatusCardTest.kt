package kniezrec.com.flightinfo.gnss.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.gnss.GnssSatellite
import kniezrec.com.flightinfo.gnss.GnssStatusState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GnssStatusCardTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun availableStatusShowsUsedCountAndOneChartDescription() {
        composeRule.setContent {
            GnssStatusCard(
                state = GnssStatusState.Available(listOf(GnssSatellite(true, 30.4f), GnssSatellite(false, 12f))),
                onOpenLocationSettings = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithText("Using 1 satellite").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("2 satellites visible, 1 used, strongest signal 30 dB-Hz").assertIsDisplayed()
        // The chart replaces the former text row per satellite.
        assertEquals(0, composeRule.onAllNodesWithText("Satellite 1").fetchSemanticsNodes().size)
    }

    @Test fun chartWithoutSignalStrengthSaysSo() {
        composeRule.setContent {
            GnssStatusCard(GnssStatusState.Available(listOf(GnssSatellite(true))), onOpenLocationSettings = {}, onRetry = {})
        }

        composeRule.onNodeWithContentDescription("1 satellite visible, 1 used, no signal strength").assertExists()
    }

    @Test fun cardHeightDoesNotGrowWithTheNumberOfSatellites() {
        var satellites by mutableStateOf(satellites(5))
        composeRule.setContent {
            Box(Modifier.wrapContentHeight().testTag(CARD)) {
                GnssStatusCard(GnssStatusState.Available(satellites), onOpenLocationSettings = {}, onRetry = {})
            }
        }
        composeRule.mainClock.advanceTimeBy(CROSSFADE_MILLIS)
        val fiveHigh = height()

        composeRule.runOnIdle { satellites = satellites(40) }
        composeRule.mainClock.advanceTimeBy(CROSSFADE_MILLIS)

        assertEquals(fiveHigh, height(), 0.5f)
        composeRule.onNodeWithContentDescription("40 satellites visible, 20 used, strongest signal 29 dB-Hz").assertExists()
    }

    @Test fun disabledStateShowsLocationSettingsAction() {
        var opened = false
        composeRule.setContent {
            GnssStatusCard(GnssStatusState.LocationServicesDisabled, onOpenLocationSettings = { opened = true }, onRetry = {})
        }
        composeRule.onNodeWithText("Open location settings").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(opened) }
    }

    @Test fun unavailableAndErrorStatesShowTheirText() {
        var retried = false
        var state by mutableStateOf<GnssStatusState>(GnssStatusState.Unavailable)
        composeRule.setContent { GnssStatusCard(state, onOpenLocationSettings = {}, onRetry = { retried = true }) }
        composeRule.onNodeWithText("GNSS unavailable").assertIsDisplayed()

        composeRule.runOnIdle { state = GnssStatusState.Error }
        composeRule.onNodeWithText("Unable to read GNSS status").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        composeRule.runOnIdle { assertTrue(retried) }
    }

    private fun height(): Float =
        composeRule
            .onNodeWithTag(CARD)
            .fetchSemanticsNode()
            .size.height
            .toFloat()

    private fun satellites(count: Int) = List(count) { GnssSatellite(usedInFix = it % 2 == 0, signalStrengthDbHz = 10f + it % 20) }

    private companion object {
        const val CARD = "card"
        const val CROSSFADE_MILLIS = 500L
    }
}
