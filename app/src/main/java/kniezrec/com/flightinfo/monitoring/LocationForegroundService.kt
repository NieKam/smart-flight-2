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

/** Foreground lifetime for the activity-owned location/GNSS monitoring session. */
class LocationForegroundService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var providerReceiver: BroadcastReceiver? = null
    private var started = false

    private val eligibilityCheck =
        object : Runnable {
            override fun run() {
                if (!isEligible()) {
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
        if (intent?.action == ACTION_STOP || !isEligible()) {
            stopMonitoring()
            stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        if (!started) {
            startForeground(
                NOTIFICATION_ID,
                notification(showWaiting = false),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
            started = true
            registerProviderReceiver()
            handler.post(eligibilityCheck)
        }
        BackgroundMonitoringBridge.attach(this)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopMonitoring()
        BackgroundMonitoringBridge.detach(this)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopMonitoring()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    internal fun reconcile(
        activityVisible: Boolean,
        hasUsableFix: Boolean,
    ) {
        if (!started || !isEligible()) {
            if (!isEligible()) {
                stopForEligibility()
            }
            return
        }
        updateNotification(showWaiting = !activityVisible && !hasUsableFix)
    }

    internal fun onUsableFix() {
        stopMonitoring()
        stopSelf()
    }

    private fun isEligible(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            getSystemService(LocationManager::class.java).isLocationEnabled &&
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
                    if (intent.action == LocationManager.PROVIDERS_CHANGED_ACTION && !isEligible()) {
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

    private fun updateNotification(showWaiting: Boolean) {
        if (started) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(showWaiting))
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
