package kniezrec.com.flightinfo.nearby.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kniezrec.com.flightinfo.displayunits.DistanceUnit

/** [NearbyCityCard] bound to [NearbyCityViewModel], with distances in [distanceUnit]. */
@Composable
fun NearbyCityCardContainer(
    distanceUnit: DistanceUnit,
    modifier: Modifier = Modifier,
    viewModel: NearbyCityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NearbyCityCard(state, viewModel::retry, distanceUnit, modifier)
}
