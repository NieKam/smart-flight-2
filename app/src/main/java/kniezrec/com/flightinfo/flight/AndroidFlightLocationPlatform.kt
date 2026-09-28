package kniezrec.com.flightinfo.flight

import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import kniezrec.com.flightinfo.location.data.gpsLocationRequest
import kniezrec.com.flightinfo.location.data.toFlightLocationFix
import java.util.concurrent.Executor

internal class AndroidFlightLocationPlatform(
    private val locationManager: LocationManager,
    private val packageManager: PackageManager,
    private val callbackExecutor: Executor,
) : FlightLocationPlatform {
    private var listener: LocationListener? = null

    override fun areLocationServicesEnabled(): Boolean = locationManager.isLocationEnabled

    override fun hasGnssHardware(): Boolean = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)

    override fun registerLocationListener(onLocation: (FlightLocationFix) -> Unit): Boolean {
        val newListener =
            object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    onLocation(location.toFlightLocationFix())
                }
            }
        listener = newListener
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                gpsLocationRequest(),
                callbackExecutor,
                newListener,
            )
        } catch (_: RuntimeException) {
            runCatching { locationManager.removeUpdates(newListener) }
            listener = null
            return false
        }
        return true
    }

    override fun unregisterLocationListener() {
        listener?.let(locationManager::removeUpdates)
        listener = null
    }
}
