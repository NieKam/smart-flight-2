package kniezrec.com.flightinfo.testutil

import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.gnss.GnssSatellite
import kniezrec.com.flightinfo.location.data.LocationDataSource
import kniezrec.com.flightinfo.location.data.LocationRegistrationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * In-memory [LocationDataSource] that records registrations like the Android one: every
 * collection registers a callback, and cancelling the collection unregisters it.
 */
class FakeLocationDataSource(
    var locationEnabled: Boolean = true,
    var gnssHardware: Boolean = true,
) : LocationDataSource {
    val fixRegistrations = Registrations<FlightLocationFix>()
    val satelliteRegistrations = Registrations<List<GnssSatellite>>()
    private val enabledRegistrations = Registrations<Boolean>()

    var failFixRegistration = false
    var failSatelliteRegistration = false

    override fun locationFixes(): Flow<FlightLocationFix> = fixRegistrations.flow { failFixRegistration }

    override fun satellites(): Flow<List<GnssSatellite>> = satelliteRegistrations.flow { failSatelliteRegistration }

    override fun isLocationEnabled(): Boolean = locationEnabled

    override fun hasGnssHardware(): Boolean = gnssHardware

    override fun locationEnabledChanges(): Flow<Boolean> =
        callbackFlow {
            val sender: (Boolean) -> Unit = { trySend(it) }
            enabledRegistrations.active += sender
            trySend(locationEnabled)
            awaitClose { enabledRegistrations.active -= sender }
        }

    fun emitFix(fix: FlightLocationFix) = fixRegistrations.emit(fix)

    fun emitSatellites(satellites: List<GnssSatellite>) = satelliteRegistrations.emit(satellites)

    fun setLocationEnabled(enabled: Boolean) {
        locationEnabled = enabled
        enabledRegistrations.emit(enabled)
    }

    /** Callbacks of one data type; [all] keeps released ones so tests can fire late callbacks. */
    class Registrations<T> {
        val all = mutableListOf<(T) -> Unit>()
        val active = mutableListOf<(T) -> Unit>()
        var registerCount = 0
            private set
        var unregisterCount = 0
            private set

        val activeCount: Int get() = active.size

        fun emit(value: T) = active.toList().forEach { it(value) }

        internal fun flow(fail: () -> Boolean): Flow<T> =
            callbackFlow {
                registerCount++
                if (fail()) throw LocationRegistrationException("fake registration refused")
                val sender: (T) -> Unit = { trySend(it) }
                all += sender
                active += sender
                awaitClose {
                    active -= sender
                    unregisterCount++
                }
            }
    }
}
