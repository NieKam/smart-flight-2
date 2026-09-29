package kniezrec.com.flightinfo.course.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kniezrec.com.flightinfo.course.CourseState
import kniezrec.com.flightinfo.course.normalizeCourseDegrees
import kniezrec.com.flightinfo.location.data.LocationRegistrationException
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.orientation.data.OrientationDataSource
import kniezrec.com.flightinfo.orientation.data.OrientationRegistrationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * State of the Course card: the compass heading from the orientation sensor, with the GPS bearing
 * of the latest fix as a supplement (a bearing that arrives before the first heading is shown with
 * it; a fix without a bearing clears it).
 *
 * Observation runs while [state] is collected and stops [STOP_TIMEOUT_MILLIS] after the last
 * collector leaves, so a configuration change keeps the card. When observation restarts (or on
 * [retry]) the card starts over from waiting, without a bearing. Without a rotation-vector sensor
 * the card is unavailable; a refused sensor registration shows the error until [retry]. While the
 * location is switched off, or after a failed GPS registration, the last bearing stays.
 */
@HiltViewModel
class CourseViewModel
    @Inject
    constructor(
        private val orientationDataSource: OrientationDataSource,
        locationRepository: LocationRepository,
    ) : ViewModel() {
        private val restarts = MutableStateFlow(0)

        @OptIn(ExperimentalCoroutinesApi::class)
        private val gpsBearings: Flow<Int?> =
            locationRepository.confirmedLocationEnabled.flatMapLatest { enabled ->
                if (enabled) {
                    locationRepository.fixes
                        .map { fix -> fix.bearingDegrees?.let(::normalizeCourseDegrees) }
                        .catch { cause -> if (cause !is LocationRegistrationException) throw cause }
                } else {
                    emptyFlow<Int?>()
                }
            }

        @OptIn(ExperimentalCoroutinesApi::class)
        val state: StateFlow<CourseState> =
            restarts
                .flatMapLatest { courseStates() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CourseState.Waiting)

        /** Starts observation over (after an error). */
        fun retry() {
            restarts.update { it + 1 }
        }

        private fun courseStates(): Flow<CourseState> {
            if (!orientationDataSource.isAvailable()) return flowOf(CourseState.Unavailable)
            val headings = orientationDataSource.samples.mapNotNull { normalizeCourseDegrees(it.headingDegrees) }
            return combine<Int, Int?, CourseState>(headings, gpsBearings.onStart { emit(null) }) { heading, bearing ->
                CourseState.Available(heading, bearing)
            }.onStart { emit(CourseState.Waiting) }
                .catch { cause -> if (cause is OrientationRegistrationException) emit(CourseState.Error) else throw cause }
        }

        internal companion object {
            /** Longer than a configuration change, shorter than a real trip to the background. */
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
