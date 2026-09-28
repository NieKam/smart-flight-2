package kniezrec.com.flightinfo

import android.app.Application
import android.location.LocationManager
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.monitoring.BackgroundMonitoringBridge
import kniezrec.com.flightinfo.monitoring.LocationForegroundService
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric

/**
 * The real [SmartFlightApplication] (from the manifest) is the Hilt root under Robolectric. Stores
 * and repositories are process singletons: shared across Activity recreation and with the service.
 */
@RunWith(AndroidJUnit4::class)
class HiltSingletonScopeTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    private val application: Application = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var previousCreateActivityContexts: String? = null

    @Before
    fun giveActivitiesTheirOwnDisplayContext() {
        // Same switch as MainActivityCharacterizationTest: AndroidOrientationSource reads Context.display.
        previousCreateActivityContexts = System.getProperty(CREATE_ACTIVITY_CONTEXTS)
        System.setProperty(CREATE_ACTIVITY_CONTEXTS, "true")
    }

    @After
    fun tearDown() {
        scenario?.close()
        BackgroundMonitoringBridge.clear()
        BackgroundMonitoringBridge.clearEventHandlers()
        previousCreateActivityContexts?.let { System.setProperty(CREATE_ACTIVITY_CONTEXTS, it) }
            ?: System.clearProperty(CREATE_ACTIVITY_CONTEXTS)
    }

    @Test
    fun applicationIsTheHiltRoot() {
        assertTrue(application is SmartFlightApplication)
    }

    @Test
    fun activityRecreationKeepsTheSameSingletons() {
        val launched = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        val first = launched.injected()

        launched.recreate()
        val second = launched.injected()

        assertNotSame(first.activity, second.activity)
        assertSame(first.displayPreferencesStore, second.displayPreferencesStore)
        assertSame(first.unitPreferencesStore, second.unitPreferencesStore)
        assertSame(first.backgroundNotificationPreferencesStore, second.backgroundNotificationPreferencesStore)
        assertSame(first.nearbyCityRepository, second.nearbyCityRepository)
        assertSame(first.mapArchiveRepository, second.mapArchiveRepository)
        assertSame(first.clock, second.clock)
    }

    @Test
    fun activityAndServiceShareTheNotificationPreferencesStore() {
        val activity = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }.injected()
        val controller = Robolectric.buildService(LocationForegroundService::class.java).create()
        try {
            val service = controller.get()

            assertSame(activity.backgroundNotificationPreferencesStore, service.backgroundNotificationPreferencesStore)
            assertSame(application.getSystemService(LocationManager::class.java), service.locationManager)
        } finally {
            controller.destroy()
        }
    }

    private fun ActivityScenario<MainActivity>.injected(): Injected {
        var result: Injected? = null
        onActivity {
            result =
                Injected(
                    activity = it,
                    displayPreferencesStore = it.displayPreferencesStore,
                    unitPreferencesStore = it.unitPreferencesStore,
                    backgroundNotificationPreferencesStore = it.backgroundNotificationPreferencesStore,
                    nearbyCityRepository = it.nearbyCityRepository,
                    mapArchiveRepository = it.mapArchiveRepository,
                    clock = it.clock,
                )
        }
        return checkNotNull(result)
    }

    private data class Injected(
        val activity: MainActivity,
        val displayPreferencesStore: Any,
        val unitPreferencesStore: Any,
        val backgroundNotificationPreferencesStore: Any,
        val nearbyCityRepository: Any,
        val mapArchiveRepository: Any,
        val clock: Any,
    )

    private companion object {
        const val CREATE_ACTIVITY_CONTEXTS = "robolectric.createActivityContexts"
    }
}
