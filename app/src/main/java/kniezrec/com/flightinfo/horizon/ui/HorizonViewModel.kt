package kniezrec.com.flightinfo.horizon.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kniezrec.com.flightinfo.horizon.HorizonState
import kniezrec.com.flightinfo.horizon.mapHorizonAttitude
import kniezrec.com.flightinfo.orientation.OrientationSample
import kniezrec.com.flightinfo.orientation.data.OrientationDataSource
import kniezrec.com.flightinfo.orientation.data.OrientationRegistrationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * State of the Horizon card: pitch relative to a reference pitch, and roll.
 *
 * The first sample of each observation becomes the reference (level); [calibrate] shows
 * recalibrating and makes the next sample the new reference. Observation runs while [state] is
 * collected and stops [STOP_TIMEOUT_MILLIS] after the last collector leaves, so a configuration
 * change keeps the card and its reference. When observation restarts (or on [retry]) the card
 * starts over from waiting and the reference is captured again. Without an orientation sensor
 * (rotation vector, or accelerometer and magnetometer) the card is unavailable; a refused sensor
 * registration shows the error until [retry].
 */
@HiltViewModel
class HorizonViewModel
    @Inject
    constructor(
        private val orientationDataSource: OrientationDataSource,
    ) : ViewModel() {
        private val restarts = MutableStateFlow(0)

        /** Calibration requests; dropped while nothing is observed, as there is no reference then. */
        private val calibrations = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        @OptIn(ExperimentalCoroutinesApi::class)
        val state: StateFlow<HorizonState> =
            restarts
                .flatMapLatest { horizonStates() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HorizonState.Waiting)

        /** Makes the next sample the level reference. */
        fun calibrate() {
            calibrations.tryEmit(Unit)
        }

        /** Starts observation over (after an error). */
        fun retry() {
            restarts.update { it + 1 }
        }

        private fun horizonStates(): Flow<HorizonState> {
            if (!orientationDataSource.isAvailable()) return flowOf(HorizonState.Unavailable)
            return flow<HorizonState> {
                var referencePitchDegrees: Double? = null
                merge<HorizonInput>(
                    calibrations.map { HorizonInput.Calibrate },
                    orientationDataSource.samples.map { HorizonInput.Sample(it) },
                ).collect { input ->
                    when (input) {
                        HorizonInput.Calibrate -> {
                            referencePitchDegrees = null
                            emit(HorizonState.Recalibrating)
                        }
                        is HorizonInput.Sample -> {
                            val pitch = input.sample.pitchDegrees
                            val roll = input.sample.rollDegrees
                            if (pitch.isFinite() && roll.isFinite()) {
                                val reference = referencePitchDegrees ?: pitch.also { referencePitchDegrees = it }
                                mapHorizonAttitude(pitch - reference, roll)?.let { emit(it) }
                            }
                        }
                    }
                }
            }.onStart { emit(HorizonState.Waiting) }
                .catch { cause -> if (cause is OrientationRegistrationException) emit(HorizonState.Error) else throw cause }
        }

        private sealed interface HorizonInput {
            data object Calibrate : HorizonInput

            data class Sample(
                val sample: OrientationSample,
            ) : HorizonInput
        }

        internal companion object {
            /** Longer than a configuration change, shorter than a real trip to the background. */
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
