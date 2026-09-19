package kniezrec.com.flightinfo.ui.gnss

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.gnss.GnssSatellite
import kniezrec.com.flightinfo.gnss.GnssStatusState
import kniezrec.com.flightinfo.flight.FlightParametersState
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
                flightParametersState = FlightParametersState.Waiting,
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
            GnssStatusScreen(GnssStatusState.LocationServicesDisabled, FlightParametersState.Waiting, {}, {})
        }
        composeRule.onNodeWithText("Open location settings").assertIsDisplayed()
    }

    @Test fun flightParametersShowWaitingAndPartialReadings() {
        composeRule.setContent {
            GnssStatusScreen(
                state = GnssStatusState.Waiting,
                flightParametersState = FlightParametersState.Waiting,
                onOpenLocationSettings = {},
                onRetry = {},
            )
        }
        composeRule.onNodeWithText("Waiting for GPS position…").assertIsDisplayed()

        composeRule.setContent {
            GnssStatusScreen(
                state = GnssStatusState.Waiting,
                flightParametersState = FlightParametersState.Readings(36.0, null, 100.0),
                onOpenLocationSettings = {},
                onRetry = {},
            )
        }
        composeRule.onNodeWithText("36.0 km/h").assertIsDisplayed()
        composeRule.onNodeWithText("—").assertIsDisplayed()
        composeRule.onNodeWithText("100.0 m").assertIsDisplayed()
    }

    @Test fun flightParametersAnnounceAvailabilityAfterWaiting() {
        var flightState by mutableStateOf<FlightParametersState>(FlightParametersState.Waiting)
        composeRule.setContent {
            GnssStatusScreen(GnssStatusState.Waiting, flightState, {}, {})
        }

        composeRule.runOnIdle {
            flightState = FlightParametersState.Readings(36.0, null, 100.0)
        }

        composeRule.onNodeWithContentDescription("Flight parameters available").assertExists()
    }

}
