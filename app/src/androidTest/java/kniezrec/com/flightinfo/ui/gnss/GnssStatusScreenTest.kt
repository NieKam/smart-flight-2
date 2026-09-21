package kniezrec.com.flightinfo.ui.gnss

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.course.CourseState
import kniezrec.com.flightinfo.flight.FlightParametersState
import kniezrec.com.flightinfo.gnss.GnssSatellite
import kniezrec.com.flightinfo.gnss.GnssStatusState
import kniezrec.com.flightinfo.map.MapSessionRules
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class GnssStatusScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun availableStatusShowsUsedCountAndTextualSatelliteStates() {
        composeRule.setContent {
            GnssStatusScreen(
                state = GnssStatusState.Available(listOf(GnssSatellite(true), GnssSatellite(false))),
                flightParametersState = FlightParametersState.Waiting,
                onOpenLocationSettings = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithText("Using 1 satellite").assertIsDisplayed()
        composeRule.onNodeWithText("Satellite 1").assertIsDisplayed()
        composeRule.onNodeWithText("Used for position").assertIsDisplayed()
        composeRule.onNodeWithText("Not used for position").assertIsDisplayed()
    }

    @Test fun disabledStateShowsLocationSettingsAction() {
        composeRule.setContent {
            GnssStatusScreen(GnssStatusState.LocationServicesDisabled, FlightParametersState.Waiting, {}, {})
        }
        composeRule.onNodeWithText("Open location settings").assertIsDisplayed()
    }

    @Test fun flightParametersShowWaitingAndPartialReadings() {
        composeRule.setContent {
            GnssStatusScreen(
                state = GnssStatusState.Waiting,
                flightParametersState = FlightParametersState.Waiting,
                onOpenLocationSettings = {},
                onRetry = {},
            )
        }
        composeRule.onNodeWithText("Waiting for GPS position…").assertIsDisplayed()

        composeRule.setContent {
            GnssStatusScreen(
                state = GnssStatusState.Waiting,
                flightParametersState = FlightParametersState.Readings(36.0, null, 100.0, 1013.25),
                onOpenLocationSettings = {},
                onRetry = {},
            )
        }
        composeRule.onNodeWithText("36.0 km/h").assertIsDisplayed()
        composeRule.onNodeWithText("—").assertIsDisplayed()
        composeRule.onNodeWithText("100.0 m").assertIsDisplayed()
        composeRule.onNodeWithText("1013.3 mbar").assertIsDisplayed()
    }

    @Test fun flightParametersPressureRowHasOrderPlaceholderAndAccessibility() {
        composeRule.setContent {
            GnssStatusScreen(
                state = GnssStatusState.Waiting,
                flightParametersState = FlightParametersState.Readings(36.0, 1.2, 100.0),
                onOpenLocationSettings = {},
                onRetry = {},
            )
        }

        val labels = listOf("Speed", "Vertical speed", "Altitude", "Pressure")
        val tops = labels.map { label ->
            composeRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.top
        }
        assertTrue(tops.zipWithNext().all { (upper, lower) -> upper < lower })
        composeRule.onNodeWithContentDescription("Pressure, unavailable").assertExists()
    }

    @Test fun flightParametersPressureAccessibilityExpandsUnitName() {
        composeRule.setContent {
            GnssStatusScreen(
                state = GnssStatusState.Waiting,
                flightParametersState = FlightParametersState.Readings(36.0, 1.2, 100.0, 1013.25),
                onOpenLocationSettings = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithContentDescription("Pressure, 1013.3 millibars").assertExists()
    }

    @Test fun flightParametersAnnounceAvailabilityAfterWaiting() {
        var flightState by mutableStateOf<FlightParametersState>(FlightParametersState.Waiting)
        composeRule.setContent {
            GnssStatusScreen(GnssStatusState.Waiting, flightState, {}, {})
        }

        composeRule.runOnIdle {
            flightState = FlightParametersState.Readings(36.0, null, 100.0)
        }

        composeRule.onNodeWithContentDescription("Flight parameters available").assertExists()
    }

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
        var courseState by mutableStateOf<CourseState>(CourseState.Waiting)
        composeRule.setContent { GnssStatusScreen(GnssStatusState.Waiting, FlightParametersState.Waiting, {}, {}, courseState, {}) }
        composeRule.runOnIdle { courseState = CourseState.Available(23, null) }
        composeRule.onNodeWithContentDescription("Compass heading available").assertExists()
    }

    @Test fun courseUsesStackedContentAtNarrowWidths() {
        composeRule.setContent {
            CourseCard(
                CourseState.Available(23, 287),
                {},
                androidx.compose.ui.Modifier
                    .width(280.dp),
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

    @Test fun unavailableMapShowsRetryActionAndDoesNotExposeMapControls() {
        composeRule.setContent {
            MapCard(
                MapCardState.Unavailable,
                MapSessionRules(),
                {},
            )
        }

        composeRule.onNodeWithText("Map unavailable").assertIsDisplayed()
        composeRule
            .onNodeWithText("Try again")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithContentDescription("My location").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Expand map").assertDoesNotExist()
    }

    @Test fun readyMapExposesExpandAndCollapseControlsWithDifferentHeights() {
        val archive = File(composeRule.activity.cacheDir, "map-test.zip")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("tile.jpg"))
            zip.write(byteArrayOf(0))
            zip.closeEntry()
        }
        try {
            composeRule.setContent {
                MapCard(
                    MapCardState.Ready(archive),
                    MapSessionRules(),
                    {},
                )
            }
            val expand = composeRule.onNodeWithContentDescription("Expand map").assertIsDisplayed()
            val normalHeight =
                composeRule
                    .onNodeWithTag("map-content")
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .height
            expand.performClick()
            val collapse = composeRule.onNodeWithContentDescription("Collapse map").assertIsDisplayed()
            val expandedHeight =
                composeRule
                    .onNodeWithTag("map-content")
                    .fetchSemanticsNode()
                    .boundsInRoot
                    .height
            assertTrue(collapse.fetchSemanticsNode().boundsInRoot.height > 0f)
            assertTrue(expandedHeight > normalHeight)
        } finally {
            archive.delete()
        }
    }

    @Test fun invalidOfflineArchiveReportsOpenFailureWithoutUsingNetworkFallback() {
        val archive = File(composeRule.activity.cacheDir, "invalid-map-test.zip")
        archive.writeText("not a zip archive")
        var failed = false
        try {
            composeRule.setContent {
                MapCard(
                    MapCardState.Ready(archive),
                    MapSessionRules(),
                    {},
                    onUnavailable = { failed = true },
                )
            }
            composeRule.waitForIdle()
            assertTrue(failed)
        } finally {
            archive.delete()
        }
    }

    private fun setCourse(courseState: CourseState) {
        composeRule.setContent {
            GnssStatusScreen(GnssStatusState.Waiting, FlightParametersState.Waiting, {}, {}, courseState, {})
        }
    }
}
