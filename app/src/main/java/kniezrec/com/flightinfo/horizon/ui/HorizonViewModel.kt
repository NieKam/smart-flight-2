package kniezrec.com.flightinfo.horizon.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kniezrec.com.flightinfo.horizon.FilteredAttitude
import kniezrec.com.flightinfo.horizon.HorizonState
import kniezrec.com.flightinfo.horizon.lowPassAttitude
import kniezrec.com.flightinfo.horizon.mapHorizonAttitude
import kniezrec.com.flightinfo.orientation.OrientationSample
import kniezrec.com.flightinfo.orientation.data.OrientationDataSource
import kniezrec.com.flightinfo.orientation.data.OrientationRegistrationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
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
 * State of the Horizon card: pitch relative to a reference pitch, and roll, both low-pass filtered
 * as in the original app ([lowPassAttitude]).
 *
 * The reference is kept in the [SavedStateHandle], so it survives pause/resume, configuration
 * changes and process death. While it is unset (the first sample ever, or after [calibrate]) the
 * next sample becomes the reference (level); [resetToAbsolute] makes it zero, so the card shows
 * the absolute pitch. Observation runs while [state] is collected and stops
 * [STOP_TIMEOUT_MILLIS] after the last collector leaves; when it restarts (or on [retry]) the card
 * waits for a new sample and the filter starts over, but the reference is kept. Without an
 * orientation sensor (rotation vector, or accelerometer and magnetometer) the card is
 * unavailable; a refused sensor registration shows the error until [retry].
 */
@HiltViewModel
class HorizonViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val orientationDataSource: OrientationDataSource,
    ) : ViewModel() {
        private val restarts = MutableStateFlow(0)

        /** Reference changes to show at once; coalesced, and dropped while nothing is observed. */
        private val referenceChanges =
            MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        /**
         * Sample pitch shown as level (samples give nose-up as negative pitch, as
         * `SensorManager.getOrientation`); null until the next sample is captured.
         */
        private var referencePitchDegrees: Double?
            get() = savedStateHandle[KEY_REFERENCE_PITCH]
            set(value) {
                savedStateHandle[KEY_REFERENCE_PITCH] = value
            }

        @OptIn(ExperimentalCoroutinesApi::class)
        val state: StateFlow<HorizonState> =
            restarts
                .flatMapLatest { horizonStates() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HorizonState.Waiting)

        /** Makes the next sample the level reference. */
        fun calibrate() {
            referencePitchDegrees = null
            referenceChanges.tryEmit(Unit)
        }

        /** Shows the absolute pitch: the reference becomes zero. */
        fun resetToAbsolute() {
            referencePitchDegrees = 0.0
            referenceChanges.tryEmit(Unit)
        }

        /** Starts observation over (after an error); the reference is kept. */
        fun retry() {
            restarts.update { it + 1 }
        }

        private fun horizonStates(): Flow<HorizonState> {
            if (!orientationDataSource.isAvailable()) return flowOf(HorizonState.Unavailable)
            return flow<HorizonState> {
                var attitude: FilteredAttitude? = null
                merge<HorizonInput>(
                    referenceChanges.map { HorizonInput.ReferenceChanged },
                    orientationDataSource.samples.map { HorizonInput.Sample(it) },
                ).collect { input ->
                    when (input) {
                        HorizonInput.ReferenceChanged -> {
                            val current = attitude
                            when {
                                referencePitchDegrees == null -> emit(HorizonState.Recalibrating)
                                current != null -> attitudeState(current)?.let { emit(it) }
                            }
                        }
                        is HorizonInput.Sample -> {
                            val pitch = input.sample.pitchDegrees
                            val roll = input.sample.rollDegrees
                            if (pitch.isFinite() && roll.isFinite()) {
                                val filtered = lowPassAttitude(attitude, pitch, roll).also { attitude = it }
                                attitudeState(filtered)?.let { emit(it) }
                            }
                        }
                    }
                }
            }.onStart { emit(HorizonState.Waiting) }
                .catch { cause -> if (cause is OrientationRegistrationException) emit(HorizonState.Error) else throw cause }
        }

        /** [attitude] relative to the reference, capturing it first when unset. */
        private fun attitudeState(attitude: FilteredAttitude): HorizonState.Available? {
            val reference = referencePitchDegrees ?: attitude.pitchDegrees.also { referencePitchDegrees = it }
            return mapHorizonAttitude(reference - attitude.pitchDegrees, attitude.rollDegrees)
        }

        private sealed interface HorizonInput {
            data object ReferenceChanged : HorizonInput

            data class Sample(
                val sample: OrientationSample,
            ) : HorizonInput
        }

        internal companion object {
            /** Longer than a configuration change, shorter than a real trip to the background. */
            const val STOP_TIMEOUT_MILLIS = 5_000L

            /** [SavedStateHandle] key of the reference pitch. */
            const val KEY_REFERENCE_PITCH = "horizon_reference_pitch"
        }
    }
