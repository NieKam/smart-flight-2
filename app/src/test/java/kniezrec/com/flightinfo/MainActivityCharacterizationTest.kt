package kniezrec.com.flightinfo

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.flight.FlightParametersState
import kniezrec.com.flightinfo.flight.ui.FlightParametersViewModel
import kniezrec.com.flightinfo.horizon.ui.HorizonViewModel
import kniezrec.com.flightinfo.monitoring.LocationForegroundService
import kniezrec.com.flightinfo.nearby.ui.NearbyCityViewModel
import kniezrec.com.flightinfo.route.ui.RouteViewModel
import kniezrec.com.flightinfo.testutil.flightFix
import kniezrec.com.flightinfo.testutil.idleMainLooper
import kniezrec.com.flightinfo.testutil.openFromOverflowMenu
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowLocationManager
import org.robolectric.shadows.ShadowSensor
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.test.hasText as hasTextMatcher

/**
 * Pins the observable orchestration of [MainActivity] (permission gate, lifecycle, fix fan-out,
 * pressure merge, settings and route persistence) before the ViewModel/DI migration.
 *
 * These tests describe CURRENT behavior. A task that changes a behavior on purpose updates its
 * scenario (not silently). Update them in the task
 * that changes the behavior, not silently.
 *
 * The activity is launched with [ActivityScenario] inside each test (not by the rule) so that
 * permissions, preferences, sensors and the map archive can be prepared before `onCreate`.
 *
 * Location data goes through the real Hilt graph (`LocationRepository` over the platform data
 * source): fixes and satellites are simulated on Robolectric's [ShadowLocationManager]. The
 * requested `LocationForegroundService` is never created unless a test creates it, so these
 * scenarios also show that the dashboard does not depend on the service for its data.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityCharacterizationTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val locationManager: ShadowLocationManager = shadowOf(application.getSystemService(LocationManager::class.java))
    private var scenario: ActivityScenario<MainActivity>? = null
    private var service: ServiceController<LocationForegroundService>? = null
    private var previousCreateActivityContexts: String? = null

    @Before
    fun giveActivitiesTheirOwnDisplayContext() {
        // By default Robolectric attaches every Activity to the Application's ContextImpl, which is
        // not a UI context, so Context.getDisplay() throws. On a device the Activity context is
        // display-associated. This Robolectric switch (read in ShadowActivity.callAttach) creates a
        // real activity ContextImpl, as Android does. (It was introduced for the former
        // activity-bound orientation source; kept so that activities run as on a device.)
        previousCreateActivityContexts = System.getProperty(CREATE_ACTIVITY_CONTEXTS)
        System.setProperty(CREATE_ACTIVITY_CONTEXTS, "true")
    }

    @Before
    fun seedOfflineMapArchive() {
        // MapArchiveRepository skips the 28 MB asset copy when a usable archive is already in the
        // cache directory. A one-entry archive keeps every launch fast; the map card itself is not
        // under test here (it has its own tests in MapCardTest and MapCardStatesTest).
        val archive = File(application.cacheDir, "osmdroid.zip")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("tile.jpg"))
            zip.write(byteArrayOf(0))
            zip.closeEntry()
        }
    }

    @After
    fun tearDown() {
        service?.destroy()
        scenario?.close()
        previousCreateActivityContexts?.let { System.setProperty(CREATE_ACTIVITY_CONTEXTS, it) }
            ?: System.clearProperty(CREATE_ACTIVITY_CONTEXTS)
    }

    // Scenario 1. Changed in TASK-024 (as the original app): without permission the dashboard shows
    // the permission card, then Course and Horizon (unavailable here: Robolectric has no
    // rotation-vector sensor); Settings and About stay; no location card and no service.
    @Test
    fun permissionNotGrantedShowsPermissionCardCourseAndHorizon() {
        launch(grantLocation = false)

        composeRule.onNodeWithText(string(R.string.permission_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.permission_grant)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.missing_sensor_course)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.missing_sensor_horizon)).assertExists()
        composeRule.onNodeWithContentDescription(string(R.string.dashboard_more_options)).assertIsDisplayed()
        composeRule.onAllNodesWithText(string(R.string.gnss_status_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.flight_parameters_title)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.route_hint)).assertCountEquals(0)
        assertNull(shadowOf(application).nextStartedService)
    }

    // Scenario 2.
    @Test
    fun permissionGrantedShowsWaitingDashboardAndRequestsForegroundService() {
        launch()

        composeRule.onNodeWithText(string(R.string.gnss_status_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.gnss_waiting)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.dashboard_more_options)).assertIsDisplayed()
        assertFlightCardWaiting()

        val started = shadowOf(application).nextStartedService
        assertNotNull(started)
        assertEquals(LocationForegroundService::class.java.name, started.component?.className)
    }

    // Scenario 3. The service is never started here: the fix reaches the card through the
    // activity's own collection of the location repository.
    @Test
    fun forwardedFixUpdatesFlightParametersInDefaultUnits() {
        launch()
        assertFlightCardWaiting()

        forward(flightFix(speedMetresPerSecond = 10.0, altitudeMetres = 100.0)) { hasTileDescription(speedKmh("36.0")) }

        composeRule.onNodeWithContentDescription(speedKmh("36.0")).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription(altitudeMetres("100.0")).performScrollTo().assertIsDisplayed()
        // First fix: no vertical speed yet, and no pressure sensor in this test.
        composeRule
            .onNodeWithContentDescription(
                string(
                    R.string.flight_row_accessibility,
                    string(R.string.flight_vertical_speed),
                    string(R.string.flight_unavailable_accessibility),
                ),
            ).assertExists()
    }

    // Scenario 3 (fan-out): the same forwarded fix also reaches the nearby-city lookup.
    @Test
    fun forwardedFixWithCoordinatesUpdatesNearbyCityCard() {
        launch()

        val closestCity = string(R.string.card_row_description, string(R.string.nearby_city_closest), "Warsaw")
        forward(flightFix(latitude = WARSAW_LATITUDE, longitude = WARSAW_LONGITUDE)) {
            composeRule.onAllNodesWithContentDescription(closestCity).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(closestCity).performScrollTo().assertIsDisplayed()
    }

    // Scenario 4.
    @Test
    fun forwardedSatellitesUpdateGnssCardUsedCount() {
        launch()

        val usedCount = application.resources.getQuantityString(R.plurals.gnss_satellites_used, 2, 2)
        val status =
            GnssStatus
                .Builder()
                .addSatellite(GnssStatus.CONSTELLATION_GPS, 1, 30f, 45f, 90f, true, true, true, false, 0f, false, 0f)
                .addSatellite(GnssStatus.CONSTELLATION_GPS, 2, 25f, 30f, 180f, true, true, true, false, 0f, false, 0f)
                .addSatellite(GnssStatus.CONSTELLATION_GPS, 3, 10f, 10f, 270f, false, false, false, false, 0f, false, 0f)
                .build()
        // Re-sent until the activity's GNSS registration (made asynchronously) receives it.
        waitUntil {
            locationManager.simulateGnssStatus(status)
            hasText(usedCount)
        }

        composeRule.onNodeWithText(usedCount).assertIsDisplayed()
        composeRule.onAllNodesWithText(string(R.string.gnss_waiting)).assertCountEquals(0)
    }

    // Scenario 5. Changed in TASK-020 (as the original app): pressure is shown before the first GPS
    // fix, with dashes in the GPS rows, and stays with the readings afterwards.
    @Test
    fun pressureIsShownBeforeFirstFixAndKeptAfterIt() {
        val sensorManager = application.getSystemService(SensorManager::class.java)
        val pressureSensor = ShadowSensor.newInstance(Sensor.TYPE_PRESSURE)
        shadowOf(sensorManager).addSensor(pressureSensor)
        launch()
        // The flight ViewModel registers the barometer once the card's state is collected.
        waitUntil { shadowOf(sensorManager).listeners.isNotEmpty() }

        composeRule.runOnIdle {
            shadowOf(sensorManager).sendSensorEventToListeners(
                SensorEventBuilder.newBuilder(pressureSensor, floatArrayOf(PRESSURE_MILLIBARS)).setTimestamp(1L).build(),
                pressureSensor,
            )
        }

        waitUntil { hasTileDescription(pressureMbar(PRESSURE_TEXT)) }
        composeRule.onNodeWithContentDescription(pressureMbar(PRESSURE_TEXT)).performScrollTo().assertIsDisplayed()
        // Readings layout (the Nearby city card still shows the same "Waiting for GPS position…" text).
        assertFalse(isFlightCardWaiting())
        composeRule.onAllNodesWithContentDescription(speedKmh("36.0")).assertCountEquals(0)

        forward(flightFix()) { hasTileDescription(speedKmh("36.0")) }

        composeRule.onNodeWithContentDescription(speedKmh("36.0")).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription(pressureMbar(PRESSURE_TEXT)).performScrollTo().assertIsDisplayed()
    }

    // Scenario 6.
    @Test
    fun changingSpeedUnitInSettingsPersistsItAndRerendersFlightCard() {
        launch()
        forward(flightFix(speedMetresPerSecond = 10.0)) { hasTileDescription(speedKmh("36.0")) }
        composeRule.onNodeWithContentDescription(speedKmh("36.0")).performScrollTo().assertIsDisplayed()

        composeRule.openFromOverflowMenu(string(R.string.settings_title))
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
            .onNodeWithContentDescription(speedMph("22.4"))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription(speedKmh("36.0")).assertCountEquals(0)
    }

    // Scenario 6 (monitoring setting). Pinned in TASK-015, when the setting moved to SettingsViewModel:
    // switching the background notification on while visible and granted requests the service again.
    @Test
    fun enablingBackgroundNotificationInSettingsPersistsItAndRequestsForegroundService() {
        // TASK-025: without POST_NOTIFICATIONS (Android 13+) the row shows "Notifications are blocked".
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        launch()
        composeRule.openFromOverflowMenu(string(R.string.settings_title))
        clickBackgroundNotificationRow(R.string.settings_on)
        composeRule.waitForIdle()
        val stored = application.getSharedPreferences("monitoring_behavior", Context.MODE_PRIVATE)
        assertEquals(false, stored.getBoolean("monitoring_show_background_notification", true))
        shadowOf(application).clearStartedServices()

        clickBackgroundNotificationRow(R.string.settings_off)
        composeRule.waitForIdle()

        assertEquals(true, stored.getBoolean("monitoring_show_background_notification", false))
        val started = shadowOf(application).nextStartedService
        assertNotNull(started)
        assertEquals(LocationForegroundService::class.java.name, started.component?.className)
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

        val departure = "Warsaw"
        val destination = "Berlin"
        waitUntil { composeRule.onAllNodesWithText(departure).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText(departure).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(destination).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.route_distance)).performScrollTo().assertIsDisplayed()
    }

    // Scenario 8. Changed in TASK-009: the GNSS and flight cards live in ViewModels, collected while
    // the activity is STARTED (as in the original app's onStart/onStop scope), and observation stops
    // only 5 s after the last collector leaves. A pause therefore no longer resets the flight card:
    // a fix arriving while paused (still visible) reaches it and resuming keeps it. Only a stop
    // longer than 5 s starts over from waiting with an empty vertical-speed history.
    @Test
    fun pauseKeepsFlightCardAndOnlyALongStopResetsIt() {
        val activity = launch()
        forward(flightFix(speedMetresPerSecond = 10.0, elapsedSeconds = 1L)) { hasTileDescription(speedKmh("36.0")) }

        activity.moveToState(Lifecycle.State.STARTED)
        val shownWhilePaused =
            forwardWhilePaused(flightFix(speedMetresPerSecond = 20.0, elapsedSeconds = 2L)) {
                pausedScreenTexts(activity).contains(speedKmh("72.0"))
            }
        assertTrue("A fix while paused should reach the flight card", shownWhilePaused)

        activity.moveToState(Lifecycle.State.RESUMED)
        composeRule.onNodeWithContentDescription(speedKmh("72.0")).performScrollTo().assertIsDisplayed()

        activity.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(LONGER_THAN_STOP_TIMEOUT_MILLIS))
        activity.moveToState(Lifecycle.State.RESUMED)
        composeRule.onAllNodesWithContentDescription(speedKmh("72.0")).assertCountEquals(0)
        assertFlightCardWaiting()

        forward(flightFix(speedMetresPerSecond = 30.0, elapsedSeconds = 3L)) { hasTileDescription(speedKmh("108.0")) }
        composeRule.onNodeWithContentDescription(speedKmh("108.0")).performScrollTo().assertIsDisplayed()
    }

    // TASK-009/TASK-011: rotation keeps the ViewModels, so the last readings and the nearby city
    // stay visible (no reset to waiting).
    @Test
    fun recreationKeepsFlightReadingsAndNearbyCityVisible() {
        val activity = launch()
        val closestCity = string(R.string.card_row_description, string(R.string.nearby_city_closest), "Warsaw")
        forward(flightFix(speedMetresPerSecond = 10.0, latitude = WARSAW_LATITUDE, longitude = WARSAW_LONGITUDE)) {
            hasTileDescription(speedKmh("36.0")) &&
                composeRule.onAllNodesWithContentDescription(closestCity).fetchSemanticsNodes().isNotEmpty()
        }
        val before = activity.flightParametersViewModel()
        val nearbyBefore = activity.nearbyCityViewModel()

        activity.recreate()

        assertSame(before, activity.flightParametersViewModel())
        assertSame(nearbyBefore, activity.nearbyCityViewModel())
        assertTrue(before.state.value is FlightParametersState.Readings)
        composeRule.onNodeWithContentDescription(speedKmh("36.0")).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.flight_vertical_speed)).assertExists()
        composeRule.onNodeWithContentDescription(closestCity).assertExists()
        // Neither the flight card nor the nearby-city card (same text) went back to waiting.
        composeRule.onAllNodesWithText(string(R.string.flight_parameters_waiting)).assertCountEquals(0)
    }

    // TASK-013: the city picker lives in RoutePickerViewModel with its endpoint, query and selection
    // in saved state, so rotation keeps it open with the typed query and the selected city.
    @Test
    fun recreationKeepsTheOpenCityPickerWithQueryAndSelection() {
        val activity = launch()
        composeRule
            .onNodeWithText(string(R.string.route_choose_departure))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(string(R.string.route_city_name)).performTextInput("Warsaw")
        composeRule.onNodeWithText(string(R.string.route_city_name)).performImeAction()
        waitUntil { composeRule.onAllNodesWithText("Warsaw (", substring = true).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onAllNodesWithText("Warsaw (", substring = true)[0].performClick()
        val selected = "Selected: Warsaw ("
        // The selected city card is read as "Selected: city (country)".
        waitUntil { composeRule.onAllNodesWithContentDescription(selected, substring = true).fetchSemanticsNodes().isNotEmpty() }
        val selectedDescription =
            composeRule
                .onAllNodesWithContentDescription(selected, substring = true)
                .fetchSemanticsNodes()
                .single()
                .config[SemanticsProperties.ContentDescription]
                .single()

        activity.recreate()

        composeRule.onNodeWithText(string(R.string.route_picker_departure)).assertIsDisplayed()
        // The typed query survives (the selected city card shows "Warsaw" too).
        composeRule.onNode(hasSetTextAction() and hasTextMatcher("Warsaw")).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(selectedDescription).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.route_confirm)).assertIsEnabled()
    }

    // TASK-016: the Settings overlay's open flag is saved, so rotation keeps Settings open; back
    // still returns to the dashboard.
    @Test
    fun recreationKeepsTheOpenSettingsOverlay() {
        val activity = launch()
        composeRule.openFromOverflowMenu(string(R.string.settings_title))
        composeRule.onNodeWithText(string(R.string.units_section)).assertIsDisplayed()

        activity.recreate()

        composeRule.onNodeWithText(string(R.string.units_section)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.navigate_up)).performClick()
        composeRule.onAllNodesWithText(string(R.string.units_section)).assertCountEquals(0)
        composeRule.onNodeWithText(string(R.string.gnss_status_title)).assertIsDisplayed()
    }

    // TASK-016: the map card's expanded flag is saved, so rotation keeps the map expanded.
    @Test
    fun recreationKeepsTheExpandedMap() {
        val activity = launch()
        val expand = string(R.string.map_expand)
        val collapse = string(R.string.map_collapse)
        waitUntil { composeRule.onAllNodesWithContentDescription(expand).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithContentDescription(expand).performScrollTo().performClick()
        composeRule.onNodeWithContentDescription(collapse).assertExists()

        activity.recreate()

        waitUntil { composeRule.onAllNodesWithContentDescription(collapse).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onAllNodesWithContentDescription(expand).assertCountEquals(0)
    }

    // TASK-016: the route card keeps its resolved route across rotation (same ViewModel, no restore
    // round trip back to empty endpoints).
    @Test
    fun recreationKeepsTheRestoredRoute() {
        application
            .getSharedPreferences("route", Context.MODE_PRIVATE)
            .edit()
            .putLong("route_departure_id", WARSAW_ID)
            .putLong("route_destination_id", BERLIN_ID)
            .commit()
        val activity = launch()
        val departure = "Warsaw"
        val destination = "Berlin"
        waitUntil { composeRule.onAllNodesWithText(departure).fetchSemanticsNodes().isNotEmpty() }
        val before = activity.routeViewModel()

        activity.recreate()

        assertSame(before, activity.routeViewModel())
        composeRule.onNodeWithText(departure).assertExists()
        composeRule.onNodeWithText(destination).assertExists()
    }

    // TASK-016: the horizon's level reference is kept across rotation (observation does not restart
    // within the ViewModel's stop timeout). Were it captured again, the first sample after the
    // rotation would read as level.
    @Test
    fun recreationKeepsTheHorizonCalibrationReference() {
        val sensorManager = application.getSystemService(SensorManager::class.java)
        val rotationSensor = ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR)
        shadowOf(sensorManager).addSensor(rotationSensor)
        val activity = launch()
        waitUntil { shadowOf(sensorManager).listeners.isNotEmpty() }
        // Device flat: this first sample becomes the level reference.
        waitUntil {
            sendRotationAboutX(sensorManager, rotationSensor, degrees = 0.0)
            hasTextContaining(horizonPitch(string(R.string.horizon_level)))
        }
        val before = activity.horizonViewModel()

        activity.recreate()

        assertSame(before, activity.horizonViewModel())
        // Nose tilted by 20 degrees: shown relative to the reference captured before the rotation.
        waitUntil {
            sendRotationAboutX(sensorManager, rotationSensor, degrees = 20.0)
            hasTextContaining(horizonPitch("20°"))
        }
    }

    // Scenario 9. Replaced in TASK-008 (formerly "the activity registers no listener of its own"):
    // the activity and the service collect the same repository, so there is exactly one platform
    // registration, and fixes still reach the dashboard.
    @Test
    fun activityAndServiceShareOneLocationRegistration() {
        shadowOf(application.packageManager).setSystemFeature(PackageManager.FEATURE_LOCATION_GPS, true)
        launch()
        assertTrue("The activity should register for fixes", pollUntil { gpsListeners().isNotEmpty() })

        val controller = Robolectric.buildService(LocationForegroundService::class.java).create().also { service = it }
        controller.startCommand(0, 1)
        idleMainLooper()

        assertFalse(shadowOf(controller.get()).isStoppedBySelf)
        assertEquals(1, gpsListeners().size)
        forward(flightFix(speedMetresPerSecond = 10.0)) { hasTileDescription(speedKmh("36.0")) }
        assertEquals(1, gpsListeners().size)
    }

    // TASK-008 review B1, updated in TASK-009: switching location off resets the flight card. After a
    // trip to the location settings longer than the ViewModels' stop timeout (location switched back
    // on while nobody listens for the change), the replayed "off" value must not keep it waiting.
    @Test
    fun locationBackOnAfterLongStopLetsFixesReachFlightCard() {
        val activity = launch()
        forward(flightFix(speedMetresPerSecond = 10.0, elapsedSeconds = 1L)) { hasTileDescription(speedKmh("36.0")) }

        switchLocation(enabled = false)
        waitUntil { !hasTileDescription(speedKmh("36.0")) }
        assertFlightCardWaiting()

        activity.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(LONGER_THAN_STOP_TIMEOUT_MILLIS))
        locationManager.setLocationEnabled(true)
        activity.moveToState(Lifecycle.State.RESUMED)

        forward(flightFix(speedMetresPerSecond = 30.0, elapsedSeconds = 3L)) { hasTileDescription(speedKmh("108.0")) }
        composeRule.onNodeWithContentDescription(speedKmh("108.0")).performScrollTo().assertIsDisplayed()
    }

    // TASK-009: location switched back on while the dashboard is visible lets fixes reach the flight
    // card again without a pause/resume (before, the card stayed waiting until the next resume).
    @Test
    fun locationBackOnWhileVisibleLetsFixesReachFlightCard() {
        launch()
        forward(flightFix(speedMetresPerSecond = 10.0, elapsedSeconds = 1L)) { hasTileDescription(speedKmh("36.0")) }

        switchLocation(enabled = false)
        waitUntil { !hasTileDescription(speedKmh("36.0")) }
        switchLocation(enabled = true)

        forward(flightFix(speedMetresPerSecond = 30.0, elapsedSeconds = 3L)) { hasTileDescription(speedKmh("108.0")) }
        composeRule.onNodeWithContentDescription(speedKmh("108.0")).performScrollTo().assertIsDisplayed()
    }

    /** Changes the location switch and sends the broadcast the system sends for it. */
    private fun switchLocation(enabled: Boolean) {
        locationManager.setLocationEnabled(enabled)
        application.sendBroadcast(Intent(LocationManager.PROVIDERS_CHANGED_ACTION))
    }

    private fun ActivityScenario<MainActivity>.flightParametersViewModel(): FlightParametersViewModel {
        var viewModel: FlightParametersViewModel? = null
        onActivity { viewModel = ViewModelProvider(it)[FlightParametersViewModel::class.java] }
        return checkNotNull(viewModel)
    }

    private fun ActivityScenario<MainActivity>.routeViewModel(): RouteViewModel {
        var viewModel: RouteViewModel? = null
        onActivity { viewModel = ViewModelProvider(it)[RouteViewModel::class.java] }
        return checkNotNull(viewModel)
    }

    private fun ActivityScenario<MainActivity>.horizonViewModel(): HorizonViewModel {
        var viewModel: HorizonViewModel? = null
        onActivity { viewModel = ViewModelProvider(it)[HorizonViewModel::class.java] }
        return checkNotNull(viewModel)
    }

    /** Sends a rotation-vector sample of the device rotated by [degrees] about its x axis (pitch). */
    private fun sendRotationAboutX(
        sensorManager: SensorManager,
        sensor: Sensor,
        degrees: Double,
    ) {
        val half = Math.toRadians(degrees) / 2
        val values = floatArrayOf(sin(half).toFloat(), 0f, 0f, cos(half).toFloat(), 0f)
        shadowOf(sensorManager).sendSensorEventToListeners(
            SensorEventBuilder.newBuilder(sensor, values).setTimestamp(System.nanoTime()).build(),
            sensor,
        )
    }

    /** Start of the horizon summary with the pitch [value] ("Pitch: level", "Pitch: 20° down"…). */
    private fun horizonPitch(value: String): String = string(R.string.horizon_summary, value, "").substringBefore(" ·")

    private fun hasTextContaining(text: String): Boolean =
        composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun ActivityScenario<MainActivity>.nearbyCityViewModel(): NearbyCityViewModel {
        var viewModel: NearbyCityViewModel? = null
        onActivity { viewModel = ViewModelProvider(it)[NearbyCityViewModel::class.java] }
        return checkNotNull(viewModel)
    }

    /**
     * Simulates [fix] while the activity is paused until [delivered] holds, producing frames by hand
     * (see [pumpFrame]). Polls in real time: repository sharing runs on a background dispatcher.
     */
    private fun forwardWhilePaused(
        fix: FlightLocationFix,
        delivered: () -> Boolean,
    ): Boolean {
        val location = fix.toLocation()
        val deadline = System.currentTimeMillis() + ASYNC_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            locationManager.simulateLocation(location)
            pumpFrame()
            if (delivered()) return true
            Thread.sleep(POLL_MILLIS)
        }
        return delivered()
    }

    private fun launch(grantLocation: Boolean = true): ActivityScenario<MainActivity> {
        // As on a phone (TASK-019: without GNSS hardware the GNSS card shows "GNSS unavailable").
        shadowOf(application.packageManager).setSystemFeature(PackageManager.FEATURE_LOCATION_GPS, true)
        if (grantLocation) {
            shadowOf(application).grantPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        }
        return ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
    }

    /**
     * Simulates [fix] on the platform until [delivered] holds. Re-sending is harmless: the shadow
     * drops a location less than the request interval after the last delivered one.
     */
    private fun forward(
        fix: FlightLocationFix,
        delivered: () -> Boolean,
    ) {
        val location = fix.toLocation()
        waitUntil {
            locationManager.simulateLocation(location)
            delivered()
        }
    }

    private fun FlightLocationFix.toLocation(): Location =
        Location(LocationManager.GPS_PROVIDER).also { location ->
            speedMetresPerSecond?.let { location.speed = it.toFloat() }
            altitudeMetres?.let { location.altitude = it }
            bearingDegrees?.let { location.bearing = it.toFloat() }
            location.latitude = latitude ?: WARSAW_LATITUDE
            location.longitude = longitude ?: WARSAW_LONGITUDE
            location.time = System.currentTimeMillis()
            location.elapsedRealtimeNanos = elapsedRealtimeNanos
        }

    @Suppress("DEPRECATION") // getLocationUpdateListeners is the only way to inspect registrations.
    private fun gpsListeners() = locationManager.getLocationUpdateListeners(LocationManager.GPS_PROVIDER)

    private fun hasText(text: String): Boolean = composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    /** Polls in real time: repository sharing runs on a background dispatcher. Works in any lifecycle state. */
    private fun pollUntil(condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + ASYNC_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            idleMainLooper()
            if (condition()) return true
            Thread.sleep(POLL_MILLIS)
        }
        return condition()
    }

    /**
     * Produces a frame without the Compose test idling machinery (which needs a RESUMED root): the
     * test clock drives the test recomposer, and the looper time drives Choreographer frames.
     */
    private fun pumpFrame() {
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_MILLIS))
    }

    /** All texts and content descriptions in the activity's unmerged Compose semantics tree, readable in any lifecycle state. */
    private fun pausedScreenTexts(activity: ActivityScenario<MainActivity>): List<String> {
        val texts = mutableListOf<String>()
        activity.onActivity { current -> collectComposeTexts(current.window.decorView, texts) }
        return texts
    }

    private fun collectComposeTexts(
        view: View,
        into: MutableList<String>,
    ) {
        when (view) {
            is ViewRootForTest -> collectSemanticsTexts(view.semanticsOwner.unmergedRootSemanticsNode, into)
            is ViewGroup -> {
                for (index in 0 until view.childCount) collectComposeTexts(view.getChildAt(index), into)
            }
        }
    }

    private fun collectSemanticsTexts(
        node: SemanticsNode,
        into: MutableList<String>,
    ) {
        if (SemanticsProperties.Text in node.config) {
            node.config[SemanticsProperties.Text].mapTo(into) { it.text }
        }
        if (SemanticsProperties.ContentDescription in node.config) {
            into += node.config[SemanticsProperties.ContentDescription]
        }
        node.children.forEach { collectSemanticsTexts(it, into) }
    }

    /** Waits for work posted from the activity's background executors back to the main looper. */
    private fun waitUntil(condition: () -> Boolean) {
        composeRule.waitUntil(ASYNC_TIMEOUT_MILLIS) {
            idleMainLooper()
            condition()
        }
    }

    /**
     * Waiting content: every flight parameter tile is unavailable (readings exist once any value
     * does), and the waiting text is shown.
     */
    private fun assertFlightCardWaiting() {
        assertTrue(isFlightCardWaiting())
        assertTrue(
            composeRule
                .onAllNodesWithText(string(R.string.flight_parameters_waiting))
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
    }

    private fun isFlightCardWaiting(): Boolean =
        listOf(R.string.flight_speed, R.string.flight_altitude, R.string.flight_vertical_speed, R.string.flight_pressure).all { label ->
            hasTileDescription(
                string(R.string.flight_row_accessibility, string(label), string(R.string.flight_unavailable_accessibility)),
            )
        }

    private fun clickBackgroundNotificationRow(
        @StringRes currentValue: Int,
    ) {
        composeRule
            .onNodeWithContentDescription(
                string(
                    R.string.display_setting_warning_description,
                    string(R.string.show_background_notification),
                    string(currentValue),
                    string(R.string.background_notification_settings_description),
                ),
            ).performScrollTo()
            .performClick()
    }

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = application.getString(id, *args)

    // A flight parameter tile is identified by its spoken "label, value unit" description (TASK-043):
    // the number, label and unit are separate texts in the tile.
    private fun speedKmh(number: String) = tile(R.string.flight_speed, number, R.string.unit_kmh_accessibility)

    private fun speedMph(number: String) = tile(R.string.flight_speed, number, R.string.unit_mph_accessibility)

    private fun altitudeMetres(number: String) = tile(R.string.flight_altitude, number, R.string.unit_m_accessibility)

    private fun pressureMbar(number: String) = tile(R.string.flight_pressure, number, R.string.unit_mbar_accessibility)

    private fun tile(
        @StringRes label: Int,
        number: String,
        @StringRes unit: Int,
    ) = string(R.string.flight_row_accessibility, string(label), string(R.string.flight_value_accessibility, number, string(unit)))

    private fun hasTileDescription(description: String): Boolean =
        composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val CREATE_ACTIVITY_CONTEXTS = "robolectric.createActivityContexts"
        const val ASYNC_TIMEOUT_MILLIS = 20_000L
        const val FRAME_MILLIS = 16L
        const val POLL_MILLIS = 10L
        const val LONGER_THAN_STOP_TIMEOUT_MILLIS = FlightParametersViewModel.STOP_TIMEOUT_MILLIS + 1_000L
        const val WARSAW_ID = 31395L
        const val BERLIN_ID = 10409L
        const val WARSAW_LATITUDE = 52.22977
        const val WARSAW_LONGITUDE = 21.01178

        // Exactly representable as a Float; no grouping separator, as the original "%.1f".
        const val PRESSURE_MILLIBARS = 1000.5f
        const val PRESSURE_TEXT = "1000.5"
    }
}
