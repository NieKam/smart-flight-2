package kniezrec.com.flightinfo.route.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.location.data.LocationRegistrationException
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.NearbyCoordinate
import kniezrec.com.flightinfo.nearby.data.CityRepository
import kniezrec.com.flightinfo.route.RouteEndpoint
import kniezrec.com.flightinfo.route.RouteError
import kniezrec.com.flightinfo.route.RouteFix
import kniezrec.com.flightinfo.route.RouteState
import kniezrec.com.flightinfo.route.data.RouteEndpointIds
import kniezrec.com.flightinfo.route.data.RouteRepository
import kniezrec.com.flightinfo.route.routeDetails
import kniezrec.com.flightinfo.route.routeOverlay
import kniezrec.com.flightinfo.route.validCity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

/**
 * State of the Route card: the saved endpoints resolved to cities, and the route details with the
 * latest GPS position.
 *
 * - Endpoints come from [RouteRepository]; every change resolves both ids again, and a newer change
 *   drops a resolution still running, so the latest write wins. An id that no longer resolves to a
 *   valid city (unknown id, bad coordinates or time zone) is dropped and removed from the saved
 *   route. A failed read shows [RouteError.RESTORE]; [retryRestore] copies the city data again.
 * - Details are present whenever the destination is set (distance to it and arrival); the
 *   departure only adds the distance between the cities. Fixes with invalid coordinates, and fixes
 *   not newer than the last accepted one, are ignored. The map overlay needs both endpoints.
 *
 * Observation runs while [state] is collected and stops [STOP_TIMEOUT_MILLIS] after the last
 * collector leaves, so a configuration change keeps the card. When observation restarts, the route
 * is resolved again and the details wait for a new position. Location switched off drops the
 * position (the details wait for a new one); after a failed GPS registration the last position is
 * kept.
 */
@HiltViewModel
class RouteViewModel
    @Inject
    constructor(
        locationRepository: LocationRepository,
        private val cityRepository: CityRepository,
        private val routeRepository: RouteRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val restoreRetries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        @OptIn(ExperimentalCoroutinesApi::class)
        private val endpoints: Flow<ResolvedEndpoints> =
            merge(
                routeRepository.endpoints.map { ids -> RestoreRequest(ids, reload = false) },
                restoreRetries.map { RestoreRequest(routeRepository.endpoints.value, reload = true) },
            ).mapLatest { request -> resolve(request) }

        @OptIn(ExperimentalCoroutinesApi::class)
        private val fixes: Flow<RouteFix?> =
            locationRepository.confirmedLocationEnabled
                .flatMapLatest { enabled ->
                    if (enabled) {
                        locationRepository.fixes
                            .catch { cause -> if (cause !is LocationRegistrationException) throw cause }
                            .latestValidFixes()
                    } else {
                        // The position is lost: remaining distance and arrival wait for a new fix.
                        flowOf<RouteFix?>(null)
                    }
                }.onStart { emit(null) }

        val state: StateFlow<RouteState> =
            combine(endpoints, fixes) { resolved, fix ->
                val departure = resolved.departure
                val destination = resolved.destination
                RouteState(
                    departure = departure,
                    destination = destination,
                    details = destination?.let { routeDetails(departure, it, fix, clock.instant()) },
                    overlay = routeOverlay(departure, destination),
                    error = resolved.error,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RouteState())

        fun clear(endpoint: RouteEndpoint) {
            viewModelScope.launch { routeRepository.clear(endpoint) }
        }

        fun clearAll() {
            viewModelScope.launch { routeRepository.clearAll() }
        }

        /** Copies the city data again and resolves the saved route (ignored while not observing). */
        fun retryRestore() {
            restoreRetries.tryEmit(Unit)
        }

        private suspend fun resolve(request: RestoreRequest): ResolvedEndpoints {
            val ids = request.ids
            val (departure, destination) =
                try {
                    if (request.reload) cityRepository.reload()
                    ids.departureId?.let { cityRepository.byId(it) } to ids.destinationId?.let { cityRepository.byId(it) }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    return ResolvedEndpoints(error = RouteError.RESTORE)
                }
            return ResolvedEndpoints(
                departure = keepValid(RouteEndpoint.DEPARTURE, ids, departure),
                destination = keepValid(RouteEndpoint.DESTINATION, ids, destination),
            )
        }

        /** [city] if valid; otherwise null, and a saved id that is still [ids]'s is removed. */
        private suspend fun keepValid(
            endpoint: RouteEndpoint,
            ids: RouteEndpointIds,
            city: NearbyCityRecord?,
        ): NearbyCityRecord? {
            if (city != null && validCity(city)) return city
            val id = ids[endpoint]
            // Only the id that was resolved: a newer choice saved meanwhile must not be removed.
            if (id != null && routeRepository.endpoints.value[endpoint] == id) routeRepository.clear(endpoint)
            return null
        }

        private class RestoreRequest(
            val ids: RouteEndpointIds,
            val reload: Boolean,
        )

        private data class ResolvedEndpoints(
            val departure: NearbyCityRecord? = null,
            val destination: NearbyCityRecord? = null,
            val error: RouteError? = null,
        )

        internal companion object {
            /** Longer than a configuration change, shorter than a real trip to the background. */
            const val STOP_TIMEOUT_MILLIS = 5_000L

            /** Fixes with valid coordinates, each newer than the last one accepted in this collection. */
            fun Flow<FlightLocationFix>.latestValidFixes(): Flow<RouteFix> =
                flow {
                    var acceptedTimestampNanos: Long? = null
                    collect { fix ->
                        val coordinate = NearbyCoordinate.from(fix.latitude, fix.longitude) ?: return@collect
                        if (acceptedTimestampNanos?.let { fix.elapsedRealtimeNanos <= it } == true) return@collect
                        acceptedTimestampNanos = fix.elapsedRealtimeNanos
                        emit(RouteFix(coordinate, fix.speedMetresPerSecond))
                    }
                }
        }
    }
