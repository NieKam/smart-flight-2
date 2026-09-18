package kniezrec.com.flightinfo.ui.gnss

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.gnss.GnssSatellite
import kniezrec.com.flightinfo.gnss.GnssStatusState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GnssStatusScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun availableStatusShowsUsedCountAndTextualSatelliteStates() {
        composeRule.setContent {
            GnssStatusScreen(
                state = GnssStatusState.Available(listOf(GnssSatellite(true), GnssSatellite(false))),
                onOpenLocationSettings = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithText("Using 1 satellite").assertIsDisplayed()
        composeRule.onNodeWithText("Satellite 1").assertIsDisplayed()
        composeRule.onNodeWithText("Used for position").assertIsDisplayed()
        composeRule.onNodeWithText("Not used for position").assertIsDisplayed()
    }

    @Test fun disabledStateShowsLocationSettingsAction() {
        composeRule.setContent {
            GnssStatusScreen(GnssStatusState.LocationServicesDisabled, {}, {})
        }
        composeRule.onNodeWithText("Open location settings").assertIsDisplayed()
    }
}
