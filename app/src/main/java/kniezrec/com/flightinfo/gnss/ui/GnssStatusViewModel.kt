package kniezrec.com.flightinfo.gnss.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kniezrec.com.flightinfo.gnss.GnssSatellite
import kniezrec.com.flightinfo.gnss.GnssStatusState
import kniezrec.com.flightinfo.location.data.LocationRegistrationException
import kniezrec.com.flightinfo.location.data.LocationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * State of the GNSS card: waiting until a satellite report arrives, then the reported satellites.
 *
 * Observation runs while [state] is collected and stops [STOP_TIMEOUT_MILLIS] after the last
 * collector leaves, so a configuration change keeps the report. When observation restarts, the
 * card starts over from waiting. Location switched off keeps the last report and ignores new ones
 * until the location is back on (then the card starts over from waiting). A failed GNSS
 * registration leaves the card as it is.
 */
@HiltViewModel
class GnssStatusViewModel
    @Inject
    constructor(
        locationRepository: LocationRepository,
    ) : ViewModel() {
        @OptIn(ExperimentalCoroutinesApi::class)
        val state: StateFlow<GnssStatusState> =
            locationRepository.confirmedLocationEnabled
                .flatMapLatest { enabled ->
                    if (enabled) {
                        locationRepository.satellites
                            .map(::gnssStatusState)
                            .onStart { emit(GnssStatusState.Waiting) }
                            .catch { cause -> if (cause !is LocationRegistrationException) throw cause }
                    } else {
                        emptyFlow<GnssStatusState>()
                    }
                }.onStart { emit(GnssStatusState.Waiting) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), GnssStatusState.Waiting)

        internal companion object {
            /** Longer than a configuration change, shorter than a real trip to the background. */
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** An empty report means no satellite yet: the card keeps waiting. */
internal fun gnssStatusState(satellites: List<GnssSatellite>): GnssStatusState =
    if (satellites.isEmpty()) GnssStatusState.Waiting else GnssStatusState.Available(satellites)
