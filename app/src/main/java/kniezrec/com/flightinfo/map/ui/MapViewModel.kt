package kniezrec.com.flightinfo.map.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kniezrec.com.flightinfo.course.normalizeCourseDegrees
import kniezrec.com.flightinfo.display.data.DisplaySettingsRepository
import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.location.data.LocationRegistrationException
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.map.MapTracking
import kniezrec.com.flightinfo.map.data.MapArchiveRepository
import kniezrec.com.flightinfo.orientation.HeadingSmoother
import kniezrec.com.flightinfo.orientation.data.OrientationDataSource
import kniezrec.com.flightinfo.orientation.data.OrientationRegistrationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import java.io.File
import javax.inject.Inject

/**
 * State of the offline map card.
 *
 * An observation prepares the archive ([MapUiState.Loading]), then shows it
 * ([MapUiState.Ready]) with the latest valid GPS position and the marker course. The first
 * position of the observation is offered once as [MapUiState.Ready.centerRequest] until the map
 * acknowledges it with [onCentered]. An archive that cannot be prepared, or that the map fails to
 * open ([onMapOpenFailed]), shows [MapUiState.Unavailable]; [retry] starts a new observation.
 *
 * Observation runs while [state] is collected and stops [STOP_TIMEOUT_MILLIS] after the last
 * collector leaves (an archive preparation still running is cancelled), so a configuration change
 * keeps the map. When observation restarts, the archive is prepared again and the position waits
 * for a new fix. While the location is switched off, or after a failed GPS registration, the last
 * position is kept.
 *
 * The plane marker's heading follows [kniezrec.com.flightinfo.map.markerRotation]: the GPS track
 * while moving, the compass heading (the Course card's value: display-relative, averaged over the
 * last 10 sensor headings, whole degrees) while standing still, otherwise the previous heading. The
 * orientation sensor is collected only while the map is observed; without one, or when its
 * registration is refused, the marker relies on the GPS track alone.
 */
@HiltViewModel
class MapViewModel
    @Inject
    constructor(
        private val mapArchiveRepository: MapArchiveRepository,
        private val locationRepository: LocationRepository,
        private val displaySettingsRepository: DisplaySettingsRepository,
        private val orientationDataSource: OrientationDataSource,
    ) : ViewModel() {
        private val retries = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        // Per observation; reset when the archive is ready.
        private val centered = MutableStateFlow(false)
        private val openFailed = MutableStateFlow(false)

        @OptIn(ExperimentalCoroutinesApi::class)
        val state: StateFlow<MapUiState> =
            retries
                .onStart { emit(Unit) }
                .flatMapLatest { observe() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MapUiState.Loading)

        /** Prepares the archive again and starts over (ignored while not observing). */
        fun retry() {
            retries.tryEmit(Unit)
        }

        /** The map could not open the archive: shows [MapUiState.Unavailable] until [retry]. */
        fun onMapOpenFailed() {
            openFailed.value = true
        }

        /** The map is centered on [MapUiState.Ready.centerRequest]; it is not requested again. */
        fun onCentered() {
            centered.value = true
        }

        private fun observe(): Flow<MapUiState> =
            flow {
                emit(MapUiState.Loading)
                val archive =
                    try {
                        mapArchiveRepository.prepare()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        null
                    }
                if (archive == null) {
                    emit(MapUiState.Unavailable)
                    return@flow
                }
                centered.value = false
                openFailed.value = false
                // Ends at Unavailable, which also releases the fixes until the next observation.
                emitAll(
                    readyStates(archive).transformWhile { uiState ->
                        emit(uiState)
                        uiState !is MapUiState.Unavailable
                    },
                )
            }

        private fun readyStates(archive: File): Flow<MapUiState> =
            combine(
                tracking(),
                displaySettingsRepository.display.map { it.largerMapZoom }.distinctUntilChanged(),
                centered,
                openFailed,
            ) { tracking, largerMapZoom, isCentered, hasOpenFailed ->
                if (hasOpenFailed) {
                    MapUiState.Unavailable
                } else {
                    MapUiState.Ready(
                        archive = archive,
                        position = tracking.position,
                        markerHeadingDegrees = tracking.markerHeadingDegrees,
                        centerRequest = if (isCentered) null else tracking.firstFix,
                        largerMapZoom = largerMapZoom,
                    )
                }
            }

        private fun tracking(): Flow<MapTracking> =
            merge(
                fixes().map { fix -> { tracking: MapTracking -> tracking.accept(fix) } },
                compassHeadings().map { heading -> { tracking: MapTracking -> tracking.acceptCompass(heading) } },
            ).scan(MapTracking()) { tracking, update -> update(tracking) }
                .distinctUntilChanged()

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun fixes(): Flow<FlightLocationFix> =
            locationRepository.confirmedLocationEnabled
                .flatMapLatest { enabled ->
                    if (enabled) {
                        locationRepository.fixes.catch { cause -> if (cause !is LocationRegistrationException) throw cause }
                    } else {
                        emptyFlow<FlightLocationFix>()
                    }
                }

        /** Compass headings as the Course card shows them; none without an orientation sensor. */
        private fun compassHeadings(): Flow<Double> {
            if (!orientationDataSource.isAvailable()) return emptyFlow()
            return flow {
                // One smoother per observation, as the Course card.
                val smoother = HeadingSmoother()
                orientationDataSource.samples.collect { sample ->
                    if (sample.headingDegrees.isFinite()) {
                        normalizeCourseDegrees(smoother.add(sample.headingDegrees))?.let { emit(it.toDouble()) }
                    }
                }
            }.distinctUntilChanged()
                .catch { cause -> if (cause !is OrientationRegistrationException) throw cause }
        }

        internal companion object {
            /** Longer than a configuration change, shorter than a real trip to the background. */
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
