package kniezrec.com.flightinfo.horizon.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * [HorizonCard] bound to [HorizonViewModel]. [onHide] is the "Hide" choice offered when the device
 * has no attitude sensor.
 */
@Composable
fun HorizonCardContainer(
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HorizonViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HorizonCard(state, viewModel::calibrate, viewModel::resetToAbsolute, viewModel::retry, onHide, modifier)
}
