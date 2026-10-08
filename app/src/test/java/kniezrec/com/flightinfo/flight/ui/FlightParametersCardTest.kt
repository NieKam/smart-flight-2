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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
        // The tiles are shown while waiting, every value unknown (TASK-043).
        composeRule.onAllNodesWithText("—").assertCountEquals(4)
        composeRule.onNodeWithText("km/h").assertIsDisplayed()

        composeRule.runOnIdle { flightState = FlightParametersState.Readings(36.0, null, 100.0, 1013.25) }
        composeRule.onAllNodesWithText("Waiting for GPS position…").assertCountEquals(0)
        composeRule.onNodeWithText("36.0").assertIsDisplayed()
        composeRule.onNodeWithText("—").assertIsDisplayed()
        composeRule.onNodeWithText("100.0").assertIsDisplayed()
        // As the original "%.1f": no grouping separator, 1013.25 rounded half up.
        composeRule.onNodeWithText("1013.3").assertIsDisplayed()
        composeRule.onNodeWithText("mbar").assertIsDisplayed()
    }

    @Test fun pressureOnlyShowsThePressureRowAndDashesForGpsRows() {
        composeRule.setContent { DashboardFlightCard(FlightParametersState.Readings(null, null, null, 1013.25)) }

        composeRule.onAllNodesWithText("Waiting for GPS position…").assertCountEquals(0)
        composeRule.onNodeWithText("1013.3").assertIsDisplayed()
        composeRule.onAllNodesWithText("—").assertCountEquals(3)
        composeRule.onNodeWithContentDescription("Speed, unavailable").assertExists()
        composeRule.onNodeWithContentDescription("Vertical speed, unavailable").assertExists()
        composeRule.onNodeWithContentDescription("Altitude, unavailable").assertExists()
    }

    @Test fun flightParametersPressureRowHasOrderPlaceholderAndAccessibility() {
        composeRule.setContent { DashboardFlightCard(FlightParametersState.Readings(36.0, 1.2, 100.0)) }

        // The design's order, read row by row (TASK-043).
        val labels = listOf("Speed", "Altitude", "Vertical speed", "Pressure")
        val positions =
            labels.map { label ->
                val bounds =
                    composeRule
                        .onNodeWithText(label)
                        .fetchSemanticsNode()
                        .boundsInRoot
                bounds.top to bounds.left
            }
        val inReadingOrder =
            positions.zipWithNext().all { (first, next) ->
                first.first < next.first || (first.first == next.first && first.second < next.second)
            }
        assertTrue("tiles out of reading order: $positions", inReadingOrder)
        composeRule.onNodeWithContentDescription("Pressure, unavailable").assertExists()
    }

    @Test fun flightParametersPressureAccessibilityExpandsUnitName() {
        composeRule.setContent { DashboardFlightCard(FlightParametersState.Readings(36.0, 1.2, 100.0, 1013.25)) }

        composeRule.onNodeWithContentDescription("Pressure, 1013.3 millibars").assertExists()
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
