package kniezrec.com.flightinfo.gnss.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** [GnssStatusCard] bound to [GnssStatusViewModel]. */
@Composable
fun GnssStatusCardContainer(
    onOpenLocationSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GnssStatusViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GnssStatusCard(state, onOpenLocationSettings, onRetry, modifier)
}
