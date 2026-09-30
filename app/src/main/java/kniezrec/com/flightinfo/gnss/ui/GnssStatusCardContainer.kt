package kniezrec.com.flightinfo.gnss.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** [GnssStatusCard] bound to [GnssStatusViewModel]; its retry registers for GNSS status again. */
@Composable
fun GnssStatusCardContainer(
    onOpenLocationSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GnssStatusViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GnssStatusCard(state, onOpenLocationSettings, viewModel::retry, modifier)
}
