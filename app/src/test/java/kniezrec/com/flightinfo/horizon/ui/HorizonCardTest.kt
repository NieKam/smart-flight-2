package kniezrec.com.flightinfo.horizon.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.horizon.HorizonState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HorizonCardTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun requiredStateTransitionsHavePoliteAnnouncements() {
        var state by mutableStateOf<HorizonState>(HorizonState.Waiting)
        composeRule.setContent { HorizonCard(state, onCalibrate = {}, onResetToAbsolute = {}, onRetry = {}, onHide = {}) }

        composeRule.runOnIdle { state = HorizonState.Recalibrating }
        composeRule.onNode(hasContentDescription("Horizon recalibrating. Waiting for attitude data.")).assertExists()

        composeRule.runOnIdle { state = HorizonState.Available(0, 0, 0f, 0f) }
        composeRule.onNode(hasContentDescription("Horizon calibrated")).assertExists()

        composeRule.runOnIdle { state = HorizonState.Unavailable }
        composeRule.onNode(hasContentDescription("Horizon unavailable")).assertExists()

        composeRule.runOnIdle { state = HorizonState.Error }
        composeRule.onNode(hasContentDescription("Unable to read horizon")).assertExists()
    }

    @Test fun waitingAndUnavailableDoNotOfferAttitudeActions() {
        // The rule allows one setContent per test, so the state is switched in place.
        var state by mutableStateOf<HorizonState>(HorizonState.Waiting)
        composeRule.setContent { HorizonCard(state, onCalibrate = {}, onResetToAbsolute = {}, onRetry = {}, onHide = {}) }
        composeRule.onNodeWithText("Horizon").assertIsDisplayed()
        composeRule.onNodeWithText("Waiting for attitude data…").assertIsDisplayed()
        composeRule.onNodeWithText("Calibrate").assertDoesNotExist()

        composeRule.runOnIdle { state = HorizonState.Unavailable }
        composeRule.onNodeWithText("Calibrate").assertDoesNotExist()
        composeRule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test fun unavailableOffersHidingTheCard() {
        var hides = 0
        composeRule.setContent {
            HorizonCard(HorizonState.Unavailable, onCalibrate = {}, onResetToAbsolute = {}, onRetry = {}, onHide = { hides++ })
        }
        composeRule.onNodeWithText("This device doesn't have a motion sensor. Hide this card?").assertIsDisplayed()
        // The blurred instrument behind the message is decoration only.
        composeRule.onNodeWithText("Horizon").assertDoesNotExist()

        composeRule.onNodeWithText("Hide").assertIsDisplayed().performClick()

        composeRule.runOnIdle { assertEquals(1, hides) }
    }

    @Test fun availableSummaryAndCalibrateAreAccessible() {
        composeRule.setContent {
            HorizonCard(
                HorizonState.Available(12, -8, .14f, 8f),
                onCalibrate = {},
                onResetToAbsolute = {},
                onRetry = {},
                onHide = {},
            )
        }
        composeRule.onNodeWithText("Pitch: 12° up · Roll: 8° left").assertIsDisplayed()
        composeRule.onNode(hasStateDescription("Sets the current pitch as level.")).assertExists()
    }

    @Test fun clickCalibratesAndLongPressResetsToAbsolute() {
        var calibrations = 0
        var resets = 0
        composeRule.setContent {
            HorizonCard(
                HorizonState.Available(0, 0, 0f, 0f),
                onCalibrate = { calibrations++ },
                onResetToAbsolute = { resets++ },
                onRetry = {},
                onHide = {},
            )
        }

        composeRule.onNodeWithText("Calibrate").performClick()
        composeRule.runOnIdle {
            assertEquals(1, calibrations)
            assertEquals(0, resets)
        }

        composeRule.onNodeWithText("Calibrate").performTouchInput { longClick() }
        composeRule.runOnIdle {
            assertEquals(1, calibrations)
            assertEquals(1, resets)
        }
    }

    @Test fun calibrateOffersResetToLevelAsAccessibilityAction() {
        var resets = 0
        composeRule.setContent {
            HorizonCard(HorizonState.Available(0, 0, 0f, 0f), onCalibrate = {}, onResetToAbsolute = { resets++ }, onRetry = {}, onHide = {})
        }

        val calibrate = composeRule.onNodeWithText("Calibrate").fetchSemanticsNode()
        val action = calibrate.config[SemanticsActions.CustomActions].single()
        assertEquals("Reset to level", action.label)
        assertEquals("Reset to level", calibrate.config[SemanticsActions.OnLongClick].label)

        composeRule.runOnIdle { action.action() }
        composeRule.runOnIdle { assertEquals(1, resets) }
    }

    @Test fun errorShowsAccessibleRetry() {
        composeRule.setContent { HorizonCard(HorizonState.Error, onCalibrate = {}, onResetToAbsolute = {}, onRetry = {}, onHide = {}) }
        composeRule.onNodeWithText("Unable to read horizon").assertIsDisplayed()
        // A refused registration may be transient: it is retried, never offered for hiding.
        composeRule.onNodeWithText("Hide").assertDoesNotExist()
        composeRule.onNode(hasStateDescription("Retries the attitude sensor.")).assertExists()
    }
}
