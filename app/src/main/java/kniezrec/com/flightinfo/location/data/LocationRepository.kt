package kniezrec.com.flightinfo.location.data

import kniezrec.com.flightinfo.di.ApplicationScope
import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.gnss.GnssSatellite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for GPS fixes, GNSS satellites and the location switch.
 *
 * Each data type has exactly one platform registration, however many collectors there are: it is
 * made when the first collector starts and released as soon as the last one stops
 * ([SharingStarted.WhileSubscribed] without a stop timeout).
 *
 * Replay:
 * - [fixes] replays nothing. A fix is an event; a replayed fix could be old (e.g. from before a
 *   pause) and would count as "first usable fix" for the background monitoring rules.
 * - [satellites] replays the latest report to a collector that joins while the registration is
 *   active, but the replay cache is dropped when the registration is released, so a collector
 *   never receives a report from an earlier registration.
 *
 * A registration failure ([LocationRegistrationException]) is rethrown to every collector of the
 * affected flow, including collectors that join while it stands. It is not retried until all
 * collectors have stopped; the next collection then registers again.
 */
@Singleton
class LocationRepository
    @Inject
    constructor(
        private val dataSource: LocationDataSource,
        @ApplicationScope scope: CoroutineScope,
    ) {
        /** GPS fixes. Collection ends with [LocationRegistrationException] if registration fails. */
        val fixes: Flow<FlightLocationFix> = dataSource.locationFixes().shareRethrowingIn(scope, replay = 0)

        /** GNSS satellite reports, conflated for slow collectors. Fails like [fixes]. */
        val satellites: Flow<List<GnssSatellite>> = dataSource.satellites().conflate().shareRethrowingIn(scope, replay = 1)

        /** Location switch; up to date while collected (read [isLocationEnabled] for a fresh one-off value). */
        val locationEnabled: StateFlow<Boolean> =
            dataSource
                .locationEnabledChanges()
                .stateIn(scope, SharingStarted.WhileSubscribed(), dataSource.isLocationEnabled())

        fun isLocationEnabled(): Boolean = dataSource.isLocationEnabled()

        fun hasGnssHardware(): Boolean = dataSource.hasGnssHardware()
    }

/**
 * Shares this flow in [scope] and rethrows an upstream failure in every collector.
 *
 * Without this, a failure would end the sharing coroutine in [scope] (reaching its exception
 * handler) while collectors wait forever. Instead the failure is kept, and the failed
 * registration stays "started" (without a platform callback), until the last collector stops:
 * every current and joining collector fails with it, none waits for data that cannot come. When
 * sharing stops, the failure is cleared and the next collector registers again.
 */
private fun <T> Flow<T>.shareRethrowingIn(
    scope: CoroutineScope,
    replay: Int,
): Flow<T> {
    val failure = MutableStateFlow<Throwable?>(null)
    val shared =
        catch { cause ->
            failure.value = cause
            try {
                awaitCancellation()
            } finally {
                failure.value = null
            }
        }.shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay)
    return merge(shared, failure.filterNotNull().map<Throwable, T> { throw it })
}
