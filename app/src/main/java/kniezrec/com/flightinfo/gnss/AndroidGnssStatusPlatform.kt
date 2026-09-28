package kniezrec.com.flightinfo.gnss

import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.LocationManager
import kniezrec.com.flightinfo.location.data.toGnssSatellites
import java.util.concurrent.Executor

internal class AndroidGnssStatusPlatform(
    private val locationManager: LocationManager,
    private val packageManager: PackageManager,
    private val callbackExecutor: Executor,
) : GnssStatusPlatform {
    private var callback: GnssStatus.Callback? = null

    override fun areLocationServicesEnabled(): Boolean = locationManager.isLocationEnabled

    override fun hasGnssHardware(): Boolean = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)

    override fun registerGnssStatusCallback(onStatus: (List<GnssSatellite>) -> Unit): Boolean {
        val newCallback =
            object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    onStatus(status.toGnssSatellites())
                }
            }
        callback = newCallback
        return try {
            locationManager.registerGnssStatusCallback(callbackExecutor, newCallback)
        } catch (_: RuntimeException) {
            runCatching { locationManager.unregisterGnssStatusCallback(newCallback) }
            callback = null
            false
        }
    }

    override fun unregisterGnssStatusCallback() {
        callback?.let(locationManager::unregisterGnssStatusCallback)
        callback = null
    }
}
