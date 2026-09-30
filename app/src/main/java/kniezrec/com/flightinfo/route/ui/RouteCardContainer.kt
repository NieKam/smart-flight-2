package kniezrec.com.flightinfo.route.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kniezrec.com.flightinfo.displayunits.DistanceUnit
import kniezrec.com.flightinfo.route.RouteEndpoint

/**
 * [RouteCard] bound to [RouteViewModel], with distances in [distanceUnit].
 *
 * @param onChoose opens the city picker for an endpoint (the picker is a screen-level overlay).
 */
@Composable
fun RouteCardContainer(
    distanceUnit: DistanceUnit,
    onChoose: (RouteEndpoint) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RouteCard(
        state = state,
        onChoose = onChoose,
        onClear = viewModel::clear,
        onClearAll = viewModel::clearAll,
        onRestoreRetry = viewModel::retryRestore,
        distanceUnit = distanceUnit,
        modifier = modifier,
    )
}
