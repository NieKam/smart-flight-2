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
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
internal open class LocationForegroundService :
    Service(),
    BackgroundMonitoringService {
    // Injected in super.onCreate() (Hilt_LocationForegroundService); test subclasses inherit it.
    @Inject lateinit var backgroundNotificationSettingsRepository: BackgroundNotificationSettingsRepository

    @Inject lateinit var locationManager: LocationManager

    // Replaced by tests after onCreate() (before onStartCommand) with a repository over a fake source.
    @Inject lateinit var locationRepository: LocationRepository

    @MainDispatcher
    @Inject
    lateinit var mainDispatcher: CoroutineDispatcher

    private val handler = Handler(Looper.getMainLooper())
    private var providerReceiver: BroadcastReceiver? = null
    private var started = false
    private var monitoringScope: CoroutineScope? = null
    private var bridgeGeneration: Long? = null

    private val eligibilityCheck =
        object : Runnable {
            override fun run() {
                if (!isMonitoringEligible()) {
                    stopForEligibility()
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
            val generation = BackgroundMonitoringBridge.attach(this)
            bridgeGeneration = generation
            if (!locationRepository.isLocationEnabled() || !locationRepository.hasGnssHardware()) {
                stopMonitoring()
                stopSelf()
                return START_NOT_STICKY
            }
            collectLocation(generation)
            registerProviderReceiver()
            handler.post(eligibilityCheck)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopMonitoring()
        bridgeGeneration?.let { BackgroundMonitoringBridge.detach(this, it) }
        bridgeGeneration = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopMonitoring()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun reconcile(
        activityVisible: Boolean,
        hasUsableFix: Boolean,
    ) {
        if (!started || !isMonitoringEligible()) {
            if (!isMonitoringEligible()) {
                stopForEligibility()
            }
            return
        }
        if (!activityVisible && !showBackgroundNotification()) {
            stopMonitoring()
            stopSelf()
            return
        }
        updateNotification(showWaiting = !activityVisible && !hasUsableFix && canPostNotifications())
    }

    override fun onUsableFix() {
        stopMonitoring()
        stopSelf()
    }

    override fun stopForPreferenceDisabled() {
        stopMonitoring()
        stopSelf()
    }

    protected open fun isMonitoringEligible(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            (
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            )

    protected open fun showBackgroundNotification(): Boolean =
        backgroundNotificationSettingsRepository.settings.value.showBackgroundNotification

    private fun registerProviderReceiver() {
        if (providerReceiver != null) return
        providerReceiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    if (intent.action == LocationManager.PROVIDERS_CHANGED_ACTION && !isMonitoringEligible()) {
                        stopForEligibility()
                    }
                }
            }
        registerReceiver(providerReceiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
    }

    private fun stopForEligibility() {
        BackgroundMonitoringBridge.notifyEligibilityLost()
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

    protected open fun canPostNotifications(): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

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
     * Forwards fixes and satellites to the bridge until [stopMonitoring]. A registration failure
     * (reported asynchronously, after the first collection starts) stops the service, as a failed
     * registration in [onStartCommand] did before.
     */
    private fun collectLocation(generation: Long) {
        val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
        monitoringScope = scope
        scope.launch {
            try {
                coroutineScope {
                    launch {
                        locationRepository.fixes.collect { fix -> BackgroundMonitoringBridge.forwardLocation(generation, fix) }
                    }
                    launch {
                        locationRepository.satellites.collect { status -> BackgroundMonitoringBridge.forwardGnssStatus(generation, status) }
                    }
                }
            } catch (_: LocationRegistrationException) {
                if (monitoringScope === scope) {
                    stopMonitoring()
                    stopSelf()
                }
            }
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
            .setSmallIcon(R.mipmap.ic_launcher)
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
