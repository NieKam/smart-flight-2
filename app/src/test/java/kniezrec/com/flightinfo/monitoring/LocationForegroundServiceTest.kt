package kniezrec.com.flightinfo.monitoring

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.testutil.FakeBackgroundNotificationSettingsRepository
import kniezrec.com.flightinfo.testutil.FakeLocationDataSource
import kniezrec.com.flightinfo.testutil.idleMainLooper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * Service lifetime rules against a fake location source. [visibility] plays the activity
 * (`onResume` sets it, `onPause` clears it); the service reads it and the notification setting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocationForegroundServiceTest {
    private val source = FakeLocationDataSource()
    private val fixRegistrations = source.fixRegistrations
    private val satelliteRegistrations = source.satelliteRegistrations

    // The main looper runs the repository's sharing, as the service's collection; idleMainLooper() drives both.
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repository = LocationRepository(source, repositoryScope)
    private val visibility = AppVisibility().apply { setVisible(true) }
    private val settings = FakeBackgroundNotificationSettingsRepository()
    private lateinit var service: TestLocationForegroundService
    private lateinit var controller: ServiceController<TestLocationForegroundService>

    @After
    fun tearDown() {
        if (::controller.isInitialized) controller.destroy()
        idleMainLooper()
        repositoryScope.cancel()
    }

    @Test
    fun `visible start shows the normal copy`() {
        startMonitoring()

        assertEquals(1, fixRegistrations.activeCount)
        assertEquals(1, satelliteRegistrations.activeCount)
        assertEquals(1, notifications().size)
        assertFalse(notificationTitle().contains("waiting", true))
    }

    @Test
    fun `repeated starts keep one registration and stable waiting notification`() {
        service = createService()

        service.onStartCommand(null, 0, 1)
        service.onStartCommand(null, 0, 2)
        idleMainLooper()
        hide()

        assertEquals(1, fixRegistrations.registerCount)
        assertEquals(1, satelliteRegistrations.registerCount)
        assertEquals(1, fixRegistrations.activeCount)
        assertEquals(1, satelliteRegistrations.activeCount)
        assertEquals(1, notifications().size)
        assertNotNull(notificationAtStableId())
        assertTrue(notificationTitle().contains("waiting", true))
    }

    @Test
    fun `foreground return replaces waiting copy without duplicating notification`() {
        startMonitoring()
        hide()
        show()

        assertEquals(1, fixRegistrations.registerCount)
        assertEquals(1, notifications().size)
        assertFalse(notificationTitle().contains("waiting", true))
    }

    @Test
    fun `first fix in the background cancels notification and releases the registrations`() {
        startMonitoring()
        hide()

        source.emitFix(FIX)
        idleMainLooper()

        assertReleased()
        assertTrue(notifications().isEmpty())
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `hiding after a fix stops the service`() {
        startMonitoring()
        source.emitFix(FIX)
        idleMainLooper()
        assertEquals(1, fixRegistrations.activeCount)
        assertFalse(notificationTitle().contains("waiting", true))

        hide()

        assertReleased()
        assertTrue(notifications().isEmpty())
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `pause while a fix is in flight stops the service when the fix arrives`() {
        startMonitoring()
        // The activity pauses before the fix is delivered: the service sees "hidden", then the fix.
        visibility.setVisible(false)
        source.emitFix(FIX)
        idleMainLooper()

        assertReleased()
        assertTrue(notifications().isEmpty())
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `a new run of the same service starts without a usable fix`() {
        startMonitoring()
        source.emitFix(FIX)
        idleMainLooper()
        hide()
        assertEquals(0, fixRegistrations.activeCount)

        show()
        service.onStartCommand(null, 0, 2)
        idleMainLooper()
        hide()

        assertEquals(2, fixRegistrations.registerCount)
        assertEquals(1, fixRegistrations.activeCount)
        assertTrue(notificationTitle().contains("waiting", true))
    }

    // Changed in TASK-025: GPS never keeps running in the background without a visible notification.
    @Test
    fun `hidden without notification access stops the service`() {
        startMonitoring(canPostNotifications = false)
        assertEquals(1, fixRegistrations.activeCount)

        hide()

        assertReleased()
        assertTrue(notifications().isEmpty())
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `waiting notification uses the plane small icon`() {
        startMonitoring()
        hide()

        assertEquals(R.drawable.ic_stat_plane, notificationAtStableId()!!.smallIcon.resId)
    }

    @Config(sdk = [33])
    @Test
    fun `android 13 with POST_NOTIFICATIONS denied stops when hidden`() {
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        startMonitoring(canPostNotifications = null)

        hide()

        assertReleased()
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Config(sdk = [33])
    @Test
    fun `android 13 with POST_NOTIFICATIONS granted shows the waiting notification`() {
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        startMonitoring(canPostNotifications = null)

        hide()

        assertEquals(1, fixRegistrations.activeCount)
        assertTrue(notificationTitle().contains("waiting", true))
        assertEquals(R.drawable.ic_stat_plane, notificationAtStableId()!!.smallIcon.resId)
    }

    @Test
    fun `waiting notification tap targets the existing main activity`() {
        startMonitoring()
        hide()

        val intent = shadowOf(notificationAtStableId()!!.contentIntent).savedIntent

        assertEquals(
            kniezrec.com.flightinfo.MainActivity::class.java.name,
            intent.component!!.className,
        )
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
    }

    @Test
    fun `notification dismissal stops only the current run`() {
        startMonitoring()
        service.onStartCommand(
            Intent(service, LocationForegroundService::class.java).setAction(LocationForegroundService.ACTION_STOP),
            0,
            2,
        )
        idleMainLooper()

        assertReleased()
        assertTrue(notifications().isEmpty())
        assertTrue(settings.settings.value.showBackgroundNotification)
    }

    @Test
    fun `preference off while visible keeps the registrations and leaves preference unchanged`() {
        startMonitoring()
        setShowBackgroundNotification(false)

        assertEquals(1, fixRegistrations.activeCount)
        assertEquals(1, satelliteRegistrations.activeCount)
        assertEquals(0, fixRegistrations.unregisterCount)
        assertEquals(1, notifications().size)
        assertFalse(settings.settings.value.showBackgroundNotification)
    }

    @Test
    fun `preference off read from the repository releases the registrations when the activity hides`() {
        setShowBackgroundNotification(false)
        startMonitoring()
        hide()

        assertReleased()
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun `preference turned off while hidden releases the registrations`() {
        startMonitoring()
        hide()
        setShowBackgroundNotification(false)

        assertReleased()
        assertTrue(notifications().isEmpty())
        assertFalse(settings.settings.value.showBackgroundNotification)
    }

    @Test
    fun `eligibility loss seen on a visibility change releases callbacks and notification`() {
        startMonitoring()
        service.eligible = false
        hide()

        assertReleased()
        assertTrue(notifications().isEmpty())
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `running service eligibility check cleans up after provider loss`() {
        startMonitoring()
        service.eligible = false
        service.checkEligibilityForTest()
        idleMainLooper()

        assertReleased()
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun `location registration failure releases gnss and cleans up foreground notification`() {
        source.failFixRegistration = true
        startMonitoring()

        assertEquals(1, fixRegistrations.registerCount)
        assertEquals(0, satelliteRegistrations.activeCount)
        assertTrue(notifications().isEmpty())
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `gnss registration failure releases location and cleans up foreground notification`() {
        source.failSatelliteRegistration = true
        startMonitoring()

        assertEquals(1, satelliteRegistrations.registerCount)
        assertEquals(0, fixRegistrations.activeCount)
        assertTrue(notifications().isEmpty())
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `disabled location services stop the service without registering`() {
        source.locationEnabled = false
        startMonitoring()

        assertEquals(0, fixRegistrations.registerCount)
        assertEquals(0, satelliteRegistrations.registerCount)
        assertTrue(notifications().isEmpty())
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `missing gnss hardware stops the service without registering`() {
        source.gnssHardware = false
        startMonitoring()

        assertEquals(0, fixRegistrations.registerCount)
        assertEquals(0, satelliteRegistrations.registerCount)
        assertTrue(notifications().isEmpty())
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `foreground startup failure cleans up without registering`() {
        service = createService()
        service.failForegroundStart = true
        service.onStartCommand(null, 0, 1)
        idleMainLooper()

        assertEquals(0, fixRegistrations.registerCount)
        assertEquals(0, satelliteRegistrations.registerCount)
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun `service destruction releases registrations and notification`() {
        startMonitoring()
        controller.destroy()
        idleMainLooper()

        assertReleased()
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun `destroyed service ignores later visibility and setting changes`() {
        startMonitoring()
        controller.destroy()
        idleMainLooper()

        hide()
        setShowBackgroundNotification(false)
        show()

        assertReleased()
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun `notification tap reconciles the existing service-owned session`() {
        startMonitoring()
        hide()
        val intent = shadowOf(notificationAtStableId()!!.contentIntent).savedIntent

        assertEquals(kniezrec.com.flightinfo.MainActivity::class.java.name, intent.component!!.className)
        show()

        assertEquals(1, fixRegistrations.registerCount)
        assertEquals(1, fixRegistrations.activeCount)
        assertFalse(notificationTitle().contains("waiting", true))
    }

    @Test
    fun `activity recreation and repeated handoff retain one registration`() {
        startMonitoring()
        hide()
        service.onStartCommand(null, 0, 2)
        show()
        hide()
        show()

        assertEquals(1, fixRegistrations.registerCount)
        assertEquals(1, satelliteRegistrations.registerCount)
        assertEquals(1, fixRegistrations.activeCount)
    }

    @Test
    fun `service and another collector share one registration per data type`() {
        val dashboardFixes = mutableListOf<FlightLocationFix>()
        val dashboard = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        dashboard.launch { repository.fixes.collect { dashboardFixes += it } }
        dashboard.launch { repository.satellites.collect { } }
        startMonitoring()

        source.emitFix(FIX)
        idleMainLooper()

        assertEquals(1, fixRegistrations.registerCount)
        assertEquals(1, satelliteRegistrations.registerCount)
        assertEquals(listOf(FIX), dashboardFixes)
        dashboard.cancel()
    }

    /** [canPostNotifications] null: the real check (permission, app and channel switches). */
    private fun createService(canPostNotifications: Boolean? = true): TestLocationForegroundService {
        controller = Robolectric.buildService(TestLocationForegroundService::class.java).create()
        service = controller.get()
        service.canPost = canPostNotifications
        service.locationRepository = repository
        service.appVisibility = visibility
        service.backgroundNotificationSettingsRepository = settings
        return service
    }

    /** Creates and starts the service as MainActivity does (while visible), then runs its collection. */
    private fun startMonitoring(canPostNotifications: Boolean? = true) {
        service = createService(canPostNotifications)
        service.onStartCommand(null, 0, 1)
        idleMainLooper()
    }

    private fun hide() {
        visibility.setVisible(false)
        idleMainLooper()
    }

    private fun show() {
        visibility.setVisible(true)
        idleMainLooper()
    }

    private fun setShowBackgroundNotification(enabled: Boolean) {
        runBlocking { settings.setShowBackgroundNotification(enabled) }
        idleMainLooper()
    }

    /** Both platform registrations were made once and have been released. */
    private fun assertReleased() {
        assertEquals(1, fixRegistrations.unregisterCount)
        assertEquals(1, satelliteRegistrations.unregisterCount)
        assertEquals(0, fixRegistrations.activeCount)
        assertEquals(0, satelliteRegistrations.activeCount)
    }

    private fun notificationAtStableId(): Notification? =
        shadowOf(
            ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getSystemService(NotificationManager::class.java),
        ).getNotification(LocationForegroundService.NOTIFICATION_ID)

    private fun notificationTitle(): String =
        notifications()
            .single()
            .extras
            .getCharSequence(Notification.EXTRA_TITLE)
            .toString()

    private fun notifications(): List<Notification> =
        shadowOf(
            ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getSystemService(NotificationManager::class.java),
        ).allNotifications
}

internal class TestLocationForegroundService : LocationForegroundService() {
    var eligible = true
    var canPost: Boolean? = true
    var failForegroundStart = false

    override fun isMonitoringEligible(): Boolean = eligible

    override fun canPostNotifications(): Boolean = canPost ?: super.canPostNotifications()

    override fun startForegroundServiceNotification(): Boolean = !failForegroundStart
}

private val FIX = FlightLocationFix(1.0, 100.0, 1L)
