package kniezrec.com.flightinfo.map.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.nearby.NearbyCoordinate
import kniezrec.com.flightinfo.route.RouteOverlay
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Map card states, controls and route overlay (formerly in GnssStatusScreenTest). */
@RunWith(AndroidJUnit4::class)
class MapCardStatesTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun unavailableMapShowsRetryActionAndDoesNotExposeMapControls() {
        composeRule.setContent {
            MapCard(
                MapUiState.Unavailable,
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
                    MapUiState.Ready(archive),
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

    // TASK-014 (F1): osmdroid skips an unreadable archive without throwing; MapCard reports it.
    @Test
    fun invalidOfflineArchiveReportsOpenFailureWithoutUsingNetworkFallback() {
        val archive = File(composeRule.activity.cacheDir, "invalid-map-test.zip")
        archive.writeText("not a zip archive")
        var failed = false
        try {
            composeRule.setContent {
                MapCard(
                    MapUiState.Ready(archive),
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

    @Test fun mapOverlayUpdatesAndClearsWithoutReplacingMapContent() {
        val archive = File(composeRule.activity.cacheDir, "route-overlay-test.zip")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("tile.jpg"))
            zip.write(byteArrayOf(0))
            zip.closeEntry()
        }
        var mapState by mutableStateOf<MapUiState>(MapUiState.Loading)
        var overlay by mutableStateOf<RouteOverlay?>(
            RouteOverlay(
                NearbyCoordinate(0.0, 0.0),
                NearbyCoordinate(1.0, 1.0),
                "Alpha",
                "Beta",
            ),
        )
        try {
            composeRule.setContent {
                MapCard(mapState, {}, routeOverlay = overlay)
            }
            composeRule.runOnIdle { mapState = MapUiState.Ready(archive) }
            composeRule.onNodeWithContentDescription("Route overlay from Alpha to Beta").assertExists()
            composeRule.onNodeWithTag("map-content").assertExists()
            composeRule.runOnIdle {
                overlay =
                    RouteOverlay(
                        NearbyCoordinate(2.0, 2.0),
                        NearbyCoordinate(3.0, 3.0),
                        "Gamma",
                        "Delta",
                    )
            }
            composeRule.onNodeWithContentDescription("Route overlay from Gamma to Delta").assertExists()
            composeRule.onNodeWithTag("map-content").assertExists()
            composeRule.runOnIdle { overlay = null }
            composeRule.onNodeWithContentDescription("Offline map showing the aircraft position").assertExists()
            composeRule.onNodeWithTag("map-content").assertExists()
        } finally {
            archive.delete()
        }
    }
}
