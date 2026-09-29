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
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.flight.FlightParametersState
import kniezrec.com.flightinfo.flight.ui.FlightParametersViewModel
import kniezrec.com.flightinfo.monitoring.LocationForegroundService
import kniezrec.com.flightinfo.nearby.ui.NearbyCityViewModel
import kniezrec.com.flightinfo.testutil.flightFix
import kniezrec.com.flightinfo.testutil.idleMainLooper
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
        service?.destroy()
        scenario?.close()
        previousCreateActivityContexts?.let { System.setProperty(CREATE_ACTIVITY_CONTEXTS, it) }
            ?: System.clearProperty(CREATE_ACTIVITY_CONTEXTS)
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

    // Scenario 3. The service is never started here: the fix reaches the card through the
    // activity's own collection of the location repository.
    @Test
    fun forwardedFixUpdatesFlightParametersInDefaultUnits() {
        launch()
        assertFlightCardWaiting()

        forward(flightFix(speedMetresPerSecond = 10.0, altitudeMetres = 100.0)) { hasText(speedKmh("36.0")) }

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

    // Scenario 5. TASK-020 changes this on purpose (pressure shown without a GPS fix).
    @Test
    fun pressureIsHiddenBeforeFirstFixAndShownAfterIt() {
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

        composeRule.onAllNodesWithText(pressureMbar(PRESSURE_TEXT)).assertCountEquals(0)
        composeRule.onAllNodesWithText(string(R.string.flight_pressure)).assertCountEquals(0)
        assertFlightCardWaiting()

        forward(flightFix()) { hasText(speedKmh("36.0")) }

        composeRule.onNodeWithText(speedKmh("36.0")).assertIsDisplayed()
        composeRule.onNodeWithText(pressureMbar(PRESSURE_TEXT)).assertIsDisplayed()
    }

    // Scenario 6.
    @Test
    fun changingSpeedUnitInSettingsPersistsItAndRerendersFlightCard() {
        launch()
        forward(flightFix(speedMetresPerSecond = 10.0)) { hasText(speedKmh("36.0")) }
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

    // Scenario 8. Changed in TASK-009: the GNSS and flight cards live in ViewModels, collected while
    // the activity is STARTED (as in the original app's onStart/onStop scope), and observation stops
    // only 5 s after the last collector leaves. A pause therefore no longer resets the flight card:
    // a fix arriving while paused (still visible) reaches it and resuming keeps it. Only a stop
    // longer than 5 s starts over from waiting with an empty vertical-speed history.
    @Test
    fun pauseKeepsFlightCardAndOnlyALongStopResetsIt() {
        val activity = launch()
        forward(flightFix(speedMetresPerSecond = 10.0, elapsedSeconds = 1L)) { hasText(speedKmh("36.0")) }

        activity.moveToState(Lifecycle.State.STARTED)
        val shownWhilePaused =
            forwardWhilePaused(flightFix(speedMetresPerSecond = 20.0, elapsedSeconds = 2L)) {
                pausedScreenTexts(activity).contains(speedKmh("72.0"))
            }
        assertTrue("A fix while paused should reach the flight card", shownWhilePaused)

        activity.moveToState(Lifecycle.State.RESUMED)
        composeRule.onNodeWithText(speedKmh("72.0")).assertIsDisplayed()

        activity.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(LONGER_THAN_STOP_TIMEOUT_MILLIS))
        activity.moveToState(Lifecycle.State.RESUMED)
        composeRule.onAllNodesWithText(speedKmh("72.0")).assertCountEquals(0)
        assertFlightCardWaiting()

        forward(flightFix(speedMetresPerSecond = 30.0, elapsedSeconds = 3L)) { hasText(speedKmh("108.0")) }
        composeRule.onNodeWithText(speedKmh("108.0")).assertIsDisplayed()
    }

    // TASK-009/TASK-011: rotation keeps the ViewModels, so the last readings and the nearby city
    // stay visible (no reset to waiting).
    @Test
    fun recreationKeepsFlightReadingsAndNearbyCityVisible() {
        val activity = launch()
        val closestCity = string(R.string.card_row_description, string(R.string.nearby_city_closest), "Warsaw")
        forward(flightFix(speedMetresPerSecond = 10.0, latitude = WARSAW_LATITUDE, longitude = WARSAW_LONGITUDE)) {
            hasText(speedKmh("36.0")) &&
                composeRule.onAllNodesWithContentDescription(closestCity).fetchSemanticsNodes().isNotEmpty()
        }
        val before = activity.flightParametersViewModel()
        val nearbyBefore = activity.nearbyCityViewModel()

        activity.recreate()

        assertSame(before, activity.flightParametersViewModel())
        assertSame(nearbyBefore, activity.nearbyCityViewModel())
        assertTrue(before.state.value is FlightParametersState.Readings)
        composeRule.onNodeWithText(speedKmh("36.0")).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.flight_vertical_speed)).assertExists()
        composeRule.onNodeWithContentDescription(closestCity).assertExists()
        // Neither the flight card nor the nearby-city card (same text) went back to waiting.
        composeRule.onAllNodesWithText(string(R.string.flight_parameters_waiting)).assertCountEquals(0)
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
        forward(flightFix(speedMetresPerSecond = 10.0)) { hasText(speedKmh("36.0")) }
        assertEquals(1, gpsListeners().size)
    }

    // TASK-008 review B1, updated in TASK-009: switching location off resets the flight card. After a
    // trip to the location settings longer than the ViewModels' stop timeout (location switched back
    // on while nobody listens for the change), the replayed "off" value must not keep it waiting.
    @Test
    fun locationBackOnAfterLongStopLetsFixesReachFlightCard() {
        val activity = launch()
        forward(flightFix(speedMetresPerSecond = 10.0, elapsedSeconds = 1L)) { hasText(speedKmh("36.0")) }

        switchLocation(enabled = false)
        waitUntil { !hasText(speedKmh("36.0")) }
        assertFlightCardWaiting()

        activity.moveToState(Lifecycle.State.CREATED)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(LONGER_THAN_STOP_TIMEOUT_MILLIS))
        locationManager.setLocationEnabled(true)
        activity.moveToState(Lifecycle.State.RESUMED)

        forward(flightFix(speedMetresPerSecond = 30.0, elapsedSeconds = 3L)) { hasText(speedKmh("108.0")) }
        composeRule.onNodeWithText(speedKmh("108.0")).assertIsDisplayed()
    }

    // TASK-009: location switched back on while the dashboard is visible lets fixes reach the flight
    // card again without a pause/resume (before, the card stayed waiting until the next resume).
    @Test
    fun locationBackOnWhileVisibleLetsFixesReachFlightCard() {
        launch()
        forward(flightFix(speedMetresPerSecond = 10.0, elapsedSeconds = 1L)) { hasText(speedKmh("36.0")) }

        switchLocation(enabled = false)
        waitUntil { !hasText(speedKmh("36.0")) }
        switchLocation(enabled = true)

        forward(flightFix(speedMetresPerSecond = 30.0, elapsedSeconds = 3L)) { hasText(speedKmh("108.0")) }
        composeRule.onNodeWithText(speedKmh("108.0")).assertIsDisplayed()
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

    /** All texts in the activity's unmerged Compose semantics tree, readable in any lifecycle state. */
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
        node.children.forEach { collectSemanticsTexts(it, into) }
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
        const val CREATE_ACTIVITY_CONTEXTS = "robolectric.createActivityContexts"
        const val ASYNC_TIMEOUT_MILLIS = 20_000L
        const val FRAME_MILLIS = 16L
        const val POLL_MILLIS = 10L
        const val LONGER_THAN_STOP_TIMEOUT_MILLIS = FlightParametersViewModel.STOP_TIMEOUT_MILLIS + 1_000L
        const val WARSAW_ID = 31395L
        const val BERLIN_ID = 10409L
        const val WARSAW_LATITUDE = 52.22977
        const val WARSAW_LONGITUDE = 21.01178

        // Exactly representable as a Float; NumberFormat adds grouping (en-US default locale).
        const val PRESSURE_MILLIBARS = 1000.5f
        const val PRESSURE_TEXT = "1,000.5"
    }
}
