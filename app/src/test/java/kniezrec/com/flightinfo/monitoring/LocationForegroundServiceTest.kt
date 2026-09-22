package kniezrec.com.flightinfo.monitoring

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.gnss.GnssSatellite
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
    private val sessions = mutableListOf<FakeMonitoringSession>()
    private lateinit var service: TestLocationForegroundService
    private lateinit var controller: ServiceController<TestLocationForegroundService>

    @After
    fun tearDown() {
        if (::controller.isInitialized) controller.destroy()
        BackgroundMonitoringBridge.clear()
    }

    @Test
    fun `repeated starts keep one session and stable waiting notification`() {
        service = startService()

        service.onStartCommand(null, 0, 1)
        service.onStartCommand(null, 0, 2)
        BackgroundMonitoringBridge.setActivityVisible(false)

        assertEquals(1, sessions.size)
        assertEquals(1, sessions.single().startCount)
        assertEquals(1, notifications().size)
        assertNotNull(notificationAtStableId())
        assertTrue(notificationTitle().contains("waiting", true))
    }

    @Test
    fun `foreground return replaces waiting copy without duplicating notification`() {
        service = startService()
        service.onStartCommand(null, 0, 1)
        BackgroundMonitoringBridge.setActivityVisible(false)
        BackgroundMonitoringBridge.setActivityVisible(true)

        assertEquals(1, sessions.size)
        assertEquals(1, notifications().size)
        assertFalse(notificationTitle().contains("waiting", true))
    }

    @Test
    fun `service forwards one gnss status through the attached session`() {
        var statuses = 0
        BackgroundMonitoringBridge.setEventHandlers({}, { statuses++ })
        service = startService()
        service.onStartCommand(null, 0, 1)
        sessions.single().emitGnssStatus(listOf(GnssSatellite(true, 20f)))

        assertEquals(1, statuses)
    }

    @Test
    fun `usable fix cancels notification and stops current session`() {
        service = startService()
        service.onStartCommand(null, 0, 1)
        BackgroundMonitoringBridge.setActivityVisible(false)
        sessions.single().emitLocation(FIX)

        assertEquals(1, sessions.single().stopCount)
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun `notification permission denial keeps degraded service without waiting notification`() {
        service = startService(canPostNotifications = false)
        service.onStartCommand(null, 0, 1)
        BackgroundMonitoringBridge.setActivityVisible(false)

        assertEquals(1, sessions.size)
        assertEquals(1, notifications().size)
        assertFalse(notificationTitle().contains("waiting", true))
    }

    @Test
    fun `notification dismissal stops only the current run`() {
        service = startService()
        service.onStartCommand(null, 0, 1)
        service.onStartCommand(
            Intent(service, LocationForegroundService::class.java).setAction(LocationForegroundService.ACTION_STOP),
            0,
            2,
        )

        assertEquals(1, sessions.single().stopCount)
        assertTrue(notifications().isEmpty())
        assertTrue(
            BackgroundNotificationPreferencesStore(
                service.getSharedPreferences(BackgroundNotificationPreferencesStore.PREFERENCES_NAME, 0),
            ).read().showBackgroundNotification,
        )
    }

    @Test
    fun `preference off stops session and leaves preference unchanged`() {
        service = startService()
        BackgroundNotificationPreferencesStore(
            service.getSharedPreferences(BackgroundNotificationPreferencesStore.PREFERENCES_NAME, 0),
        ).write(BackgroundNotificationPreferences(false))
        service.onStartCommand(null, 0, 1)
        BackgroundMonitoringBridge.setNotificationEnabled(false)

        assertEquals(1, sessions.single().stopCount)
        assertTrue(notifications().isEmpty())
        assertFalse(
            BackgroundNotificationPreferencesStore(
                service.getSharedPreferences(BackgroundNotificationPreferencesStore.PREFERENCES_NAME, 0),
            ).read().showBackgroundNotification,
        )
    }

    @Test
    fun `eligibility loss releases callbacks and stable notification`() {
        service = startService()
        service.onStartCommand(null, 0, 1)
        service.eligible = false
        service.reconcile(activityVisible = false, hasUsableFix = false)

        assertEquals(1, sessions.single().stopCount)
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun `registration failure cleans up foreground notification`() {
        service = startService(sessionStarts = false)
        service.onStartCommand(null, 0, 1)

        assertEquals(1, sessions.single().startCount)
        assertEquals(1, sessions.single().stopCount)
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun `foreground startup failure cleans up without creating a session`() {
        service = startService()
        service.failForegroundStart = true
        service.onStartCommand(null, 0, 1)

        assertTrue(sessions.isEmpty())
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun `service destruction unregisters session and notification`() {
        service = startService()
        service.onStartCommand(null, 0, 1)
        controller.destroy()

        assertEquals(1, sessions.single().stopCount)
        assertTrue(notifications().isEmpty())
    }

    @Test
    fun `old session callbacks are ignored after recreation`() {
        var fixes = 0
        BackgroundMonitoringBridge.setEventHandlers({ fixes++ }, {})
        service = startService()
        service.onStartCommand(null, 0, 1)
        val oldSession = sessions.single()
        controller.destroy()
        BackgroundMonitoringBridge.clear()

        service = startService()
        service.onStartCommand(null, 0, 2)
        val newSession = sessions.last()
        oldSession.emitLocation(FIX)
        newSession.emitLocation(FIX)

        assertEquals(1, fixes)
    }

    private fun startService(
        canPostNotifications: Boolean = true,
        sessionStarts: Boolean = true,
    ): TestLocationForegroundService {
        controller = Robolectric.buildService(TestLocationForegroundService::class.java).create()
        service = controller.get()
        service.canPost = canPostNotifications
        service.sessionFactory = { generation ->
            FakeMonitoringSession(generation, sessionStarts).also(sessions::add)
        }
        return service
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
    var sessionFactory: (Long) -> MonitoringSession = { FakeMonitoringSession(it, true) }

    override fun isMonitoringEligible(): Boolean = eligible

    override fun canPostNotifications(): Boolean = canPost

    override fun startForegroundServiceNotification(): Boolean = !failForegroundStart

    override fun createMonitoringSession(generation: Long): MonitoringSession = sessionFactory(generation)
}

private class FakeMonitoringSession(
    private val generation: Long,
    private val startResult: Boolean,
) : MonitoringSession {
    var startCount = 0
    var stopCount = 0

    override fun start(): Boolean {
        startCount++
        return startResult
    }

    override fun stop() {
        stopCount++
    }

    fun emitLocation(fix: FlightLocationFix) = BackgroundMonitoringBridge.forwardLocation(generation, fix)

    fun emitGnssStatus(status: List<GnssSatellite>) = BackgroundMonitoringBridge.forwardGnssStatus(generation, status)
}

private val FIX = FlightLocationFix(1.0, 100.0, 1L)
