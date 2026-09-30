package kniezrec.com.flightinfo.gnss.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kniezrec.com.flightinfo.gnss.GnssSatellite
import kniezrec.com.flightinfo.gnss.GnssStatusState
import kniezrec.com.flightinfo.location.data.LocationRegistrationException
import kniezrec.com.flightinfo.location.data.LocationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * State of the GNSS card: waiting until a satellite report arrives, then the reported satellites.
 *
 * Location switched off shows [GnssStatusState.LocationServicesDisabled] (the satellites are
 * dropped); switched back on, the card starts over from waiting. A device without GNSS hardware
 * shows [GnssStatusState.Unavailable]. A failed GNSS registration shows [GnssStatusState.Error]
 * until [retry] registers again.
 *
 * Observation runs while [state] is collected and stops [STOP_TIMEOUT_MILLIS] after the last
 * collector leaves, so a configuration change keeps the report. When observation restarts, the
 * card starts over from waiting.
 */
@HiltViewModel
class GnssStatusViewModel
    @Inject
    constructor(
        private val locationRepository: LocationRepository,
    ) : ViewModel() {
        private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        @OptIn(ExperimentalCoroutinesApi::class)
        val state: StateFlow<GnssStatusState> =
            retries
                .onStart { emit(Unit) }
                .flatMapLatest { locationRepository.confirmedLocationEnabled }
                .flatMapLatest { enabled ->
                    when {
                        !enabled -> flowOf(GnssStatusState.LocationServicesDisabled)
                        !locationRepository.hasGnssHardware() -> flowOf(GnssStatusState.Unavailable)
                        else -> satelliteStates()
                    }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), GnssStatusState.Waiting)

        /** Registers for GNSS status again after [GnssStatusState.Error] (ignored while not observing). */
        fun retry() {
            retries.tryEmit(Unit)
        }

        private fun satelliteStates(): Flow<GnssStatusState> =
            locationRepository.satellites
                .map(::gnssStatusState)
                .onStart { emit(GnssStatusState.Waiting) }
                .catch { cause ->
                    if (cause !is LocationRegistrationException) throw cause
                    emit(GnssStatusState.Error)
                }

        internal companion object {
            /** Longer than a configuration change, shorter than a real trip to the background. */
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** An empty report means no satellite yet: the card keeps waiting. */
internal fun gnssStatusState(satellites: List<GnssSatellite>): GnssStatusState =
    if (satellites.isEmpty()) GnssStatusState.Waiting else GnssStatusState.Available(satellites)
