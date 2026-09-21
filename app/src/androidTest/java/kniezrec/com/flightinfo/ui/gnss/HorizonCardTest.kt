package kniezrec.com.flightinfo.ui.gnss

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.horizon.HorizonState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HorizonCardTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun waitingAndUnavailableDoNotOfferAttitudeActions() {
        composeRule.setContent { HorizonCard(HorizonState.Waiting, {}, {}) }
        composeRule.onNodeWithText("Horizon").assertIsDisplayed()
        composeRule.onNodeWithText("Waiting for attitude data…").assertIsDisplayed()
        composeRule.onNodeWithText("Calibrate").assertDoesNotExist()

        composeRule.setContent { HorizonCard(HorizonState.Unavailable, {}, {}) }
        composeRule.onNodeWithText("Horizon unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test fun availableSummaryAndCalibrateAreAccessible() {
        composeRule.setContent { HorizonCard(HorizonState.Available(12, -8, -.14f, -8f), {}, {}) }
        composeRule.onNodeWithText("Pitch: 12° up · Roll: 8° left").assertIsDisplayed()
        composeRule.onNode(hasStateDescription("Sets the current pitch as level.")).assertExists()
    }

    @Test fun errorShowsAccessibleRetry() {
        composeRule.setContent { HorizonCard(HorizonState.Error, {}, {}) }
        composeRule.onNodeWithText("Unable to read horizon").assertIsDisplayed()
        composeRule.onNode(hasStateDescription("Retries the attitude sensor.")).assertExists()
    }
}
