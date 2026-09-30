package kniezrec.com.flightinfo.dashboard.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.MainActivity
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.map.MapRules
import kniezrec.com.flightinfo.map.ui.MAP_ZOOM_TIP_TAG
import kniezrec.com.flightinfo.map.ui.MapViewModel
import kniezrec.com.flightinfo.settings.ui.LARGER_MAP_ZOOM_ROW_TAG
import kniezrec.com.flightinfo.settings.ui.SettingHighlighted
import kniezrec.com.flightinfo.testutil.idleMainLooper
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The dashboard as the app shows it (real Hilt graph, location granted): the card containers in
 * order, and the Settings, About and city-picker overlays hosted at screen level.
 */
@RunWith(AndroidJUnit4::class)
class DashboardScreenTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    private val application: Application = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var previousCreateActivityContexts: String? = null

    @Before
    fun setUp() {
        // As in MainActivityCharacterizationTest: activities get a display-associated context, and
        // a one-entry map archive keeps the launch fast.
        previousCreateActivityContexts = System.getProperty(CREATE_ACTIVITY_CONTEXTS)
        System.setProperty(CREATE_ACTIVITY_CONTEXTS, "true")
        ZipOutputStream(FileOutputStream(File(application.cacheDir, "osmdroid.zip"))).use { zip ->
            zip.putNextEntry(ZipEntry("tile.jpg"))
            zip.write(byteArrayOf(0))
            zip.closeEntry()
        }
        shadowOf(application.packageManager).setSystemFeature(PackageManager.FEATURE_LOCATION_GPS, true)
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
        previousCreateActivityContexts?.let { System.setProperty(CREATE_ACTIVITY_CONTEXTS, it) }
            ?: System.clearProperty(CREATE_ACTIVITY_CONTEXTS)
    }

    // Robolectric has no orientation sensor, so Course and Horizon show their missing-sensor messages.
    @Test
    fun cardsAreListedInTheirOrderBelowTheHeader() {
        val expand = string(R.string.map_expand)
        waitUntil { composeRule.onAllNodesWithContentDescription(expand).fetchSemanticsNodes().isNotEmpty() }

        val tops =
            listOf(
                R.string.app_name,
                R.string.gnss_status_title,
                R.string.flight_parameters_title,
                R.string.missing_sensor_course,
                R.string.missing_sensor_horizon,
                R.string.nearby_city_title,
                R.string.route_title,
            ).map { title ->
                composeRule
                    .onNodeWithText(string(title))
                    .fetchSemanticsNode()
                    .positionInRoot.y
            }
        val mapTop =
            composeRule
                .onNodeWithContentDescription(expand)
                .fetchSemanticsNode()
                .positionInRoot.y

        assertTrue("Cards out of order: $tops, map $mapTop", (tops + mapTop).zipWithNext().all { (upper, lower) -> upper < lower })
    }

    @Test
    fun hidingTheUnavailableCourseCardRemovesItFromTheDashboard() {
        val courseMessage = string(R.string.missing_sensor_course)
        val horizonMessage = string(R.string.missing_sensor_horizon)
        waitUntil { composeRule.onAllNodesWithText(courseMessage).fetchSemanticsNodes().isNotEmpty() }

        hideButtonOf(courseMessage).performScrollTo().performClick()

        waitUntil { composeRule.onAllNodesWithText(courseMessage).fetchSemanticsNodes().isEmpty() }
        // Only the Course card is gone; the other cards stay.
        composeRule.onNodeWithText(horizonMessage).assertExists()
        composeRule.onNodeWithText(string(R.string.flight_parameters_title)).assertExists()
    }

    @Test
    fun showHiddenCardsInSettingsBringsTheCardBack() {
        val courseMessage = string(R.string.missing_sensor_course)
        waitUntil { composeRule.onAllNodesWithText(courseMessage).fetchSemanticsNodes().isNotEmpty() }
        hideButtonOf(courseMessage).performScrollTo().performClick()
        waitUntil { composeRule.onAllNodesWithText(courseMessage).fetchSemanticsNodes().isEmpty() }

        composeRule.onNodeWithText(string(R.string.settings_title)).performClick()
        composeRule.onNodeWithText(string(R.string.hidden_card_course)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.show_hidden_cards)).assertIsEnabled().performClick()
        composeRule.onNodeWithText(string(R.string.no_hidden_cards)).assertExists()
        composeRule.onNodeWithText(string(R.string.show_hidden_cards)).assertIsNotEnabled()
        pressBack()

        // The sensor is still missing, so the card offers hiding again.
        waitUntil { composeRule.onAllNodesWithText(courseMessage).fetchSemanticsNodes().isNotEmpty() }
        hideButtonOf(courseMessage).assertExists()
    }

    @Test
    fun settingsOverlayOpensFromTheHeaderAndSystemBackClosesIt() {
        composeRule.onNodeWithText(string(R.string.settings_title)).performClick()
        composeRule.onNodeWithText(string(R.string.units_section)).assertIsDisplayed()

        pressBack()

        composeRule.onAllNodesWithText(string(R.string.units_section)).assertCountEquals(0)
        composeRule.onNodeWithText(string(R.string.gnss_status_title)).assertIsDisplayed()
    }

    @Test
    fun aboutDialogOpensFromTheHeaderAndSystemBackClosesIt() {
        composeRule.onNodeWithText(string(R.string.about_title)).performClick()
        composeRule.onNodeWithText(string(R.string.about_disclaimer_heading)).assertIsDisplayed()

        pressBack()

        composeRule.onAllNodesWithText(string(R.string.about_disclaimer_heading)).assertCountEquals(0)
    }

    @Test
    fun choosingARouteEndpointOpensThePickerOverlayAndCancelClosesIt() {
        val chooseDeparture = "${string(R.string.route_departure)}: ${string(R.string.route_choose_departure)}"
        composeRule.onNodeWithText(chooseDeparture).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.route_picker_departure)).assertIsDisplayed()

        composeRule.onNodeWithText(string(R.string.route_cancel)).performClick()

        composeRule.onAllNodesWithText(string(R.string.route_picker_departure)).assertCountEquals(0)
        // The dashboard is back where it was: still scrolled to the Route card, which is visible
        // again, with the rest of the list (GNSS card above, scrolled out of view) still there.
        composeRule.onNodeWithText(chooseDeparture).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.gnss_status_title)).assertExists()
        composeRule.onNodeWithText(string(R.string.gnss_status_title)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun maxZoomTipActionOpensSettingsWithTheLargerMapZoomRowHighlighted() {
        val expand = string(R.string.map_expand)
        val tip = string(R.string.map_zoom_tip)
        waitUntil { composeRule.onAllNodesWithContentDescription(expand).fetchSemanticsNodes().isNotEmpty() }

        // The map reached the standard maximum (as its zoom listener reports it).
        checkNotNull(scenario).onActivity { activity ->
            ViewModelProvider(activity)[MapViewModel::class.java].onZoomChanged(MapRules.STANDARD_MAX_ZOOM)
        }
        waitUntil { composeRule.onAllNodesWithText(tip).fetchSemanticsNodes().isNotEmpty() }

        // Paused clock: the highlight is observed while it flashes.
        composeRule.mainClock.autoAdvance = false
        composeRule
            .onNode(hasText(string(R.string.settings_title)) and hasAnyAncestor(hasTestTag(MAP_ZOOM_TIP_TAG)))
            .performClick()
        composeRule.mainClock.advanceTimeBy(HIGHLIGHT_CHECK_MILLIS)

        composeRule.onNodeWithText(string(R.string.units_section)).assertExists()
        composeRule.onNodeWithTag(LARGER_MAP_ZOOM_ROW_TAG).assert(SemanticsMatcher.expectValue(SettingHighlighted, true))

        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LARGER_MAP_ZOOM_ROW_TAG).assert(SemanticsMatcher.expectValue(SettingHighlighted, false))
        composeRule.onAllNodesWithText(tip).assertCountEquals(0)
    }

    /** The "Hide" button of the missing-sensor placeholder showing [message]. */
    private fun hideButtonOf(message: String) = composeRule.onNode(hasText(string(R.string.hide_card)) and hasAnySibling(hasText(message)))

    private fun pressBack() {
        checkNotNull(scenario).onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
    }

    private fun waitUntil(condition: () -> Boolean) {
        composeRule.waitUntil(ASYNC_TIMEOUT_MILLIS) {
            idleMainLooper()
            condition()
        }
    }

    private fun string(
        @StringRes id: Int,
    ): String = application.getString(id)

    private companion object {
        const val CREATE_ACTIVITY_CONTEXTS = "robolectric.createActivityContexts"
        const val ASYNC_TIMEOUT_MILLIS = 20_000L
        const val HIGHLIGHT_CHECK_MILLIS = 200L
    }
}
