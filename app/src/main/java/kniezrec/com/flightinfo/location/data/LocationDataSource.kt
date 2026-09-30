package kniezrec.com.flightinfo.location.data

import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.gnss.GnssSatellite
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow

/**
 * Platform boundary for GPS fixes, GNSS satellite status and the location-services switch.
 *
 * Every returned flow is cold: each collection registers its own platform callback and the
 * callback is unregistered when the collection ends. A registration the platform refuses or
 * that throws ends the flow with [LocationRegistrationException].
 */
interface LocationDataSource {
    /** GPS fixes, requested every second. */
    fun locationFixes(): Flow<FlightLocationFix>

    /** Satellites of every GNSS status report. */
    fun satellites(): Flow<List<GnssSatellite>>

    /** Whether GPS positions can be received now (GPS provider and location switch on). */
    fun isLocationEnabled(): Boolean

    fun hasGnssHardware(): Boolean

    /** Current value of [isLocationEnabled], then every change of it. */
    fun locationEnabledChanges(): Flow<Boolean>
}

/** The platform refused or failed to register a location or GNSS callback. */
class LocationRegistrationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Registers a platform callback for the lifetime of this `callbackFlow` producer and unregisters
 * it when the collection ends.
 *
 * [register] returning false or throwing a [RuntimeException] (including [SecurityException])
 * ends the flow with [LocationRegistrationException]. [unregister] runs in that case too, because
 * Android may install the callback before it throws or refuses.
 */
internal suspend fun ProducerScope<*>.registerUntilClosed(
    description: String,
    register: () -> Boolean,
    unregister: () -> Unit,
) {
    val registered =
        try {
            register()
        } catch (exception: RuntimeException) {
            runCatching { unregister() }
            throw LocationRegistrationException("$description registration failed", exception)
        }
    if (!registered) {
        runCatching { unregister() }
        throw LocationRegistrationException("$description registration was refused")
    }
    awaitClose { runCatching { unregister() } }
}
