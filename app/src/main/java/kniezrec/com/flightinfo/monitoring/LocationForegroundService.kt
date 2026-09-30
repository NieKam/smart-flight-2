package kniezrec.com.flightinfo.monitoring

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kniezrec.com.flightinfo.MainActivity
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.di.MainDispatcher
import kniezrec.com.flightinfo.location.data.LocationRegistrationException
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.monitoring.data.BackgroundNotificationSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the location registration alive while the dashboard is hidden, until the first usable fix.
 *
 * Rules, re-evaluated whenever visibility, the notification setting or the fix state changes:
 * - hidden after a usable fix of this run: stop;
 * - not eligible (permission or providers lost): stop;
 * - hidden with the background notification turned off: stop;
 * - hidden while notifications cannot be posted (permission denied, app or channel blocked): stop,
 *   so GPS never keeps running in the background without a visible notification;
 * - otherwise show the notification, with the "waiting" copy when hidden without a fix.
 */
@AndroidEntryPoint
internal open class LocationForegroundService : Service() {
    // Injected in super.onCreate() (Hilt_LocationForegroundService); test subclasses inherit them.
    // Tests replace the observed dependencies after onCreate() (before onStartCommand).
    @Inject lateinit var backgroundNotificationSettingsRepository: BackgroundNotificationSettingsRepository

    @Inject lateinit var locationManager: LocationManager

    @Inject lateinit var locationRepository: LocationRepository

    @Inject lateinit var appVisibility: AppVisibility

    @MainDispatcher
    @Inject
    lateinit var mainDispatcher: CoroutineDispatcher

    private val handler = Handler(Looper.getMainLooper())
    private var providerReceiver: BroadcastReceiver? = null
    private var started = false
    private var monitoringScope: CoroutineScope? = null

    /** Whether the current run has received a fix; a new run starts without one. */
    private val hasUsableFix = MutableStateFlow(false)

    private val eligibilityCheck =
        object : Runnable {
            override fun run() {
                if (!isMonitoringEligible()) {
                    stopRun()
                } else {
                    handler.postDelayed(this, CHECK_INTERVAL_MS)
                }
            }
        }

    internal fun checkEligibilityForTest() {
        eligibilityCheck.run()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == ACTION_STOP || !isMonitoringEligible()) {
            stopMonitoring()
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        if (!started) {
            if (!startForegroundServiceNotification()) {
                stopMonitoring()
                stopSelf()
                return START_NOT_STICKY
            }
            started = true
            if (!locationRepository.isLocationEnabled() || !locationRepository.hasGnssHardware()) {
                stopMonitoring()
                stopSelf()
                return START_NOT_STICKY
            }
            hasUsableFix.value = false
            startRun()
            registerProviderReceiver()
            handler.post(eligibilityCheck)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopMonitoring()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    /** Applies the rules in the class documentation to the current state. */
    private fun applyRules(
        activityVisible: Boolean,
        showBackgroundNotification: Boolean,
        hasUsableFix: Boolean,
    ) {
        if (!started) return
        when {
            !activityVisible && hasUsableFix -> stopRun()
            !isMonitoringEligible() -> stopRun()
            !activityVisible && !showBackgroundNotification -> stopRun()
            !activityVisible && !canPostNotifications() -> stopRun()
            else -> updateNotification(showWaiting = !activityVisible && !hasUsableFix)
        }
    }

    protected open fun isMonitoringEligible(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            (
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            )

    private fun registerProviderReceiver() {
        if (providerReceiver != null) return
        providerReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (intent.action == LocationManager.PROVIDERS_CHANGED_ACTION && !isMonitoringEligible()) {
                        stopRun()
                    }
                }
            }
        registerReceiver(providerReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
    }

    private fun stopRun() {
        stopMonitoring()
        stopSelf()
    }

    private fun stopMonitoring() {
        handler.removeCallbacks(eligibilityCheck)
        // Cancelling the last collectors releases the repository's platform registrations.
        monitoringScope?.cancel()
        monitoringScope = null
        providerReceiver?.let { unregisterReceiver(it) }
        providerReceiver = null
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        started = false
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.background_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    protected open fun canPostNotifications(): Boolean {
        val permitted =
            android.os.Build.VERSION.SDK_INT < POST_NOTIFICATIONS_SDK ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && getSystemService(NotificationManager::class.java).backgroundNotificationsEnabled()
    }

    protected open fun startForegroundServiceNotification(): Boolean =
        runCatching {
            startForeground(
                NOTIFICATION_ID,
                notification(showWaiting = false),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        }.isSuccess

    private fun updateNotification(showWaiting: Boolean) {
        if (started) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(showWaiting))
    }

    /**
     * Holds the repository's fix and satellite registrations until [stopMonitoring] (the dashboard
     * collects the same registrations itself) and applies the rules on every state change. A
     * registration failure (reported asynchronously, after the first collection starts) stops the
     * service, as a failed registration in [onStartCommand] did before.
     */
    private fun startRun() {
        val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
        monitoringScope = scope
        scope.launch {
            try {
                coroutineScope {
                    launch { locationRepository.fixes.collect { hasUsableFix.value = true } }
                    // Values unused here: collecting keeps the GNSS registration of the run alive.
                    launch { locationRepository.satellites.collect { } }
                }
            } catch (_: LocationRegistrationException) {
                if (monitoringScope === scope) stopRun()
            }
        }
        scope.launch {
            combine(
                appVisibility.visible,
                backgroundNotificationSettingsRepository.settings,
                hasUsableFix,
            ) { visible, settings, fix -> Triple(visible, settings.showBackgroundNotification, fix) }
                .collect { (visible, showNotification, fix) -> applyRules(visible, showNotification, fix) }
        }
    }

    private fun notification(showWaiting: Boolean): Notification {
        val openApp =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val dismiss =
            PendingIntent.getService(
                this,
                1,
                Intent(this, LocationForegroundService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return Notification
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_plane)
            .setContentTitle(getString(if (showWaiting) R.string.background_notification_title else R.string.app_name))
            .setContentText(getString(if (showWaiting) R.string.background_notification_content else R.string.background_service_content))
            .setContentIntent(openApp)
            .setDeleteIntent(dismiss)
            .setOngoing(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val ACTION_STOP = "kniezrec.com.flightinfo.action.STOP_BACKGROUND_MONITORING"
        const val CHANNEL_ID = "location"
        const val NOTIFICATION_ID = 13013
        private const val CHECK_INTERVAL_MS = 1_000L
    }
}

/** Notifications are on for the app and the background notification channel is not blocked. */
internal fun NotificationManager.backgroundNotificationsEnabled(): Boolean =
    areNotificationsEnabled() &&
        getNotificationChannel(LocationForegroundService.CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
