package kniezrec.com.flightinfo.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.displayunits.AltitudeUnit
import kniezrec.com.flightinfo.displayunits.DistanceUnit
import kniezrec.com.flightinfo.displayunits.PressureUnit
import kniezrec.com.flightinfo.displayunits.SpeedUnit
import kniezrec.com.flightinfo.displayunits.UnitPreferences
import kniezrec.com.flightinfo.displayunits.VerticalSpeedUnit
import kniezrec.com.flightinfo.flight.FlightParametersState
import kniezrec.com.flightinfo.gnss.GnssStatusState
import kniezrec.com.flightinfo.ui.gnss.GnssStatusScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnitSettingsScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun showsAllMetricDefaultsAndAccessibleBackAction() {
        composeRule.setContent { UnitSettingsScreen(UnitPreferences(), {}, {}) }
        composeRule.onNodeWithText("Units").assertIsDisplayed()
        composeRule.onNode(hasContentDescription("Speed, current value km/h, double tap to change")).assertExists()
        composeRule.onNode(hasContentDescription("Altitude, current value m, double tap to change")).assertExists()
        composeRule.onNode(hasContentDescription("Distance, current value km, double tap to change")).assertExists()
        composeRule.onNode(hasContentDescription("Vertical speed, current value m/s, double tap to change")).assertExists()
        composeRule.onNode(hasContentDescription("Pressure, current value mbar, double tap to change")).assertExists()
        composeRule.onNodeWithContentDescription("Navigate up").assertExists()
    }

    @Test fun selectingDistanceUpdatesSummaryAndCallbackImmediately() {
        var selected by mutableStateOf(UnitPreferences())
        composeRule.setContent {
            UnitSettingsScreen(selected, { selected = it }, {})
        }
        composeRule.onNode(hasContentDescription("Distance, current value km, double tap to change")).performClick()
        composeRule.onNodeWithText("Miles (mi)").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(DistanceUnit.MILES, selected.distance) }
        composeRule.onNode(hasContentDescription("Distance, current value mi, double tap to change")).assertExists()
    }

    @Test fun everySelectorShowsItsExactOptionsAndUpdatesItsSummary() {
        var selected by mutableStateOf(UnitPreferences())
        composeRule.setContent {
            UnitSettingsScreen(selected, { selected = it }, {})
        }
        val selectors =
            listOf(
                Triple("Speed", listOf("Kilometres per hour (km/h)", "Miles per hour (mph)", "Knots (kt)"), "mph"),
                Triple("Altitude", listOf("Metres (m)", "Feet (ft)"), "ft"),
                Triple("Distance", listOf("Kilometres (km)", "Miles (mi)"), "mi"),
                Triple(
                    "Vertical speed",
                    listOf("Metres per second (m/s)", "Metres per minute (m/min)", "Feet per minute (ft/min)"),
                    "ft/min",
                ),
                Triple("Pressure", listOf("Millibar (mbar)", "Inch of mercury (inHg)"), "inHg"),
            )
        val defaults = listOf("km/h", "m", "km", "m/s", "mbar")
        selectors.forEachIndexed { index, (label, options, chosen) ->
            composeRule.onNode(hasContentDescription("$label, current value ${defaults[index]}, double tap to change")).performClick()
            options.forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
            composeRule.onNodeWithText(options.last()).performClick()
            composeRule.onNode(hasContentDescription("$label, current value $chosen, double tap to change")).assertExists()
        }
        composeRule.runOnIdle {
            assertEquals(SpeedUnit.MILES_PER_HOUR, selected.speed)
            assertEquals(AltitudeUnit.FEET, selected.altitude)
            assertEquals(DistanceUnit.MILES, selected.distance)
            assertEquals(VerticalSpeedUnit.FEET_PER_MINUTE, selected.verticalSpeed)
            assertEquals(PressureUnit.INCHES_OF_MERCURY, selected.pressure)
        }
    }

    @Test fun authorizedDashboardSettingsEntryAndBackReturnToDashboard() {
        var showSettings by mutableStateOf(false)
        composeRule.setContent {
            if (showSettings) {
                UnitSettingsScreen(UnitPreferences(), {}, { showSettings = false })
            } else {
                GnssStatusScreen(
                    state = GnssStatusState.Waiting,
                    flightParametersState = FlightParametersState.Waiting,
                    onOpenLocationSettings = {},
                    onRetry = {},
                    onOpenSettings = { showSettings = true },
                )
            }
        }
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Units").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Navigate up").performClick()
        composeRule.onNodeWithText("GNSS status").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }
}
