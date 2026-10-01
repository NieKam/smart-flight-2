package kniezrec.com.flightinfo.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.displayunits.SpeedUnit
import kniezrec.com.flightinfo.testutil.FakeBackgroundNotificationSettingsRepository
import kniezrec.com.flightinfo.testutil.FakeCardVisibilityRepository
import kniezrec.com.flightinfo.testutil.FakeDisplaySettingsRepository
import kniezrec.com.flightinfo.testutil.FakeUnitSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The overlay renders its [SettingsViewModel]'s settings; a change is persisted and back is reported. */
@RunWith(AndroidJUnit4::class)
class SettingsOverlayTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun choosingASpeedUnitPersistsItAndNavigateUpGoesBack() {
        val units = FakeUnitSettingsRepository()
        val viewModel =
            SettingsViewModel(
                units,
                FakeDisplaySettingsRepository(),
                FakeBackgroundNotificationSettingsRepository(),
                FakeCardVisibilityRepository(),
            )
        var back = false
        composeRule.setContent { SettingsOverlay(onBack = { back = true }, viewModel = viewModel) }

        composeRule
            .onNodeWithContentDescription("Speed, current value km/h, double tap to change")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Miles per hour (mph)").performClick()
        composeRule.runOnIdle { assertEquals(SpeedUnit.MILES_PER_HOUR, units.units.value.speed) }
        composeRule.onNodeWithContentDescription("Speed, current value mph, double tap to change").assertExists()

        composeRule.onNodeWithContentDescription("Navigate up").performClick()
        composeRule.runOnIdle { assertTrue(back) }
    }
}
