package kniezrec.com.flightinfo.map.ui

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.map.MapCoordinate
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

    // The glyph comes from the button kind, not from the (translated) description.
    @Config(qualifiers = "pl")
    @Test
    fun buttonsShowTheirGlyphsUnderANonEnglishLocale() {
        composeRule.setContent { MapCard(MapUiState.Ready(archive), onRetry = {}) }

        composeRule.onNodeWithContentDescription(string(R.string.map_recenter)).assert(hasText(RECENTER_GLYPH))
        composeRule
            .onNodeWithContentDescription(string(R.string.map_expand))
            .assert(hasText(EXPAND_COLLAPSE_GLYPH))
            .performClick()
        composeRule.onNodeWithContentDescription(string(R.string.map_collapse)).assert(hasText(EXPAND_COLLAPSE_GLYPH))
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
        const val RECENTER_GLYPH = "◎"
        const val EXPAND_COLLAPSE_GLYPH = "↕"

        // The map centre is snapped to whole pixels at zoom 3 (about 0.2 degrees per pixel).
        const val CENTER_TOLERANCE_DEGREES = 0.5
    }
}
