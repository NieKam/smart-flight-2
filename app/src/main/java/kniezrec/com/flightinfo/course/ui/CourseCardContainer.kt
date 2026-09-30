package kniezrec.com.flightinfo.course.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * [CourseCard] bound to [CourseViewModel]. Without [locationPermitted] the card shows the compass
 * heading only (no GPS bearing, and no location collection).
 */
@Composable
fun CourseCardContainer(
    locationPermitted: Boolean,
    modifier: Modifier = Modifier,
    viewModel: CourseViewModel = hiltViewModel(),
) {
    // Before the collection below starts, so no location is collected without permission.
    SideEffect { viewModel.setLocationPermitted(locationPermitted) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    CourseCard(state, viewModel::retry, modifier)
}
