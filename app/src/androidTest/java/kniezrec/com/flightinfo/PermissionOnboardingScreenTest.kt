package kniezrec.com.flightinfo

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionOnboardingScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun stateCardCapsAt600DpOnAnExpandedWindow() {
        composeRule.setContent {
            // A density of 1 makes the host's pixel width a wide dp width for this layout test.
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                PermissionOnboardingScreen(
                    state = LocationPermissionState.Requestable,
                    onGrantPermission = {},
                    onOpenSettings = {},
                    modifier = Modifier.requiredWidth(700.dp),
                )
            }
        }

        val card = composeRule.onNodeWithTag(permissionStateCardTestTag)
        card.assertExists()
        composeRule.runOnIdle {
            assertEquals(600f, card.fetchSemanticsNode().boundsInRoot.width, 0.5f)
        }
    }
}
