package kniezrec.com.flightinfo.course.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.testutil.FakeLocationDataSource
import kniezrec.com.flightinfo.testutil.FakeOrientationDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The container renders its [CourseViewModel]'s state and wires the retry action to it. */
@RunWith(AndroidJUnit4::class)
class CourseCardContainerTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val orientation = FakeOrientationDataSource()

    @After fun tearDown() = repositoryScope.cancel()

    @Test fun retryAfterARefusedSensorRestartsTheHeading() {
        orientation.failRegistration = true
        val viewModel = CourseViewModel(orientation, LocationRepository(FakeLocationDataSource(), repositoryScope))
        composeRule.setContent { CourseCardContainer(viewModel = viewModel) }
        composeRule.onNodeWithText("Unable to read compass").assertIsDisplayed()

        orientation.failRegistration = false
        composeRule.onNodeWithText("Try again").performClick()
        composeRule.waitUntil {
            orientation.emit(headingDegrees = 23.0)
            composeRule.onAllNodesWithText("23°").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("23°").assertIsDisplayed()
    }
}
