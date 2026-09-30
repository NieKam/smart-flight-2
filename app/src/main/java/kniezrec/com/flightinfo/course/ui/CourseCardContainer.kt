package kniezrec.com.flightinfo.course.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** [CourseCard] bound to [CourseViewModel]. */
@Composable
fun CourseCardContainer(
    modifier: Modifier = Modifier,
    viewModel: CourseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CourseCard(state, viewModel::retry, modifier)
}
