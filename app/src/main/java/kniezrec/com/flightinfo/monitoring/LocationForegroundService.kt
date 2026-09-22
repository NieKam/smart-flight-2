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
import kniezrec.com.flightinfo.MainActivity
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.flight.AndroidFlightLocationPlatform
import kniezrec.com.flightinfo.gnss.AndroidGnssStatusPlatform

/** Foreground lifetime for the service-owned location/GNSS monitoring session. */
internal interface MonitoringSession {
    fun start(): Boolean

    fun stop()
}

internal class LocationForegroundService :
    Service(),
    BackgroundMonitoringService {
    private val handler = Handler(Looper.getMainLooper())
    private var providerReceiver: BroadcastReceiver? = null
    private var started = false
    private var monitoringSession: MonitoringSession? = null
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
            createMonitoringSession(generation).also { session ->
                monitoringSession = session
                if (!session.start()) {
                    stopMonitoring()
                    stopSelf()
                    return START_NOT_STICKY
                }
            }
            registerProviderReceiver()
            handler.post(eligibilityCheck)
            BackgroundMonitoringBridge.reconcile()
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
            getSystemService(LocationManager::class.java).isLocationEnabled

    protected open fun showBackgroundNotification(): Boolean =
        BackgroundNotificationPreferencesStore(
            getSharedPreferences(BackgroundNotificationPreferencesStore.PREFERENCES_NAME, MODE_PRIVATE),
        ).read().showBackgroundNotification

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
        monitoringSession?.stop()
        monitoringSession = null
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

    protected open fun createMonitoringSession(generation: Long): MonitoringSession =
        LocationGnssMonitoringSession(
            locationPlatform =
                AndroidFlightLocationPlatform(
                    getSystemService(LocationManager::class.java),
                    packageManager,
                    mainExecutor,
                ),
            gnssPlatform =
                AndroidGnssStatusPlatform(
                    getSystemService(LocationManager::class.java),
                    packageManager,
                    mainExecutor,
                ),
            onLocation = { fix -> BackgroundMonitoringBridge.forwardLocation(generation, fix) },
            onGnssStatus = { status -> BackgroundMonitoringBridge.forwardGnssStatus(generation, status) },
        )

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
