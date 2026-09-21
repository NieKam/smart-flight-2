package kniezrec.com.flightinfo.ui.gnss

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.nearby.NearbyCityState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NearbyCityCardTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun unavailableRetryKeepsVisibleActionLabelAndHint() {
        composeRule.setContent { NearbyCityCard(NearbyCityState.Unavailable, {}) }
        composeRule.onNodeWithText("Try again").assertIsDisplayed().assertHasClickAction()
        composeRule.onNode(hasStateDescription("Reloads nearby city data and tries the latest GPS position.")).assertExists()
    }

    @Test fun availableRowsMergeLocalizedDistanceAndTimeOffsetSemantics() {
        composeRule.setContent { NearbyCityCard(NearbyCityState.Available("Gdańsk", "Poland", 12.34, "10:42", 7_200), {}) }
        composeRule.onNodeWithText("12.3 km").assertIsDisplayed()
        composeRule.onNode(hasContentDescription("Distance to the city, 12.3 kilometres")).assertExists()
        composeRule.onNode(hasContentDescription("Time, 10:42, UTC plus 2 hours 0 minutes")).assertExists()
    }
}
