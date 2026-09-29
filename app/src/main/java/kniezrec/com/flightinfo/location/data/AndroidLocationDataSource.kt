package kniezrec.com.flightinfo.location.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.gnss.GnssSatellite
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.Executor
import javax.inject.Inject

/**
 * [LocationDataSource] over [LocationManager]. Callbacks are delivered on the main thread (main
 * executor), as with the former per-screen platforms.
 */
internal class AndroidLocationDataSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val locationManager: LocationManager,
        private val packageManager: PackageManager,
    ) : LocationDataSource {
        private val callbackExecutor: Executor get() = context.mainExecutor

        override fun locationFixes(): Flow<FlightLocationFix> =
            callbackFlow {
                val listener = LocationListener { location -> trySend(location.toFlightLocationFix()) }
                registerUntilClosed(
                    description = "GPS location updates",
                    register = {
                        locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            gpsLocationRequest(),
                            callbackExecutor,
                            listener,
                        )
                        true
                    },
                    unregister = { locationManager.removeUpdates(listener) },
                )
            }

        override fun satellites(): Flow<List<GnssSatellite>> =
            callbackFlow {
                val callback =
                    object : GnssStatus.Callback() {
                        override fun onSatelliteStatusChanged(status: GnssStatus) {
                            trySend(status.toGnssSatellites())
                        }
                    }
                registerUntilClosed(
                    description = "GNSS status callback",
                    register = { locationManager.registerGnssStatusCallback(callbackExecutor, callback) },
                    unregister = { locationManager.unregisterGnssStatusCallback(callback) },
                )
            }

        override fun isLocationEnabled(): Boolean = locationManager.isLocationEnabled

        override fun hasGnssHardware(): Boolean = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)

        override fun locationEnabledChanges(): Flow<Boolean> =
            callbackFlow {
                val receiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(
                            context: Context,
                            intent: Intent,
                        ) {
                            trySend(locationManager.isLocationEnabled)
                        }
                    }
                // PROVIDERS_CHANGED_ACTION is a protected system broadcast: no export flag is needed
                // (same registration as LocationForegroundService's provider receiver).
                context.registerReceiver(receiver, IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION))
                // Read after registering so that a change in between is not lost.
                trySend(locationManager.isLocationEnabled)
                awaitClose { context.unregisterReceiver(receiver) }
            }.distinctUntilChanged()
    }

/** GPS updates every second. */
internal fun gpsLocationRequest(): LocationRequest = LocationRequest.Builder(GPS_UPDATE_INTERVAL_MILLIS).build()

internal const val GPS_UPDATE_INTERVAL_MILLIS = 1_000L

internal fun Location.toFlightLocationFix(): FlightLocationFix =
    FlightLocationFix(
        speedMetresPerSecond = takeIf(Location::hasSpeed)?.speed?.toDouble(),
        altitudeMetres = takeIf(Location::hasAltitude)?.altitude,
        elapsedRealtimeNanos = elapsedRealtimeNanos,
        bearingDegrees = takeIf(Location::hasBearing)?.bearing?.toDouble(),
        latitude = latitude,
        longitude = longitude,
    )

internal fun GnssStatus.toGnssSatellites(): List<GnssSatellite> =
    List(satelliteCount) { index ->
        GnssSatellite(usedInFix(index), getCn0DbHz(index))
    }
