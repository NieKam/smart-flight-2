package kniezrec.com.flightinfo.map.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kniezrec.com.flightinfo.route.ui.RouteViewModel

/** [MapCard] bound to [MapViewModel], with the route of [routeViewModel] drawn over it. */
@Composable
fun MapCardContainer(
    modifier: Modifier = Modifier,
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
        routeOverlay = routeState.overlay,
        modifier = modifier,
    )
}
