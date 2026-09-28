package kniezrec.com.flightinfo.monitoring

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.gnss.GnssSatellite
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.testutil.FakeBackgroundNotificationSettingsRepository
import kniezrec.com.flightinfo.testutil.FakeLocationDataSource
import kniezrec.com.flightinfo.testutil.idleMainLooper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocationForegroundServiceTest {
    private val source = FakeLocationDataSource()
    private val fixRegistrations = source.fixRegistrations
    private val satelliteRegistrations = source.satelliteRegistrations

    // The main looper runs the repository's sharing, as the service's collection; idleMainLooper() drives both.
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val repository = LocationRepository(source, repositoryScope)
    private lateinit var service: TestLocationForegroundService
    private lateinit var controller: ServiceController<TestLocationForegroundService>

    @After
    fun tearDown() {
        if (::controller.isInitialized) controller.destroy()
        idleMainLooper()
        repositoryScope.cancel()
        BackgroundMonitoringBridge.clear()
        BackgroundMonitoringBridge.clearEventHandlers()
    }

    @Test
    fun `repeated starts keep one registration and stable waiting notification`() {
        service = startService()

        service.onStartCommand(null, 0, 1)
        service.onStartCommand(null, 0, 2)
        idleMainLooper()
        BackgroundMonitoringBridge.setActivityVisible(false)

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
        service = startService()
        service.onStartCommand(null, 0, 1)
        idleMainLooper()
        BackgroundMonitoringBridge.setActivityVisible(false)
        BackgroundMonitoringBridge.setActivityVisible(true)

        assertEquals(1, fixRegistrations.registerCount)
        assertEquals(1, notifications().size)
        assertFalse(notificationTitle().contains("waiting", true))
    }

    @Test
    fun `service forwards one gnss status from the repository through the bridge`() {
        val statuses = mutableListOf<List<GnssSatellite>>()
        BackgroundMonitoringBridge.setEventHandlers({}, { statuses += it })
        service = startService()
        service.onStartCommand(null, 0, 1)
        idleMainLooper()

        source.emitSatellites(listOf(GnssSatellite(true, 20f)))
        idleMainLooper()

        assertEquals(listOf(listOf(GnssSatellite(true, 20f))), statuses)
    }

    @Test
    fun `service forwards fixes from the repository through the bridge`() {
        val fixes = mutableListOf<FlightLocationFix>()
        BackgroundMonitoringBridge.setEventHandlers({ fixes += it }, {})
        service = startService()
        service.onStartCommand(null, 0, 1)
        idleMainLooper()

        source.emitFix(FIX)
        idleMainLooper()

        assertEquals(listOf(FIX), fixes)
    }

    @Test
    fun `usable fix cancels notification and releases the registrations`() {
        BackgroundMonitoringBridge.setEventHandlers(
            onLocation = BackgroundMonitoringBridge::onUsableLocationFix,
            onGnssStatus = {},
        )
        service = startService()
        service.onStartCommand(null, 0, 1)
        idleMainLooper()
        BackgroundMonitoringBridge.setActivityVisible(false)
        source.emitFix(FIX)
        idleMainLooper()

        assertReleased()
        assertTrue(notifications().isEmpty())
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `notification permission denial keeps degraded service without waiting notification`() {
        service = startService(canPostNotifications = false)
        service.onStartCommand(null, 0, 1)
        idleMainLooper()
        BackgroundMonitoringBridge.setActivityVisible(false)

        assertEquals(1, fixRegistrations.activeCount)
        assertEquals(1, notifications().size)
        assertFalse(notificationTitle().contains("waiting", true))
    }

    @Test
    fun `waiting notification tap targets the existing main activity`() {
        service = startService()
        service.onStartCommand(null, 0, 1)
        BackgroundMonitoringBridge.setActivityVisible(false)

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
        service = startService()
        service.onStartCommand(null, 0, 1)
        idleMainLooper()
        service.onStartCommand(
            Intent(service, LocationForegroundService::class.java).setAction(LocationForegroundService.ACTION_STOP),
            0,
            2,
        )
        idleMainLooper()

        assertReleased()
        assertTrue(notifications().isEmpty())
        assertTrue(service.backgroundNotificationSettingsRepository.settings.value.showBackgroundNotification)
    }

    @Test
    fun `preference off while visible keeps the registrations and leaves preference unchanged`() {
        service = startService()
        val settings = FakeBackgroundNotificationSettingsRepository(showBackgroundNotification = false)
        service.backgroundNotificationSettingsRepository = settings
        service.onStartCommand(null, 0, 1)
        idleMainLooper()
        BackgroundMonitoringBridge.setActivityVisible(true)
        BackgroundMonitoringBridge.setNotificationEnabled(false)
        idleMainLooper()

        assertEquals(1, fixRegistrations.activeCount)
        assertEquals(1, satelliteRegistrations.activeCount)
        assertEquals(0, fixRegistrations.unregisterCount)
        assertEquals(1, notifications().size)
        assertFalse(settings.settings.value.showBackgroundNotification)
    }

    @Test
    fun `preference off read from the repository releases the registrations when the activity hides`() {
        service = startService()
        service.backgroundNotificationSettingsRepository = FakeBackgroundNotificationSettingsRepository(showBackgroundNotification = false)
        service.onStartCommand(null, 0, 1)
        idleMainLooper()
        BackgroundMonitoringBridge.setActivityVisible(false)
        idleMainLooper()

        assertReleased()
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun `preference off releases background registrations but keeps preference off`() {
        service = startService()
        service.onStartCommand(null, 0, 1)
        idleMainLooper()
        BackgroundMonitoringBridge.setActivityVisible(false)
        BackgroundMonitoringBridge.setNotificationEnabled(false)
        idleMainLooper()

        assertReleased()
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun `eligibility loss releases callbacks and stable notification`() {
        service = startService()
        service.onStartCommand(null, 0, 1)
        idleMainLooper()
        service.eligible = false
        service.reconcile(activityVisible = false, hasUsableFix = false)
        idleMainLooper()

        assertReleased()
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun `running service eligibility check cleans up after provider loss`() {
        service = startService()
        service.onStartCommand(null, 0, 1)
        idleMainLooper()
        service.eligible = false
        service.checkEligibilityForTest()
        idleMainLooper()

        assertReleased()
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun `location registration failure releases gnss and cleans up foreground notification`() {
        source.failFixRegistration = true
        service = startService()
        service.onStartCommand(null, 0, 1)
        idleMainLooper()

        assertEquals(1, fixRegistrations.registerCount)
        assertEquals(0, satelliteRegistrations.activeCount)
        assertTrue(notifications().isEmpty())
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `gnss registration failure releases location and cleans up foreground notification`() {
        source.failSatelliteRegistration = true
        service = startService()
        service.onStartCommand(null, 0, 1)
        idleMainLooper()

        assertEquals(1, satelliteRegistrations.registerCount)
        assertEquals(0, fixRegistrations.activeCount)
        assertTrue(notifications().isEmpty())
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `disabled location services stop the service without registering`() {
        source.locationEnabled = false
        service = startService()
        service.onStartCommand(null, 0, 1)
        idleMainLooper()

        assertEquals(0, fixRegistrations.registerCount)
        assertEquals(0, satelliteRegistrations.registerCount)
        assertTrue(notifications().isEmpty())
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `missing gnss hardware stops the service without registering`() {
        source.gnssHardware = false
        service = startService()
        service.onStartCommand(null, 0, 1)
        idleMainLooper()

        assertEquals(0, fixRegistrations.registerCount)
        assertEquals(0, satelliteRegistrations.registerCount)
        assertTrue(notifications().isEmpty())
        assertTrue(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `foreground startup failure cleans up without registering`() {
        service = startService()
        service.failForegroundStart = true
        service.onStartCommand(null, 0, 1)
        idleMainLooper()

        assertEquals(0, fixRegistrations.registerCount)
        assertEquals(0, satelliteRegistrations.registerCount)
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun `service destruction releases registrations and notification`() {
        service = startService()
        service.onStartCommand(null, 0, 1)
        idleMainLooper()
        controller.destroy()
        idleMainLooper()

        assertReleased()
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun `old registration callbacks are ignored after recreation`() {
        var fixes = 0
        BackgroundMonitoringBridge.setEventHandlers({ fixes++ }, {})
        service = startService()
        service.onStartCommand(null, 0, 1)
        idleMainLooper()
        val oldCallback = fixRegistrations.all.single()
        controller.destroy()
        BackgroundMonitoringBridge.clear()
        idleMainLooper()

        service = startService()
        service.onStartCommand(null, 0, 2)
        idleMainLooper()
        val newCallback = fixRegistrations.all.last()
        oldCallback(FIX)
        newCallback(FIX)
        idleMainLooper()

        assertEquals(2, fixRegistrations.registerCount)
        assertEquals(1, fixes)
    }

    @Test
    fun `notification tap reconciles the existing service-owned session`() {
        service = startService()
        service.onStartCommand(null, 0, 1)
        idleMainLooper()
        BackgroundMonitoringBridge.setActivityVisible(false)
        val intent = shadowOf(notificationAtStableId()!!.contentIntent).savedIntent

        assertEquals(kniezrec.com.flightinfo.MainActivity::class.java.name, intent.component!!.className)
        BackgroundMonitoringBridge.setActivityVisible(true)

        assertEquals(1, fixRegistrations.registerCount)
        assertEquals(1, fixRegistrations.activeCount)
        assertFalse(notificationTitle().contains("waiting", true))
    }

    @Test
    fun `activity recreation and repeated handoff retain one registration`() {
        service = startService()
        service.onStartCommand(null, 0, 1)
        idleMainLooper()
        BackgroundMonitoringBridge.setActivityVisible(false)
        service.onStartCommand(null, 0, 2)
        BackgroundMonitoringBridge.setActivityVisible(true)
        BackgroundMonitoringBridge.setActivityVisible(false)
        BackgroundMonitoringBridge.setActivityVisible(true)
        idleMainLooper()

        assertEquals(1, fixRegistrations.registerCount)
        assertEquals(1, satelliteRegistrations.registerCount)
        assertEquals(1, fixRegistrations.activeCount)
    }

    private fun startService(canPostNotifications: Boolean = true): TestLocationForegroundService {
        controller = Robolectric.buildService(TestLocationForegroundService::class.java).create()
        service = controller.get()
        service.canPost = canPostNotifications
        service.locationRepository = repository
        return service
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
    var canPost = true
    var failForegroundStart = false

    override fun isMonitoringEligible(): Boolean = eligible

    override fun canPostNotifications(): Boolean = canPost

    override fun startForegroundServiceNotification(): Boolean = !failForegroundStart
}

private val FIX = FlightLocationFix(1.0, 100.0, 1L)
