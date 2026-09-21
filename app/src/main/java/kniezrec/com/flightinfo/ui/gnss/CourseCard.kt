package kniezrec.com.flightinfo.ui.gnss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.course.CourseState
import kniezrec.com.flightinfo.course.compassCardinal
import kniezrec.com.flightinfo.ui.permission.actionCyan
import kniezrec.com.flightinfo.ui.permission.cardPurple
import java.text.NumberFormat

@Composable
internal fun CourseCard(state: CourseState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth().heightIn(min = 160.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = cardPurple), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        when (state) {
            CourseState.Waiting -> StaticCourse(R.string.course_title, R.string.course_waiting)
            CourseState.Unavailable -> StaticCourse(R.string.compass_unavailable, R.string.compass_unavailable_body)
            CourseState.Error -> StaticCourse(R.string.compass_error, R.string.compass_error_body, onRetry)
            is CourseState.Available -> CourseReading(state)
        }
    }
}

@Composable private fun StaticCourse(title: Int, body: Int, retry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text(stringResource(title), color = textColor, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium))
        Text(stringResource(body), Modifier.padding(top = 12.dp), color = textColor, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp))
        if (retry != null) TextButton(retry, Modifier.padding(top = 12.dp), content = { Text(stringResource(R.string.course_try_again), color = actionCyan) })
    }
}

@Composable private fun CourseReading(state: CourseState.Available) {
    val heading = NumberFormat.getIntegerInstance().format(state.headingDegrees)
    val cardinal = compassCardinal(state.headingDegrees)
    val bearing = state.gpsBearingDegrees?.let { NumberFormat.getIntegerInstance().format(it) + "°" } ?: stringResource(R.string.course_unavailable)
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text(stringResource(R.string.course_title), color = textColor, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium))
        Row(Modifier.padding(top = 16.dp).semantics(mergeDescendants = true) { contentDescription = stringResource(R.string.course_heading_spoken, heading, cardinal) }) {
            Text("$heading°", color = textColor, fontSize = 40.sp, fontWeight = FontWeight.Medium)
            Text(cardinal, Modifier.padding(start = 8.dp, top = 14.dp), color = actionCyan, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) { contentDescription = stringResource(R.string.course_bearing_spoken, if (state.gpsBearingDegrees == null) stringResource(R.string.course_unavailable_spoken) else bearing) }, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.course_gps_bearing), Modifier.weight(1f), color = textColor, fontSize = 18.sp)
            Text(bearing, color = textColor, fontSize = 18.sp)
        }
    }
}
