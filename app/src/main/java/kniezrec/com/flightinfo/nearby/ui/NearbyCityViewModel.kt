package kniezrec.com.flightinfo.nearby.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kniezrec.com.flightinfo.location.data.LocationRegistrationException
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.NearbyCityState
import kniezrec.com.flightinfo.nearby.NearbyCoordinate
import kniezrec.com.flightinfo.nearby.data.CityRepository
import kniezrec.com.flightinfo.nearby.distanceKilometres
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject

/**
 * State of the Nearby city card: the city nearest to the latest GPS position.
 *
 * Every valid fix starts a lookup ([NearbyCityState.LookingUp]); a newer fix cancels a lookup still
 * running, so a stale result never replaces a newer one. A failed lookup, no city or a city with
 * an unknown time zone shows [NearbyCityState.Unavailable]; [retry] copies the city data again and
 * looks up the latest position.
 *
 * Observation runs while [state] is collected and stops [STOP_TIMEOUT_MILLIS] after the last
 * collector leaves, so a configuration change keeps the card. When observation restarts, the card
 * starts over from waiting. While the location is switched off, or after a failed GPS
 * registration, the card stays as it is.
 */
@HiltViewModel
class NearbyCityViewModel
    @Inject
    constructor(
        locationRepository: LocationRepository,
        private val cityRepository: CityRepository,
        private val clock: Clock,
    ) : ViewModel() {
        private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        @OptIn(ExperimentalCoroutinesApi::class)
        private val positions: Flow<NearbyCoordinate> =
            locationRepository.confirmedLocationEnabled.flatMapLatest { enabled ->
                if (enabled) {
                    locationRepository.fixes
                        .mapNotNull { fix -> NearbyCoordinate.from(fix.latitude, fix.longitude) }
                        .catch { cause -> if (cause !is LocationRegistrationException) throw cause }
                } else {
                    emptyFlow<NearbyCoordinate>()
                }
            }

        @OptIn(ExperimentalCoroutinesApi::class)
        val state: StateFlow<NearbyCityState> =
            flow<NearbyCityState> {
                // Latest valid position of this observation; a retry looks it up again.
                var latest: NearbyCoordinate? = null
                val requests =
                    merge(
                        positions.map { position -> Request(position.also { latest = it }, reload = false) },
                        retries.map { Request(latest, reload = true) },
                    )
                emitAll(
                    requests.transformLatest<Request, NearbyCityState> { request ->
                        val position = request.position
                        if (position == null) {
                            emit(NearbyCityState.WaitingForPosition)
                        } else {
                            emit(NearbyCityState.LookingUp)
                            emit(lookUp(position, request.reload))
                        }
                    },
                )
            }.onStart { emit(NearbyCityState.WaitingForPosition) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), NearbyCityState.WaitingForPosition)

        /** Copies the city data again and looks up the latest position (ignored while not observing). */
        fun retry() {
            retries.tryEmit(Unit)
        }

        private suspend fun lookUp(
            position: NearbyCoordinate,
            reload: Boolean,
        ): NearbyCityState =
            try {
                if (reload) cityRepository.reload()
                cityRepository.nearest(position)?.let { present(it, position) } ?: NearbyCityState.Unavailable
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                NearbyCityState.Unavailable
            }

        private fun present(
            city: NearbyCityRecord,
            position: NearbyCoordinate,
        ): NearbyCityState.Available {
            val zone = ZoneId.of(city.timeZoneId)
            val instant = clock.instant()
            return NearbyCityState.Available(
                cityName = city.name,
                country = city.country,
                distanceKilometres = distanceKilometres(position, NearbyCoordinate(city.latitude, city.longitude)),
                zoneId = zone,
                instant = instant,
                utcOffsetSeconds = zone.rules.getOffset(instant).totalSeconds,
            )
        }

        private class Request(
            val position: NearbyCoordinate?,
            val reload: Boolean,
        )

        internal companion object {
            /** Longer than a configuration change, shorter than a real trip to the background. */
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
