package kniezrec.com.flightinfo.ui.about
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.about.AppVersion
import kniezrec.com.flightinfo.permission.LocationPermissionState
import kniezrec.com.flightinfo.ui.gnss.DashboardHeader
import kniezrec.com.flightinfo.ui.permission.PermissionOnboardingScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutDialogTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun authorizedHeaderShowsAboutAndCompactMenuKeepsItDiscoverable() {
        var opened = false
        composeRule.setContent { DashboardHeader({}, { opened = true }, Modifier.requiredWidth(320.dp)) }
        composeRule.onNodeWithText("More options").assertIsDisplayed().performClick()
        composeRule
            .onNodeWithText("About")
            .assertIsDisplayed()
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle { assert(opened) }
    }

    @Test
    fun permissionOnboardingDoesNotExposeAbout() {
        composeRule.setContent { PermissionOnboardingScreen(LocationPermissionState.Requestable, {}, {}) }
        composeRule.onNodeWithText("About").assertDoesNotExist()
    }

    @Test
    fun aboutShowsAccessibleContentAndExplicitDismissal() {
        var visible by mutableStateOf(true)
        composeRule.setContent {
            if (visible) AboutDialog(AppVersion("1.0", 1), { false }, { false }) { visible = false }
        }
        composeRule.onNodeWithText("Smart Flight").assertIsDisplayed()
        composeRule.onNodeWithText("Version").assertIsDisplayed()
        composeRule.onNodeWithText("1.0 (1)").assertIsDisplayed()
        composeRule.onNodeWithText("Send feedback").assertIsDisplayed()
        composeRule.onNodeWithText("Rate in Google Play").assertIsDisplayed()
        composeRule.onNodeWithText("Safety information").assertIsDisplayed()
        composeRule.onNode(hasStateDescription("Sends feedback using an email app.")).assertExists()
        composeRule.onNodeWithText("OK").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Smart Flight").assertDoesNotExist()
    }

    @Test
    fun systemBackDismissesAboutBeforeActivity() {
        var visible by mutableStateOf(true)
        composeRule.setContent {
            BackHandler(enabled = visible) { visible = false }
            if (visible) AboutDialog(AppVersion("1.0", 1), { true }, { true }) { visible = false }
        }
        composeRule.onNodeWithText("Smart Flight").assertIsDisplayed()
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.onNodeWithText("Smart Flight").assertDoesNotExist()
    }
}
