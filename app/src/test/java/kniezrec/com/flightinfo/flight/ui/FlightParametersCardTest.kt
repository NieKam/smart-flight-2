package kniezrec.com.flightinfo.flight.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.flight.FlightParametersState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FlightParametersCardTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun flightParametersShowWaitingAndPartialReadings() {
        // The rule allows one setContent per test, so the state is switched in place.
        var flightState by mutableStateOf<FlightParametersState>(FlightParametersState.Waiting)
        composeRule.setContent { DashboardFlightCard(flightState) }
        composeRule.onNodeWithText("Waiting for GPS position…").assertIsDisplayed()

        composeRule.runOnIdle { flightState = FlightParametersState.Readings(36.0, null, 100.0, 1013.25) }
        composeRule.onNodeWithText("36.0 km/h").assertIsDisplayed()
        composeRule.onNodeWithText("—").assertIsDisplayed()
        composeRule.onNodeWithText("100.0 m").assertIsDisplayed()
        // NumberFormat: grouping separator and HALF_EVEN rounding of 1013.25.
        composeRule.onNodeWithText("1,013.2 mbar").assertIsDisplayed()
    }

    @Test fun flightParametersPressureRowHasOrderPlaceholderAndAccessibility() {
        composeRule.setContent { DashboardFlightCard(FlightParametersState.Readings(36.0, 1.2, 100.0)) }

        val labels = listOf("Speed", "Vertical speed", "Altitude", "Pressure")
        val tops =
            labels.map { label ->
                composeRule
                    .onNodeWithText(label)
                    .fetchSemanticsNode()
                    .boundsInRoot.top
            }
        assertTrue(tops.zipWithNext().all { (upper, lower) -> upper < lower })
        composeRule.onNodeWithContentDescription("Pressure unavailable").assertExists()
    }

    @Test fun flightParametersPressureAccessibilityExpandsUnitName() {
        composeRule.setContent { DashboardFlightCard(FlightParametersState.Readings(36.0, 1.2, 100.0, 1013.25)) }

        composeRule.onNodeWithContentDescription("Pressure 1,013.2 millibars").assertExists()
    }

    @Test fun flightParametersAnnounceAvailabilityAfterWaiting() {
        var flightState by mutableStateOf<FlightParametersState>(FlightParametersState.Waiting)
        composeRule.setContent { DashboardFlightCard(flightState) }

        composeRule.runOnIdle {
            flightState = FlightParametersState.Readings(36.0, null, 100.0)
        }

        composeRule.onNodeWithContentDescription("Flight parameters available").assertExists()
    }

    /** The card with the dashboard's list padding and card modifier. */
    @Composable
    private fun DashboardFlightCard(state: FlightParametersState) {
        Box(Modifier.padding(horizontal = 12.dp)) {
            FlightParametersCard(state, modifier = Modifier.padding(bottom = 12.dp).widthIn(max = 600.dp))
        }
    }
}
