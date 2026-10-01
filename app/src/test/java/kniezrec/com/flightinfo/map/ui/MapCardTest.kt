package kniezrec.com.flightinfo.map.ui

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.map.MapCoordinate
import kniezrec.com.flightinfo.map.MapRules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class MapCardTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var archive: File

    @Before fun createArchive() {
        archive = File(composeRule.activity.cacheDir, "map-card-test.zip")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("tile.jpg"))
            zip.write(byteArrayOf(0))
            zip.closeEntry()
        }
    }

    @After fun deleteArchive() {
        archive.delete()
    }

    // Icons, not text glyphs: nothing locale-dependent is drawn on the map.
    @Config(qualifiers = "pl")
    @Test
    fun buttonsShowTheOriginalIconsWithoutText() {
        composeRule.setContent { MapCard(MapUiState.Ready(archive), onRetry = {}) }

        composeRule.onNodeWithTag(mapButtonIconTag(MapButtonKind.Recenter), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(mapButtonIconTag(MapButtonKind.Expand), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription(string(R.string.map_recenter)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.map_expand)).assertIsDisplayed()
        composeRule.onAllNodesWithText("◎").assertCountEquals(0)
        composeRule.onAllNodesWithText("↕").assertCountEquals(0)
    }

    @Test fun expandSwapsTheIconAndDescriptionAndDoublesTheMapHeight() {
        composeRule.setContent {
            // An unbounded scrolling list, as the dashboard (no viewport limit given).
            Column(Modifier.width(MAP_WIDTH).verticalScroll(rememberScrollState())) {
                MapCard(MapUiState.Ready(archive), onRetry = {})
            }
        }
        val collapsed = mapAreaHeight()

        composeRule.onNodeWithContentDescription(string(R.string.map_expand)).performClick()

        composeRule.onNodeWithContentDescription(string(R.string.map_collapse)).assertExists()
        composeRule.onNodeWithContentDescription(string(R.string.map_expand)).assertDoesNotExist()
        composeRule.onNodeWithTag(mapButtonIconTag(MapButtonKind.Collapse), useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(mapButtonIconTag(MapButtonKind.Expand), useUnmergedTree = true).assertDoesNotExist()
        assertEquals(2 * collapsed, mapAreaHeight())

        composeRule.onNodeWithContentDescription(string(R.string.map_collapse)).performScrollTo().performClick()

        composeRule.onNodeWithContentDescription(string(R.string.map_expand)).assertExists()
        composeRule.onNodeWithTag(mapButtonIconTag(MapButtonKind.Expand), useUnmergedTree = true).assertExists()
        assertEquals(collapsed, mapAreaHeight())
    }

    @Test fun theExpandedMapIsBoundedByTheViewport() {
        composeRule.setContent {
            Column(Modifier.width(MAP_WIDTH).verticalScroll(rememberScrollState())) {
                MapCard(MapUiState.Ready(archive), onRetry = {}, maxMapHeight = 500.dp)
            }
        }
        val collapsed = mapAreaHeight()

        composeRule.onNodeWithContentDescription(string(R.string.map_expand)).performClick()

        val density = composeRule.activity.resources.displayMetrics.density
        assertEquals(360f * density, collapsed.toFloat(), 1f)
        assertEquals(500f * density, mapAreaHeight().toFloat(), 1f)
    }

    @Test fun expandingScrollsTheWholeMapIntoView() {
        val scroll = ScrollState(0)
        composeRule.setContent {
            Column(Modifier.width(MAP_WIDTH).height(VIEWPORT_HEIGHT).verticalScroll(scroll)) {
                Spacer(Modifier.height(30.dp))
                MapCard(MapUiState.Ready(archive), onRetry = {}, maxMapHeight = VIEWPORT_HEIGHT)
            }
        }
        composeRule.runOnIdle { assertEquals(0, scroll.value) }

        composeRule.onNodeWithContentDescription(string(R.string.map_expand)).performClick()

        composeRule.runOnIdle {
            val density = composeRule.activity.resources.displayMetrics.density
            // The map (as tall as the viewport) starts below the 30 dp spacer: scrolled to it.
            assertEquals(30f * density, scroll.value.toFloat(), 1f)
        }
    }

    @Test fun theMapReportsItsInitialZoom() {
        val zooms = mutableListOf<Double>()
        composeRule.setContent { MapCard(MapUiState.Ready(archive), onRetry = {}, onZoomChanged = { zooms += it }) }

        composeRule.runOnIdle { assertEquals(MapRules.DEFAULT_ZOOM, zooms.first(), 0.0) }
    }

    @Test fun mapHeightDoublesWhenExpandedWithinItsBounds() {
        // Phone width: 360 dp collapsed, 720 dp expanded in an unbounded list.
        assertEquals(360.dp, mapHeight(360.dp, Dp.Infinity, Dp.Infinity, expanded = false))
        assertEquals(720.dp, mapHeight(360.dp, Dp.Infinity, Dp.Infinity, expanded = true))
        // Narrow: 16/9 of the width, at least 240 dp.
        assertEquals(240.dp, mapHeight(100.dp, Dp.Infinity, Dp.Infinity, expanded = false))
        assertEquals(480.dp, mapHeight(100.dp, Dp.Infinity, Dp.Infinity, expanded = true))
        // Bounded by the viewport (the list's height)...
        assertEquals(600.dp, mapHeight(360.dp, Dp.Infinity, 600.dp, expanded = true))
        // ...but never smaller than the collapsed map.
        assertEquals(360.dp, mapHeight(360.dp, Dp.Infinity, 300.dp, expanded = true))
        // A bounded parent limits both.
        assertEquals(300.dp, mapHeight(360.dp, 300.dp, Dp.Infinity, expanded = false))
        assertEquals(300.dp, mapHeight(360.dp, 300.dp, 600.dp, expanded = true))
    }

    @Test fun readyMapRecomposesWithEveryNewPositionAndMovesOnePlaneMarker() {
        var state by mutableStateOf(MapUiState.Ready(archive))
        composeRule.setContent { MapCard(state, onRetry = {}) }
        val map = composeRule.runOnIdle { findMapView() }
        composeRule.onNode(hasStateDescription(string(R.string.map_no_position))).assertExists()
        composeRule.runOnIdle { assertEquals(0, map.overlays.count { it is Marker }) }

        composeRule.runOnIdle { state = state.copy(position = MapCoordinate(10.0, 20.0), markerHeadingDegrees = 90f) }
        composeRule.onNode(hasStateDescription(string(R.string.map_position_shown))).assertExists()
        val plane =
            composeRule.runOnIdle {
                map.overlays.filterIsInstance<Marker>().single().also { plane ->
                    assertEquals(10.0, plane.position.latitude, 0.0)
                    // Heading 90 (east) is osmdroid rotation 270 (counter-clockwise).
                    assertEquals(270f, plane.rotation)
                }
            }

        composeRule.runOnIdle { state = state.copy(position = MapCoordinate(-30.0, 40.0), markerHeadingDegrees = 180f) }
        composeRule.runOnIdle {
            assertSame(map, findMapView())
            assertSame(plane, map.overlays.filterIsInstance<Marker>().single())
            assertEquals(-30.0, plane.position.latitude, 0.0)
            assertEquals(40.0, plane.position.longitude, 0.0)
            assertEquals(180f, plane.rotation)
        }
    }

    @Test fun centerRequestCentersTheMapAndIsAcknowledged() {
        var acknowledged = 0
        var state by mutableStateOf(MapUiState.Ready(archive))
        composeRule.setContent {
            MapCard(
                state,
                onRetry = {},
                // Counts only: writing Compose state from the map's update block is avoided on purpose.
                onCentered = { acknowledged++ },
            )
        }

        composeRule.runOnIdle {
            val first = MapCoordinate(48.0, 2.0)
            state = state.copy(position = first, centerRequest = first)
        }

        composeRule.runOnIdle {
            val map = findMapView()
            assertTrue(acknowledged > 0)
            assertEquals(48.0, map.mapCenter.latitude, CENTER_TOLERANCE_DEGREES)
            assertEquals(2.0, map.mapCenter.longitude, CENTER_TOLERANCE_DEGREES)
        }
    }

    private fun mapAreaHeight(): Int =
        composeRule
            .onNodeWithTag(MAP_AREA_TAG)
            .fetchSemanticsNode()
            .size.height

    private fun findMapView(): MapView = checkNotNull(findMapView(composeRule.activity.window.decorView)) { "No MapView" }

    private fun findMapView(view: View): MapView? =
        when (view) {
            is MapView -> view
            is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findMapView(view.getChildAt(it)) }
            else -> null
        }

    private fun string(
        @StringRes id: Int,
    ): String = composeRule.activity.getString(id)

    private companion object {
        val MAP_WIDTH = 300.dp
        val VIEWPORT_HEIGHT = 400.dp

        // The map centre is snapped to whole pixels at zoom 3 (about 0.2 degrees per pixel).
        const val CENTER_TOLERANCE_DEGREES = 0.5
    }
}
