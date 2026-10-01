package kniezrec.com.flightinfo.map.ui

import kniezrec.com.flightinfo.map.MapCoordinate
import java.io.File

/** State of the offline map card. */
sealed interface MapUiState {
    /** The offline archive is being prepared. */
    data object Loading : MapUiState

    /** The archive could not be prepared or opened; the card offers a retry. */
    data object Unavailable : MapUiState

    /** The map is not observed (no map ViewModel behind the card). */
    data object Inactive : MapUiState

    /**
     * The map can be shown from [archive].
     *
     * @property position latest valid GPS position of this observation, or null before the first.
     * @property markerHeadingDegrees heading of the plane marker in [0, 360), degrees clockwise from
     *   north (the map is north-up): the GPS track when moving, the compass when standing still,
     *   otherwise the previous heading (0 until one is known). See [kniezrec.com.flightinfo.map.markerRotation].
     * @property centerRequest one-shot: the first position of the observation, to center the map on.
     *   The map acknowledges it with [MapViewModel.onCentered]; it is null afterwards.
     * @property largerMapZoom "larger map zoom" setting (maximum zoom 9 instead of 6).
     */
    data class Ready(
        val archive: File,
        val position: MapCoordinate? = null,
        val markerHeadingDegrees: Float = 0f,
        val centerRequest: MapCoordinate? = null,
        val largerMapZoom: Boolean = false,
    ) : MapUiState
}
