package kniezrec.com.flightinfo

import android.app.Application
import android.location.LocationManager
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.monitoring.LocationForegroundService
import kniezrec.com.flightinfo.permission.ui.LocationPermissionViewModel
import kniezrec.com.flightinfo.settings.ui.SettingsViewModel
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
 * The real [SmartFlightApplication] (from the manifest) is the Hilt root under Robolectric.
 * Repositories are process singletons: shared across Activity recreation and with the service.
 */
@RunWith(AndroidJUnit4::class)
class HiltSingletonScopeTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    private val application: Application = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var previousCreateActivityContexts: String? = null

    @Before
    fun giveActivitiesTheirOwnDisplayContext() {
        // Same switch as MainActivityCharacterizationTest: activities get a display-associated context.
        previousCreateActivityContexts = System.getProperty(CREATE_ACTIVITY_CONTEXTS)
        System.setProperty(CREATE_ACTIVITY_CONTEXTS, "true")
    }

    @After
    fun tearDown() {
        scenario?.close()
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
        assertSame(first.displaySettingsRepository, second.displaySettingsRepository)
        assertSame(first.appVisibility, second.appVisibility)
    }

    // Settings and permission repositories are injected into ViewModels (TASK-015). ViewModels
    // survive recreation, so two activity launches give two separately injected instances.
    @Test
    fun separatelyCreatedViewModelsShareTheSameSingletons() {
        val firstLaunch = ActivityScenario.launch(MainActivity::class.java)
        val first = firstLaunch.viewModels()
        firstLaunch.close()
        val second = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }.viewModels()

        assertNotSame(first.settings, second.settings)
        assertNotSame(first.permission, second.permission)
        assertSame(first.settings.unitSettingsRepository, second.settings.unitSettingsRepository)
        assertSame(first.settings.displaySettingsRepository, second.settings.displaySettingsRepository)
        assertSame(
            first.settings.backgroundNotificationSettingsRepository,
            second.settings.backgroundNotificationSettingsRepository,
        )
        assertSame(first.permission.requestHistory, second.permission.requestHistory)
    }

    @Test
    fun activityAndServiceShareTheirObservedState() {
        val launched = ActivityScenario.launch(MainActivity::class.java).also { scenario = it }
        val activity = launched.injected()
        val settings = launched.viewModels().settings
        val controller = Robolectric.buildService(LocationForegroundService::class.java).create()
        try {
            val service = controller.get()

            assertSame(settings.backgroundNotificationSettingsRepository, service.backgroundNotificationSettingsRepository)
            assertSame(activity.appVisibility, service.appVisibility)
            assertSame(application.getSystemService(LocationManager::class.java), service.locationManager)
        } finally {
            controller.destroy()
        }
    }

    @Test
    fun serviceInstancesShareOneLocationRepository() {
        val first = Robolectric.buildService(LocationForegroundService::class.java).create()
        val second = Robolectric.buildService(LocationForegroundService::class.java).create()
        try {
            assertSame(first.get().locationRepository, second.get().locationRepository)
        } finally {
            first.destroy()
            second.destroy()
        }
    }

    private fun ActivityScenario<MainActivity>.injected(): Injected {
        var result: Injected? = null
        onActivity {
            result =
                Injected(
                    activity = it,
                    displaySettingsRepository = it.displaySettingsRepository,
                    appVisibility = it.appVisibility,
                )
        }
        return checkNotNull(result)
    }

    private data class Injected(
        val activity: MainActivity,
        val displaySettingsRepository: Any,
        val appVisibility: Any,
    )

    private fun ActivityScenario<MainActivity>.viewModels(): ViewModels {
        var result: ViewModels? = null
        onActivity {
            val provider = ViewModelProvider(it)
            result =
                ViewModels(
                    settings = provider[SettingsViewModel::class.java],
                    permission = provider[LocationPermissionViewModel::class.java],
                )
        }
        return checkNotNull(result)
    }

    private class ViewModels(
        val settings: SettingsViewModel,
        val permission: LocationPermissionViewModel,
    )

    private companion object {
        const val CREATE_ACTIVITY_CONTEXTS = "robolectric.createActivityContexts"
    }
}
