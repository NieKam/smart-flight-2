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
import androidx.compose.ui.test.assertIsEnabled
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
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.route.RouteEndpoint
import kniezrec.com.flightinfo.route.RouteOverlay
import kniezrec.com.flightinfo.route.RouteState
import kniezrec.com.flightinfo.ui.route.RouteCard
import kniezrec.com.flightinfo.ui.route.RoutePicker
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
                flightParametersState = FlightParametersState.Readings(36.0, null, 100.0),
                onOpenLocationSettings = {},
                onRetry = {},
            )
        }
        composeRule.onNodeWithText("36.0 km/h").assertIsDisplayed()
        composeRule.onNodeWithText("—").assertIsDisplayed()
        composeRule.onNodeWithText("100.0 m").assertIsDisplayed()
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

    @Test fun routePickerSupportsDraftSelectionAndExplicitCancel() {
        val city = NearbyCityRecord(7L, "Berlin", "Germany", 52.5, 13.4, "Europe/Berlin")
        var confirmed = false
        var cancelled = false
        composeRule.setContent {
            RoutePicker(
                endpoint = RouteEndpoint.DEPARTURE,
                initial = null,
                results = listOf(city),
                loading = false,
                error = null,
                mapArchive = null,
                onSearch = {},
                onNearest = {},
                onConfirm = { confirmed = true },
                onCancel = { cancelled = true },
                onRetry = {},
            )
        }
        composeRule.onNodeWithText("Berlin (Germany)").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Confirm").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertTrue(confirmed) }
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle { assertTrue(cancelled) }
    }

    @Test fun routePickerNearestCityIsImmediatelyConfirmableDraft() {
        val city = NearbyCityRecord(8L, "Paris", "France", 48.8, 2.3, "Europe/Paris")
        var confirmed = false
        composeRule.setContent {
            RoutePicker(endpoint = RouteEndpoint.DESTINATION, initial = null, results = emptyList(), loading = false, error = null, mapArchive = null, onSearch = {}, onNearest = {}, nearestDraft = city, onConfirm = { confirmed = true }, onCancel = {}, onRetry = {})
        }
        composeRule.onNodeWithText("Selected: Paris (France)").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertTrue(confirmed) }
    }

    @Test fun routePickerKeepsMultipleResultsUnselectedUntilExplicitChoiceAndSupportsRetry() {
        val first = NearbyCityRecord(9L, "Springfield", "US", 39.8, -89.6, "UTC")
        val second = first.copy(id = 10L, country = "CA")
        var selected: NearbyCityRecord? = null
        var retried = false
        composeRule.setContent {
            RoutePicker(
                endpoint = RouteEndpoint.DEPARTURE,
                initial = null,
                results = listOf(first, second),
                loading = false,
                error = "database unavailable",
                mapArchive = null,
                onSearch = {},
                onNearest = {},
                onConfirm = { selected = it },
                onCancel = {},
                onRetry = { retried = true },
            )
        }
        composeRule.onNodeWithText("Selected: Springfield (US)").assertDoesNotExist()
        composeRule.onNodeWithText("Springfield (US)").performClick()
        composeRule.onNodeWithText("Selected: Springfield (US)").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.runOnIdle { assertTrue(retried) }
        composeRule.onNodeWithText("Confirm").performClick()
        composeRule.runOnIdle { assertTrue(selected == first) }
    }

    @Test fun routeCardExposesEndpointAndDetailSemanticsWithLargeTextContent() {
        val departure = NearbyCityRecord(11L, "A very long departure city name", "US", 0.0, 0.0, "UTC")
        val destination = NearbyCityRecord(12L, "A very long destination city name", "DE", 0.0, 1.0, "Europe/Berlin")
        var cleared = false
        composeRule.setContent {
            RouteCard(
                state = RouteState(
                    departure = departure,
                    destination = destination,
                    details = kniezrec.com.flightinfo.route.RouteDetails(111.2, null, null, null),
                ),
                onChoose = {},
                onClear = {},
                onClearAll = { cleared = true },
            )
        }
        composeRule.onNodeWithContentDescription("Departure, A very long departure city name").assertHasClickAction()
        val departureAction = composeRule.onNodeWithContentDescription("Departure, A very long departure city name").assertHasClickAction()
        assertTrue(departureAction.fetchSemanticsNode().boundsInRoot.height >= 48f * composeRule.density.density)
        composeRule.onNodeWithContentDescription("Destination, A very long destination city name").assertHasClickAction()
        composeRule.onNodeWithText("Distance between cities").assertIsDisplayed()
        composeRule.onNodeWithText("Waiting for current position").assertIsDisplayed()
        composeRule.onNodeWithText("Clear route").performClick()
        composeRule.runOnIdle { assertTrue(cleared) }
    }

    @Test fun mapOverlayUpdatesAndClearsWithoutReplacingMapContent() {
        val archive = File(composeRule.activity.cacheDir, "route-overlay-test.zip")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("tile.jpg"))
            zip.write(byteArrayOf(0))
            zip.closeEntry()
        }
        var overlay by mutableStateOf<RouteOverlay?>(
            RouteOverlay(
                kniezrec.com.flightinfo.nearby.NearbyCoordinate(0.0, 0.0),
                kniezrec.com.flightinfo.nearby.NearbyCoordinate(1.0, 1.0),
                "Alpha",
                "Beta",
            ),
        )
        try {
            composeRule.setContent {
                MapCard(MapCardState.Ready(archive), MapSessionRules(), {}, routeOverlay = overlay)
            }
            composeRule.onNodeWithContentDescription("Route overlay from Alpha to Beta").assertExists()
            composeRule.onNodeWithTag("map-content").assertExists()
            composeRule.runOnIdle { overlay = null }
            composeRule.onNodeWithContentDescription("Offline map showing the aircraft position").assertExists()
            composeRule.onNodeWithTag("map-content").assertExists()
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
