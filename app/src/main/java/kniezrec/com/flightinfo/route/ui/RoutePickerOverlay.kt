package kniezrec.com.flightinfo.route.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kniezrec.com.flightinfo.map.ui.MapUiState
import kniezrec.com.flightinfo.map.ui.MapViewModel
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme
import kotlinx.coroutines.Dispatchers

/**
 * The city picker as a full-screen overlay, bound to [viewModel]; shows nothing while the picker is
 * closed. The picker map uses the offline archive prepared by [mapViewModel].
 */
@Composable
fun RoutePickerOverlay(
    modifier: Modifier = Modifier,
    viewModel: RoutePickerViewModel = hiltViewModel(),
    mapViewModel: MapViewModel = hiltViewModel(),
) {
    // Immediate, so the search field shows each typed character before the next input event.
    val state by viewModel.state.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    if (state.endpoint == null) return
    val mapState by mapViewModel.state.collectAsStateWithLifecycle()
    Box(modifier.fillMaxSize().background(SmartFlightTheme.colors.pickerBackground)) {
        RoutePicker(
            state = state,
            mapArchive = (mapState as? MapUiState.Ready)?.archive,
            onQueryChange = viewModel::updateQuery,
            onSearch = viewModel::search,
            onNearest = viewModel::nearest,
            onSelect = viewModel::select,
            onConfirm = { viewModel.confirm() },
            onCancel = viewModel::close,
            onRetry = viewModel::retry,
        )
    }
}
