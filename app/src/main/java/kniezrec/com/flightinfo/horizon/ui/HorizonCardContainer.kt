package kniezrec.com.flightinfo.horizon.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** [HorizonCard] bound to [HorizonViewModel]. */
@Composable
fun HorizonCardContainer(
    modifier: Modifier = Modifier,
    viewModel: HorizonViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HorizonCard(state, viewModel::calibrate, viewModel::retry, modifier)
}
