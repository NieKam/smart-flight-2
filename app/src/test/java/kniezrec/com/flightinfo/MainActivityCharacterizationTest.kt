package kniezrec.com.flightinfo

import android.Manifest
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.gnss.GnssSatellite
import kniezrec.com.flightinfo.monitoring.BackgroundMonitoringBridge
import kniezrec.com.flightinfo.monitoring.LocationForegroundService
import kniezrec.com.flightinfo.testutil.flightFix
import kniezrec.com.flightinfo.testutil.idleMainLooper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Pins the observable orchestration of [MainActivity] (permission gate, lifecycle, fix fan-out,
 * pressure merge, settings and route persistence) before the ViewModel/DI migration.
 *
 * These tests describe CURRENT behavior, including behavior that later tasks change on purpose
 * (TASK-020 pressure without GPS, TASK-024 dashboard without permission). Update them in the task
 * that changes the behavior, not silently.
 *
 * The activity is launched with [ActivityScenario] inside each test (not by the rule) so that
 * permissions, preferences, sensors and the map archive can be prepared before `onCreate`.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityCharacterizationTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    private val application: Application = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun seedOfflineMapArchive() {
        // MapArchiveRepository skips the 28 MB asset copy when a usable archive is already in the
        // cache directory. A one-entry archive keeps every launch fast; the map card itself is not
        // under test here (it has its own tests in GnssStatusScreenTest).
        val archive = File(application.cacheDir, "osmdroid.zip")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("tile.jpg"))
            zip.write(byteArrayOf(0))
            zip.closeEntry()
        }
    }

    @After
    fun tearDown() {
        scenario?.close()
        BackgroundMonitoringBridge.clear()
        BackgroundMonitoringBridge.clearEventHandlers()
    }

    // Scenario 1. TASK-024 changes this on purpose (dashboard without permission).
    @Test
    fun permissionNotGrantedShowsOnlyOnboarding() {
        launch(grantLocation = false)

        composeRule.onNodeWithText(string(R.string.permission_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.permission_grant)).assertIsDisplayed()
        composeRule.onAllNodesWithText(string(R.string.gnss_status_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.flight_parameters_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.course_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.route_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.settings_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.about_title)).assertCountEquals(0)
        assertNull(shadowOf(application).nextStartedService)
    }

    // Scenario 2.
    @Test
    fun permissionGrantedShowsWaitingDashboardAndRequestsForegroundService() {
        launch()

        composeRule.onNodeWithText(string(R.string.gnss_status_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.gnss_waiting)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.settings_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.about_title)).assertIsDisplayed()
        assertFlightCardWaiting()

        val started = shadowOf(application).nextStartedService
        assertNotNull(started)
        assertEquals(LocationForegroundService::class.java.name, started.component?.className)
    }

    // Scenario 3.
    @Test
    fun forwardedFixUpdatesFlightParametersInDefaultUnits() {
        launch()
        assertFlightCardWaiting()

        forward(flightFix(speedMetresPerSecond = 10.0, altitudeMetres = 100.0))

        composeRule.onNodeWithText(speedKmh("36.0")).assertIsDisplayed()
        composeRule.onNodeWithText(altitudeMetres("100.0")).assertIsDisplayed()
        // First fix: no vertical speed yet, and no pressure sensor in this test.
        composeRule
            .onNodeWithContentDescription(
                string(
                    R.string.flight_value_accessibility,
                    string(R.string.flight_vertical_speed),
                    string(R.string.flight_unavailable_accessibility),
                ),
            ).assertExists()
    }

    // Scenario 3 (fan-out): the same forwarded fix also reaches the nearby-city lookup.
    @Test
    fun forwardedFixWithCoordinatesUpdatesNearbyCityCard() {
        launch()

        forward(flightFix(latitude = WARSAW_LATITUDE, longitude = WARSAW_LONGITUDE))

        val closestCity = string(R.string.card_row_description, string(R.string.nearby_city_closest), "Warsaw")
        waitUntil { composeRule.onAllNodesWithContentDescription(closestCity).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithContentDescription(closestCity).performScrollTo().assertIsDisplayed()
    }

    // Scenario 4.
    @Test
    fun forwardedSatellitesUpdateGnssCardUsedCount() {
        launch()

        composeRule.runOnIdle {
            BackgroundMonitoringBridge.forwardGnssStatus(
                listOf(GnssSatellite(usedInFix = true), GnssSatellite(usedInFix = true), GnssSatellite(usedInFix = false)),
            )
        }

        composeRule
            .onNodeWithText(application.resources.getQuantityString(R.plurals.gnss_satellites_used, 2, 2))
            .assertIsDisplayed()
        composeRule.onAllNodesWithText(string(R.string.gnss_waiting)).assertCountEquals(0)
    }

    // Scenario 5. TASK-020 changes this on purpose (pressure shown without a GPS fix).
    @Test
    fun pressureIsHiddenBeforeFirstFixAndShownAfterIt() {
        val sensorManager = application.getSystemService(SensorManager::class.java)
        val pressureSensor = ShadowSensor.newInstance(Sensor.TYPE_PRESSURE)
        shadowOf(sensorManager).addSensor(pressureSensor)
        launch()

        composeRule.runOnIdle {
            shadowOf(sensorManager).sendSensorEventToListeners(
                SensorEventBuilder.newBuilder(pressureSensor, floatArrayOf(PRESSURE_MILLIBARS)).setTimestamp(1L).build(),
                pressureSensor,
            )
        }

        composeRule.onAllNodesWithText(pressureMbar(PRESSURE_TEXT)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.flight_pressure)).assertCountEquals(0)
        assertFlightCardWaiting()

        forward(flightFix())

        composeRule.onNodeWithText(speedKmh("36.0")).assertIsDisplayed()
        composeRule.onNodeWithText(pressureMbar(PRESSURE_TEXT)).assertIsDisplayed()
    }

    // Scenario 6.
    @Test
    fun changingSpeedUnitInSettingsPersistsItAndRerendersFlightCard() {
        launch()
        forward(flightFix(speedMetresPerSecond = 10.0))
        composeRule.onNodeWithText(speedKmh("36.0")).assertIsDisplayed()

        composeRule.onNodeWithText(string(R.string.settings_title)).performClick()
        composeRule
            .onNodeWithContentDescription(
                string(R.string.settings_row_description, string(R.string.unit_speed), string(R.string.unit_kmh)),
            ).performScrollTo()
            .performClick()
        composeRule.onNodeWithText(string(R.string.option_mph)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.navigate_up)).performClick()

        val stored =
            application
                .getSharedPreferences("display_units", Context.MODE_PRIVATE)
                .getString("display_units_speed", null)
        assertEquals("mph", stored)
        // 36 km/h * 0.621371 = 22.37 mph.
        composeRule
            .onNodeWithText(string(R.string.flight_speed_value, "22.4", string(R.string.unit_mph)))
            .assertIsDisplayed()
        composeRule.onAllNodesWithText(speedKmh("36.0")).assertCountEquals(0)
    }

    // Scenario 7. IDs are real rows of assets/databases/cities_info.db.
    @Test
    fun persistedRouteIsRestoredIntoRouteCardAfterLaunch() {
        application
            .getSharedPreferences("route", Context.MODE_PRIVATE)
            .edit()
            .putLong("route_departure_id", WARSAW_ID)
            .putLong("route_destination_id", BERLIN_ID)
            .commit()
        launch()

        val departure = "${string(R.string.route_departure)}: Warsaw"
        val destination = "${string(R.string.route_destination)}: Berlin"
        waitUntil { composeRule.onAllNodesWithText(departure).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText(departure).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(destination).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.route_distance)).performScrollTo().assertIsDisplayed()
    }

    // Scenario 8. CURRENT behavior: onPause does not stop the flight-parameters controller, so a
    // fix forwarded while paused still updates the card; onResume resets the card to waiting and
    // the next forwarded fix shows again.
    @Test
    fun pauseKeepsAcceptingForwardedFixesAndResumeResetsFlightCard() {
        val activity = launch()
        forward(flightFix(speedMetresPerSecond = 10.0, elapsedSeconds = 1L))
        composeRule.onNodeWithText(speedKmh("36.0")).assertExists()

        activity.moveToState(Lifecycle.State.STARTED)
        forward(flightFix(speedMetresPerSecond = 20.0, elapsedSeconds = 2L))
        composeRule.onNodeWithText(speedKmh("72.0")).assertExists()

        activity.moveToState(Lifecycle.State.RESUMED)
        composeRule.onAllNodesWithText(speedKmh("72.0")).assertCountEquals(0)
        assertFlightCardWaiting()

        forward(flightFix(speedMetresPerSecond = 30.0, elapsedSeconds = 3L))
        composeRule.onNodeWithText(speedKmh("108.0")).assertIsDisplayed()
    }

    // Scenario 9 (architecture F7): the foreground path depends only on service-forwarded fixes.
    @Suppress("DEPRECATION") // getLocationUpdateListeners is the only way to inspect registrations.
    @Test
    fun activityRegistersNoLocationListenerOfItsOwn() {
        val activity = launch()
        // Under Robolectric the requested LocationForegroundService is recorded, never created.

        activity.onActivity { current ->
            val activityLocationManager = current.getSystemService(LocationManager::class.java)
            val applicationLocationManager = application.getSystemService(LocationManager::class.java)
            assertTrue(shadowOf(activityLocationManager).locationUpdateListeners.isEmpty())
            assertTrue(shadowOf(applicationLocationManager).locationUpdateListeners.isEmpty())
            shadowOf(activityLocationManager).simulateLocation(
                Location(LocationManager.GPS_PROVIDER).apply {
                    latitude = WARSAW_LATITUDE
                    longitude = WARSAW_LONGITUDE
                    speed = 10f
                    altitude = 100.0
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = 1_000_000_000L
                },
            )
        }

        assertFlightCardWaiting()
        composeRule.onAllNodesWithText(speedKmh("36.0")).assertCountEquals(0)
    }

    private fun launch(grantLocation: Boolean = true): ActivityScenario<MainActivity> {
        if (grantLocation) {
            shadowOf(application).grantPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        }
        return ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
    }

    private fun forward(fix: FlightLocationFix) {
        composeRule.runOnIdle { BackgroundMonitoringBridge.forwardLocation(fix) }
    }

    /** Waits for work posted from the activity's background executors back to the main looper. */
    private fun waitUntil(condition: () -> Boolean) {
        composeRule.waitUntil(ASYNC_TIMEOUT_MILLIS) {
            idleMainLooper()
            condition()
        }
    }

    /** Waiting content has no readings rows; "Vertical speed" appears only on the readings layout. */
    private fun assertFlightCardWaiting() {
        composeRule.onAllNodesWithText(string(R.string.flight_vertical_speed)).assertCountEquals(0)
        assertTrue(
            composeRule
                .onAllNodesWithText(string(R.string.flight_parameters_waiting))
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
    }

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = application.getString(id, *args)

    private fun speedKmh(number: String) = string(R.string.flight_speed_value, number, string(R.string.unit_kmh))

    private fun altitudeMetres(number: String) = string(R.string.flight_altitude_value, number, string(R.string.unit_m))

    private fun pressureMbar(number: String) = string(R.string.flight_pressure_value, number, string(R.string.unit_mbar))

    private companion object {
        const val ASYNC_TIMEOUT_MILLIS = 20_000L
        const val WARSAW_ID = 31395L
        const val BERLIN_ID = 10409L
        const val WARSAW_LATITUDE = 52.22977
        const val WARSAW_LONGITUDE = 21.01178

        // Exactly representable as a Float; NumberFormat adds grouping (en-US default locale).
        const val PRESSURE_MILLIBARS = 1000.5f
        const val PRESSURE_TEXT = "1,000.5"
    }
}
