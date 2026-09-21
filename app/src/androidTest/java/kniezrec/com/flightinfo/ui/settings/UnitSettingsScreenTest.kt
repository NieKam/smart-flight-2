package kniezrec.com.flightinfo.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertExists
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.displayunits.DistanceUnit
import kniezrec.com.flightinfo.displayunits.UnitPreferences
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
}
