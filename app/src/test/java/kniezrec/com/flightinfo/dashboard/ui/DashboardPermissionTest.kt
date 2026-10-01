package kniezrec.com.flightinfo.dashboard.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.MainActivity
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.monitoring.LocationForegroundService
import kniezrec.com.flightinfo.testutil.idleMainLooper
import kniezrec.com.flightinfo.testutil.openFromOverflowMenu
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLocationManager
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The dashboard without location permission (TASK-024), with the real Hilt graph. */
@RunWith(AndroidJUnit4::class)
class DashboardPermissionTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val locationManager: ShadowLocationManager = shadowOf(application.getSystemService(LocationManager::class.java))
    private var scenario: ActivityScenario<MainActivity>? = null
    private var previousCreateActivityContexts: String? = null

    @Before
    fun setUp() {
        // As in DashboardScreenTest: activities get a display-associated context, and a one-entry
        // map archive keeps the launch fast.
        previousCreateActivityContexts = System.getProperty(CREATE_ACTIVITY_CONTEXTS)
        System.setProperty(CREATE_ACTIVITY_CONTEXTS, "true")
        ZipOutputStream(FileOutputStream(File(application.cacheDir, "osmdroid.zip"))).use { zip ->
            zip.putNextEntry(ZipEntry("tile.jpg"))
            zip.write(byteArrayOf(0))
            zip.closeEntry()
        }
        shadowOf(application.packageManager).setSystemFeature(PackageManager.FEATURE_LOCATION_GPS, true)
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario?.close()
        previousCreateActivityContexts?.let { System.setProperty(CREATE_ACTIVITY_CONTEXTS, it) }
            ?: System.clearProperty(CREATE_ACTIVITY_CONTEXTS)
    }

    @Test
    fun deniedShowsPermissionCardThenCourseAndHorizon() {
        composeRule.onNodeWithText(string(R.string.permission_title)).assertIsDisplayed()
        // Robolectric has no rotation-vector sensor, so both show their missing-sensor messages.
        composeRule.onNodeWithText(string(R.string.missing_sensor_course)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.missing_sensor_horizon)).assertExists()
        composeRule.onAllNodesWithText(string(R.string.gnss_status_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.nearby_city_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.route_hint)).assertCountEquals(0)
    }

    // TASK-034: the permission card first, then Course and Horizon, and no location card.
    @Test
    fun deniedCardOrderIsPermissionCourseHorizon() {
        composeRule.waitForIdle()
        val tops =
            listOf(DashboardCardTags.PERMISSION, DashboardCardTags.COURSE, DashboardCardTags.HORIZON).map { tag ->
                composeRule
                    .onNodeWithTag(tag)
                    .fetchSemanticsNode()
                    .positionInRoot.y
            }
        assertTrue("Cards out of order: $tops", tops.zipWithNext().all { (upper, lower) -> upper < lower })
        for (tag in listOf(
            DashboardCardTags.SATELLITES,
            DashboardCardTags.FLIGHT_PARAMETERS,
            DashboardCardTags.NEARBY_CITY,
            DashboardCardTags.ROUTE,
            DashboardCardTags.MAP,
        )) {
            composeRule.onAllNodesWithTag(tag).assertCountEquals(0)
        }
    }

    @Test
    fun settingsAndAboutOpenWithoutPermission() {
        composeRule.openFromOverflowMenu(string(R.string.settings_title))
        composeRule.onNodeWithText(string(R.string.units_section)).assertIsDisplayed()
        checkNotNull(scenario).onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        composeRule.openFromOverflowMenu(string(R.string.about_title))
        composeRule.onNodeWithText(string(R.string.about_disclaimer_heading)).assertIsDisplayed()
    }

    @Test
    fun deniedTouchesNoLocationAndGrantStartsIt() {
        composeRule.waitForIdle()
        idleMainLooper()
        assertEquals(0, gpsListeners().size)
        assertNull(shadowOf(application).nextStartedService)

        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        // The Activity reads the permission again on resume (as after the system dialog).
        checkNotNull(scenario).moveToState(Lifecycle.State.STARTED)
        checkNotNull(scenario).moveToState(Lifecycle.State.RESUMED)

        composeRule.waitUntil(ASYNC_TIMEOUT_MILLIS) {
            idleMainLooper()
            gpsListeners().isNotEmpty()
        }
        composeRule.onNodeWithText(string(R.string.gnss_status_title)).assertExists()
        composeRule.onAllNodesWithText(string(R.string.permission_title)).assertCountEquals(0)
        val started = shadowOf(application).nextStartedService
        assertEquals(LocationForegroundService::class.java.name, started?.component?.className)
        assertTrue(gpsListeners().isNotEmpty())
    }

    @Suppress("DEPRECATION") // getLocationUpdateListeners is the only way to inspect registrations.
    private fun gpsListeners() = locationManager.getLocationUpdateListeners(LocationManager.GPS_PROVIDER)

    private fun string(
        @StringRes id: Int,
    ): String = application.getString(id)

    private companion object {
        const val CREATE_ACTIVITY_CONTEXTS = "robolectric.createActivityContexts"
        const val ASYNC_TIMEOUT_MILLIS = 20_000L
    }
}
