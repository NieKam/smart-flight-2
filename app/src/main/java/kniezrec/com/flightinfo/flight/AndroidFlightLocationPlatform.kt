package kniezrec.com.flightinfo.flight

import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
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
                    onLocation(
                        FlightLocationFix(
                            speedMetresPerSecond = location.takeIf(Location::hasSpeed)?.speed?.toDouble(),
                            altitudeMetres = location.takeIf(Location::hasAltitude)?.altitude,
                            elapsedRealtimeNanos = location.elapsedRealtimeNanos,
                            bearingDegrees = location.takeIf(Location::hasBearing)?.bearing?.toDouble(),
                            latitude = location.latitude,
                            longitude = location.longitude,
                        ),
                    )
                }
            }
        listener = newListener
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            LocationRequest.Builder(1_000L).build(),
            callbackExecutor,
            newListener,
        )
        return true
    }

    override fun unregisterLocationListener() {
        listener?.let(locationManager::removeUpdates)
        listener = null
    }
}
