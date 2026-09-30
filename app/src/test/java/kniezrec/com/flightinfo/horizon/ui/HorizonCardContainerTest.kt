package kniezrec.com.flightinfo.horizon.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.testutil.FakeOrientationDataSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The container renders its [HorizonViewModel]'s attitude and wires Calibrate to it. */
@RunWith(AndroidJUnit4::class)
class HorizonCardContainerTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val orientation = FakeOrientationDataSource()

    @Test fun showsPitchRelativeToTheReferenceAndCalibrateResetsIt() {
        val viewModel = HorizonViewModel(SavedStateHandle(), orientation)
        composeRule.setContent { HorizonCardContainer(viewModel = viewModel) }

        // The first sample becomes the level reference.
        emitUntilShown(pitchDegrees = 0.0, text = "Pitch: level")
        // Samples give nose-up as negative pitch.
        emitUntilShown(pitchDegrees = -10.0, text = "Pitch: 10° up")

        composeRule.onNodeWithText("Calibrate").performClick()

        emitUntilShown(pitchDegrees = -10.0, text = "Pitch: level")
    }

    /** Re-sends the sample until the ViewModel's registration (made when the card starts collecting) shows it. */
    private fun emitUntilShown(
        pitchDegrees: Double,
        text: String,
    ) {
        composeRule.waitUntil {
            orientation.emit(pitchDegrees = pitchDegrees)
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
