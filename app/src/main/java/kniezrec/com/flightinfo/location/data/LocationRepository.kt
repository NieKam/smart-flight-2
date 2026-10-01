package kniezrec.com.flightinfo.location.data

import kniezrec.com.flightinfo.data.shareRethrowingIn
import kniezrec.com.flightinfo.di.ApplicationScope
import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.gnss.GnssSatellite
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

        /**
         * [locationEnabled] for reacting to the switch. A new collector of the StateFlow first gets
         * the value cached by an earlier subscription, possibly stale (e.g. after a trip to the
         * location settings), so "off" counts only when a fresh read confirms it.
         */
        val confirmedLocationEnabled: Flow<Boolean> =
            locationEnabled
                .map { enabled -> enabled || dataSource.isLocationEnabled() }
                .distinctUntilChanged()

        fun isLocationEnabled(): Boolean = dataSource.isLocationEnabled()

        fun hasGnssHardware(): Boolean = dataSource.hasGnssHardware()
    }
