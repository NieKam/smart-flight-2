package kniezrec.com.flightinfo.ui.gnss

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.course.CompassCardinal
import kniezrec.com.flightinfo.course.CourseState
import kniezrec.com.flightinfo.course.compassCardinal
import kniezrec.com.flightinfo.ui.permission.actionCyan
import kniezrec.com.flightinfo.ui.permission.cardPurple
import java.text.NumberFormat

@Composable
internal fun CourseCard(state: CourseState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier.fillMaxWidth().heightIn(min = 160.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cardPurple),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        CourseStateAnnouncement(state)
        when (state) {
            CourseState.Waiting -> StaticCourse(R.string.course_title, R.string.course_waiting)
            CourseState.Unavailable -> StaticCourse(R.string.compass_unavailable, R.string.compass_unavailable_body)
            CourseState.Error -> StaticCourse(R.string.compass_error, R.string.compass_error_body, onRetry)
            is CourseState.Available -> CourseReading(state)
        }
    }
}

@Composable
private fun StaticCourse(title: Int, body: Int, retry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        Arrangement.Center,
        Alignment.CenterHorizontally,
    ) {
        Text(stringResource(title), color = textColor, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium))
        Text(stringResource(body), Modifier.padding(top = 12.dp), color = textColor, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp))
        if (retry != null) CourseRetryAction(retry)
    }
}

@Composable
private fun CourseReading(state: CourseState.Available) {
    val heading = NumberFormat.getIntegerInstance().format(state.headingDegrees)
    val headingValue = stringResource(R.string.course_degree_value, heading)
    val cardinal = compassCardinal(state.headingDegrees)
    val cardinalValue = stringResource(cardinal.displayResource())
    val cardinalSpoken = stringResource(cardinal.spokenResource())
    val bearing = state.gpsBearingDegrees?.let { stringResource(R.string.course_degree_value, NumberFormat.getIntegerInstance().format(it)) } ?: stringResource(R.string.course_unavailable)
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        Text(stringResource(R.string.course_title), color = textColor, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Medium))
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            if (maxWidth >= 360.dp && LocalDensity.current.fontScale <= 1.3f) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HeadingValue(heading, headingValue, cardinalValue, cardinalSpoken, Modifier.weight(1f))
                    CompassDirectionVisual(state.headingDegrees)
                }
            } else {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                    HeadingValue(heading, headingValue, cardinalValue, cardinalSpoken)
                    Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) { CompassDirectionVisual(state.headingDegrees) }
                }
            }
        }
        GpsBearing(state.gpsBearingDegrees, bearing)
    }
}

@Composable
private fun HeadingValue(heading: String, headingValue: String, cardinalValue: String, cardinalSpoken: String, modifier: Modifier = Modifier) {
    Row(modifier.testTag("course-heading").semantics(mergeDescendants = true) { contentDescription = stringResource(R.string.course_heading_spoken, heading, cardinalSpoken) }) {
        Text(headingValue, color = textColor, fontSize = 40.sp, fontWeight = FontWeight.Medium)
        Text(cardinalValue, Modifier.padding(start = 8.dp, top = 14.dp), color = actionCyan, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GpsBearing(bearingDegrees: Int?, bearing: String) {
    val spokenBearing = if (bearingDegrees == null) {
        stringResource(R.string.course_unavailable_spoken)
    } else {
        bearing
    }
    val contentDescription = stringResource(R.string.course_bearing_spoken, spokenBearing)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) { this.contentDescription = contentDescription }, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.course_gps_bearing), Modifier.weight(1f), color = textColor, fontSize = 18.sp)
        Text(bearing, color = textColor, fontSize = 18.sp)
    }
}

@Composable
private fun CourseStateAnnouncement(state: CourseState) {
    val announcement = when (state) {
        CourseState.Waiting -> stringResource(R.string.course_waiting)
        CourseState.Unavailable -> stringResource(R.string.compass_unavailable)
        CourseState.Error -> stringResource(R.string.compass_error)
        is CourseState.Available -> stringResource(R.string.course_available_announcement)
    }
    Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite; contentDescription = announcement })
}

private fun CompassCardinal.displayResource(): Int = when (this) {
    CompassCardinal.North -> R.string.course_cardinal_north
    CompassCardinal.NorthEast -> R.string.course_cardinal_north_east
    CompassCardinal.East -> R.string.course_cardinal_east
    CompassCardinal.SouthEast -> R.string.course_cardinal_south_east
    CompassCardinal.South -> R.string.course_cardinal_south
    CompassCardinal.SouthWest -> R.string.course_cardinal_south_west
    CompassCardinal.West -> R.string.course_cardinal_west
    CompassCardinal.NorthWest -> R.string.course_cardinal_north_west
}

private fun CompassCardinal.spokenResource(): Int = when (this) {
    CompassCardinal.North -> R.string.course_cardinal_north_spoken
    CompassCardinal.NorthEast -> R.string.course_cardinal_north_east_spoken
    CompassCardinal.East -> R.string.course_cardinal_east_spoken
    CompassCardinal.SouthEast -> R.string.course_cardinal_south_east_spoken
    CompassCardinal.South -> R.string.course_cardinal_south_spoken
    CompassCardinal.SouthWest -> R.string.course_cardinal_south_west_spoken
    CompassCardinal.West -> R.string.course_cardinal_west_spoken
    CompassCardinal.NorthWest -> R.string.course_cardinal_north_west_spoken
}

@Composable
private fun CompassDirectionVisual(headingDegrees: Int) {
    Box(Modifier.size(72.dp).testTag("course-direction-visual").semantics { contentDescription = null }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(textColor.copy(alpha = 0.45f), size.minDimension / 2f, style = Stroke(width = 2.dp.toPx()))
        }
        Canvas(Modifier.size(40.dp).graphicsLayer { rotationZ = headingDegrees.toFloat() }) {
            val centerX = size.width / 2f
            drawLine(textColor, Offset(centerX, size.height * .82f), Offset(centerX, size.height * .18f), strokeWidth = 5.dp.toPx())
            drawLine(textColor, Offset(centerX, size.height * .18f), Offset(size.width * .3f, size.height * .43f), strokeWidth = 5.dp.toPx())
            drawLine(textColor, Offset(centerX, size.height * .18f), Offset(size.width * .7f, size.height * .43f), strokeWidth = 5.dp.toPx())
        }
    }
}

@Composable
private fun CourseRetryAction(onRetry: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val hint = stringResource(R.string.course_try_again_hint)
    TextButton(
        onClick = onRetry,
        modifier = Modifier.padding(top = 12.dp).sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .then(if (focused) Modifier.border(2.dp, actionCyan, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .semantics { role = Role.Button; stateDescription = hint },
    ) {
        Text(stringResource(R.string.course_try_again), color = actionCyan)
    }
}
