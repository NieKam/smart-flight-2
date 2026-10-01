package kniezrec.com.flightinfo.flight.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kniezrec.com.flightinfo.displayunits.UnitPreferences

/** [FlightParametersCard] bound to [FlightParametersViewModel], shown in [units]. */
@Composable
fun FlightParametersCardContainer(
    units: UnitPreferences,
    modifier: Modifier = Modifier,
    viewModel: FlightParametersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    FlightParametersCard(state, units, modifier)
}
