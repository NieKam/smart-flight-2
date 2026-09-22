package kniezrec.com.flightinfo.monitoring

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import kniezrec.com.flightinfo.MainActivity

class LocationForegroundService : Service() {
    private var locationListener: LocationListener? = null
    private var gnssCallback: GnssStatus.Callback? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !isEligible()) {
            stopMonitoring()
            stopSelf()
            return START_NOT_STICKY
        }
        stopMonitoring()
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
        registerMonitoring()
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

    private fun isEligible(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
            getSystemService(LocationManager::class.java).isLocationEnabled &&
            BackgroundNotificationPreferencesStore(
                getSharedPreferences(BackgroundNotificationPreferencesStore.PREFERENCES_NAME, MODE_PRIVATE),
            ).read().showBackgroundNotification

    private fun registerMonitoring() {
        val locationManager = getSystemService(LocationManager::class.java)
        try {
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (location.hasAccuracy() && location.accuracy >= 0f) {
                        stopMonitoring()
                        stopSelf()
                    }
                }
            }
            locationListener = listener
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, mainExecutor, listener)
            val callback = object : GnssStatus.Callback() {}
            gnssCallback = callback
            locationManager.registerGnssStatusCallback(mainExecutor, callback)
        } catch (_: SecurityException) {
            stopMonitoring()
            stopSelf()
        } catch (_: RuntimeException) {
            stopMonitoring()
            stopSelf()
        }
    }

    private fun stopMonitoring() {
        val locationManager = getSystemService(LocationManager::class.java)
        locationListener?.let(locationManager::removeUpdates)
        locationListener = null
        gnssCallback?.let(locationManager::unregisterGnssStatusCallback)
        gnssCallback = null
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.background_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notification(): Notification {
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
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.background_notification_title))
            .setContentText(getString(R.string.background_notification_content))
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
    }
}
