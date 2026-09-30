package kniezrec.com.flightinfo.dashboard.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.MainActivity
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.testutil.idleMainLooper
import org.junit.After
import org.junit.Assert.assertEquals
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

/** The original "Enable GPS" prompt of the dashboard (real Hilt graph, location permission granted). */
@RunWith(AndroidJUnit4::class)
class EnableGpsPromptTest {
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
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    @After
    fun tearDown() {
        scenario?.close()
        previousCreateActivityContexts?.let { System.setProperty(CREATE_ACTIVITY_CONTEXTS, it) }
            ?: System.clearProperty(CREATE_ACTIVITY_CONTEXTS)
    }

    @Test
    fun launchWithGpsOffShowsThePromptAndTheGnssCardState() {
        launch(gpsEnabled = false)

        composeRule.onNodeWithText(string(R.string.enable_gps_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.enable_gps_message)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.no)).performClick()

        composeRule.onAllNodesWithText(string(R.string.enable_gps_title)).assertCountEquals(0)
        waitForText(string(R.string.location_services_off_title))
        composeRule.onNodeWithText(string(R.string.location_services_off_title)).assertIsDisplayed()
    }

    @Test
    fun yesOpensTheLocationSettings() {
        val activity = launch(gpsEnabled = false)

        composeRule.onNodeWithText(string(R.string.yes)).performClick()

        composeRule.onAllNodesWithText(string(R.string.enable_gps_title)).assertCountEquals(0)
        var started: Intent? = null
        activity.onActivity { started = shadowOf(it).nextStartedActivity }
        assertEquals(Settings.ACTION_LOCATION_SOURCE_SETTINGS, started?.action)
    }

    @Test
    fun rotationKeepsAnOpenPromptAndDoesNotShowADismissedOneAgain() {
        val activity = launch(gpsEnabled = false)
        composeRule.onNodeWithText(string(R.string.enable_gps_title)).assertIsDisplayed()

        activity.recreate()
        composeRule.onAllNodesWithText(string(R.string.enable_gps_title)).assertCountEquals(1)

        composeRule.onNodeWithText(string(R.string.no)).performClick()
        activity.recreate()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText(string(R.string.enable_gps_title)).assertCountEquals(0)
    }

    @Test
    fun aNewStartWithGpsStillOffShowsThePromptAgain() {
        val activity = launch(gpsEnabled = false)
        composeRule.onNodeWithText(string(R.string.no)).performClick()

        activity.moveToState(Lifecycle.State.CREATED)
        activity.moveToState(Lifecycle.State.RESUMED)

        composeRule.onNodeWithText(string(R.string.enable_gps_title)).assertIsDisplayed()
    }

    @Test
    fun gpsSwitchedOffWhileVisibleShowsOnlyTheGnssCardState() {
        launch(gpsEnabled = true)
        composeRule.onAllNodesWithText(string(R.string.enable_gps_title)).assertCountEquals(0)

        locationManager.setLocationEnabled(false)
        application.sendBroadcast(Intent(LocationManager.PROVIDERS_CHANGED_ACTION))
        waitForText(string(R.string.location_services_off_title))

        composeRule.onAllNodesWithText(string(R.string.enable_gps_title)).assertCountEquals(0)
    }

    private fun launch(gpsEnabled: Boolean): ActivityScenario<MainActivity> {
        locationManager.setLocationEnabled(gpsEnabled)
        return ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
    }

    /** Polls in real time: repository sharing runs on a background dispatcher. */
    private fun waitForText(text: String) {
        composeRule.waitUntil(ASYNC_TIMEOUT_MILLIS) {
            idleMainLooper()
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(
        @StringRes id: Int,
    ): String = application.getString(id)

    private companion object {
        const val CREATE_ACTIVITY_CONTEXTS = "robolectric.createActivityContexts"
        const val ASYNC_TIMEOUT_MILLIS = 20_000L
    }
}
