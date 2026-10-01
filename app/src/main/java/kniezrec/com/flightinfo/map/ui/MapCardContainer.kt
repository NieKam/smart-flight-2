package kniezrec.com.flightinfo.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kniezrec.com.flightinfo.route.ui.RouteViewModel

/**
 * [MapCard] bound to [MapViewModel], with the route of [routeViewModel] drawn over it. The max-zoom
 * tip the map's zooming requests ([MapViewModel.zoomTip]) is shown by the screen, not by the card.
 *
 * @param maxMapHeight height of the viewport the card scrolls in (see [MapCard]).
 */
@Composable
fun MapCardContainer(
    modifier: Modifier = Modifier,
    maxMapHeight: Dp = Dp.Infinity,
    viewModel: MapViewModel = hiltViewModel(),
    routeViewModel: RouteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val routeState by routeViewModel.state.collectAsStateWithLifecycle()
    MapCard(
        state = state,
        onRetry = viewModel::retry,
        onUnavailable = viewModel::onMapOpenFailed,
        onCentered = viewModel::onCentered,
        onZoomChanged = viewModel::onZoomChanged,
        routeOverlay = routeState.overlay,
        maxMapHeight = maxMapHeight,
        modifier = modifier,
    )
}
