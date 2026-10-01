package kniezrec.com.flightinfo.flight.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kniezrec.com.flightinfo.flight.FlightParametersState
import kniezrec.com.flightinfo.flight.data.PressureDataSource
import kniezrec.com.flightinfo.flight.flightParameters
import kniezrec.com.flightinfo.flight.withPressure
import kniezrec.com.flightinfo.location.data.LocationRegistrationException
import kniezrec.com.flightinfo.location.data.LocationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * State of the Flight parameters card: GPS fixes turned into readings, with the barometer value
 * attached whether or not there is a GPS reading (pressure alone shows readings with only the
 * pressure). Location switched off drops the GPS readings, not the pressure.
 *
 * Observation (GPS fixes and the pressure sensor) runs while [state] is collected and stops
 * [STOP_TIMEOUT_MILLIS] after the last collector leaves, so a configuration change keeps the
 * readings. When observation restarts, the card starts over from waiting with an empty
 * vertical-speed history. A failed GPS registration leaves the GPS readings as they are.
 */
@HiltViewModel
class FlightParametersViewModel
    @Inject
    constructor(
        locationRepository: LocationRepository,
        pressureDataSource: PressureDataSource,
    ) : ViewModel() {
        @OptIn(ExperimentalCoroutinesApi::class)
        private val flightReadings: Flow<FlightParametersState> =
            locationRepository.confirmedLocationEnabled.flatMapLatest { enabled ->
                if (enabled) {
                    locationRepository.fixes
                        .catch { cause -> if (cause !is LocationRegistrationException) throw cause }
                        .flightParameters()
                } else {
                    flowOf(FlightParametersState.Waiting)
                }
            }

        private val pressureMillibars: Flow<Double?> = pressureDataSource.pressureMillibars()

        val state: StateFlow<FlightParametersState> =
            combine(flightReadings, pressureMillibars.onStart { emit(null) }) { readings, pressure ->
                readings.withPressure(pressure)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), FlightParametersState.Waiting)

        internal companion object {
            /** Longer than a configuration change, shorter than a real trip to the background. */
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
