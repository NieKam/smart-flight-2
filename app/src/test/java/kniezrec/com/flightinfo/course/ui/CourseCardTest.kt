package kniezrec.com.flightinfo.course.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.course.CourseState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CourseCardTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun courseWaitingShowsOnlyCurrentSessionWaitingContent() {
        setCourse(CourseState.Waiting)
        composeRule.onNodeWithText("Waiting for compass heading…").assertIsDisplayed()
        composeRule.onNodeWithText("GPS bearing").assertDoesNotExist()
    }

    @Test fun courseAvailableShowsHeadingBearingAndDecorativeVisual() {
        setCourse(CourseState.Available(23, 287))
        composeRule.onNodeWithText("23°").assertIsDisplayed()
        composeRule.onNodeWithText("NE").assertIsDisplayed()
        composeRule.onNodeWithText("287°").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Compass heading, 23 degrees, northeast").assertExists()
        composeRule.onNodeWithContentDescription("GPS bearing, 287°").assertExists()
    }

    @Test fun courseStateChangesExposePoliteAnnouncementWithoutMakingHeadingLive() {
        setCourse(CourseState.Waiting)
        setCourse(CourseState.Available(23, null))
        composeRule.onNodeWithContentDescription("Compass heading available").assertExists()
    }

    @Test fun courseUsesStackedContentAtNarrowWidths() {
        composeRule.setContent {
            CourseCard(
                CourseState.Available(23, 287),
                {},
                Modifier.width(280.dp),
            )
        }
        composeRule.onNodeWithTag("course-heading").assertIsDisplayed()
        composeRule.onNodeWithTag("course-direction-visual").assertIsDisplayed()
        composeRule.onNodeWithText("GPS bearing").assertIsDisplayed()
    }

    @Test fun courseUnavailableAndErrorHideReadingsAndExposeRetryHint() {
        setCourse(CourseState.Unavailable)
        composeRule.onNodeWithText("Compass unavailable").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertDoesNotExist()
        setCourse(CourseState.Error)
        composeRule.onNodeWithText("Unable to read compass").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").assertIsDisplayed()
        composeRule.onNode(hasStateDescription("Retries compass")).assertExists()
    }

    private var shownCourse by mutableStateOf<CourseState>(CourseState.Waiting)
    private var courseContentSet = false

    // The rule allows one setContent per test, so later calls switch the state in place.
    private fun setCourse(courseState: CourseState) {
        if (courseContentSet) {
            composeRule.runOnIdle { shownCourse = courseState }
            return
        }
        shownCourse = courseState
        courseContentSet = true
        composeRule.setContent {
            // The dashboard's list padding and card modifier.
            Box(Modifier.padding(horizontal = 12.dp)) {
                CourseCard(shownCourse, {}, Modifier.padding(bottom = 12.dp).widthIn(max = 600.dp))
            }
        }
    }
}
