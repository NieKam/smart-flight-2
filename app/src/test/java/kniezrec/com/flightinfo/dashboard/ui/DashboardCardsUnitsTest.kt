package kniezrec.com.flightinfo.dashboard.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import kniezrec.com.flightinfo.displayunits.AltitudeUnit
import kniezrec.com.flightinfo.displayunits.DistanceUnit
import kniezrec.com.flightinfo.displayunits.PressureUnit
import kniezrec.com.flightinfo.displayunits.SpeedUnit
import kniezrec.com.flightinfo.displayunits.UnitPreferences
import kniezrec.com.flightinfo.displayunits.VerticalSpeedUnit
import kniezrec.com.flightinfo.flight.FlightParametersState
import kniezrec.com.flightinfo.flight.ui.FlightParametersCard
import kniezrec.com.flightinfo.nearby.NearbyCityState
import kniezrec.com.flightinfo.nearby.ui.NearbyCityCard
import kniezrec.com.flightinfo.route.RouteDetails
import kniezrec.com.flightinfo.route.RouteState
import kniezrec.com.flightinfo.route.ui.RouteCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The unit-dependent cards follow a units change at once, as the dashboard passes the units down. */
@RunWith(AndroidJUnit4::class)
class DashboardCardsUnitsTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Tall window so all three cards are on screen; fixed locale for the arrival.
    @Config(qualifiers = "en-rUS-w411dp-h2000dp")
    @Test
    fun changingUnitsImmediatelyUpdatesFlightNearbyAndRouteValues() {
        var preferences by mutableStateOf(UnitPreferences())
        composeRule.setContent {
            Column(Modifier.padding(horizontal = 12.dp)) {
                FlightParametersCard(FlightParametersState.Readings(36.0, 1.0, 100.0, 1013.25), preferences)
                NearbyCityCard(
                    NearbyCityState.Available("Nearby", "US", 10.0, ZoneOffset.UTC, Instant.EPOCH, 0),
                    onRetry = {},
                    distanceUnit = preferences.distance,
                )
                RouteCard(
                    state = RouteState(details = RouteDetails(100.0, 50.0, ARRIVAL, ZoneOffset.UTC, Duration.ofHours(1))),
                    onChoose = {},
                    onClear = {},
                    onClearAll = {},
                    distanceUnit = preferences.distance,
                )
            }
        }
        // Flight parameter tiles: number, label and unit symbol; the description joins them (TASK-043).
        composeRule.onNodeWithContentDescription("Speed, 36.0 kilometres per hour").assertIsDisplayed()
        composeRule.onNodeWithText("km/h").assertIsDisplayed()
        composeRule.onNodeWithText("10.0 km").assertIsDisplayed()
        composeRule.onNodeWithText("100.0 km").assertIsDisplayed()
        composeRule.onNodeWithText("50.0 km").assertIsDisplayed()
        composeRule.onNodeWithText("$ARRIVAL_TEXT (01:00)").assertIsDisplayed()

        composeRule.runOnIdle {
            preferences =
                UnitPreferences(
                    speed = SpeedUnit.MILES_PER_HOUR,
                    altitude = AltitudeUnit.FEET,
                    distance = DistanceUnit.MILES,
                    verticalSpeed = VerticalSpeedUnit.FEET_PER_MINUTE,
                    pressure = PressureUnit.INCHES_OF_MERCURY,
                )
        }

        composeRule.onNodeWithContentDescription("Speed, 22.4 miles per hour").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Vertical speed, +196.9 feet per minute").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Altitude, 328.1 feet").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Pressure, 29.9 inches of mercury").assertIsDisplayed()
        for (symbol in listOf("mph", "ft/min", "ft", "inHg")) composeRule.onNodeWithText(symbol).assertIsDisplayed()
        composeRule.onNodeWithText("6.2 mi").assertIsDisplayed()
        composeRule.onNodeWithText("62.1 mi").assertIsDisplayed()
        composeRule.onNodeWithText("31.1 mi").assertIsDisplayed()
        composeRule.onNodeWithText("$ARRIVAL_TEXT (01:00)").assertIsDisplayed()
    }

    private companion object {
        val ARRIVAL: Instant = Instant.parse("2020-01-01T10:00:00Z")

        /** [ARRIVAL] as the route card shows it with the test's en-US locale. */
        val ARRIVAL_TEXT: String =
            DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.US)
                .format(ARRIVAL.atZone(ZoneOffset.UTC))
    }
}
