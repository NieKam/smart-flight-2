package kniezrec.com.flightinfo.course.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.course.CompassCardinal
import kniezrec.com.flightinfo.course.CourseState
import kniezrec.com.flightinfo.course.compassCardinal
import kniezrec.com.flightinfo.course.shortestRotationTarget
import kniezrec.com.flightinfo.ui.theme.CardHeader
import kniezrec.com.flightinfo.ui.theme.LabelText
import kniezrec.com.flightinfo.ui.theme.LabelValueRow
import kniezrec.com.flightinfo.ui.theme.MissingSensorPlaceholder
import kniezrec.com.flightinfo.ui.theme.SmartFlightCard
import kniezrec.com.flightinfo.ui.theme.SmartFlightCardDefaults
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme
import kniezrec.com.flightinfo.ui.theme.ValueText
import java.text.NumberFormat
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun CourseCard(
    state: CourseState,
    onRetry: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SmartFlightCard(
        modifier,
        minHeight = SmartFlightCardDefaults.MinHeight,
        // The missing-sensor overlay covers the whole card.
        contentPadding =
            if (state ==
                CourseState.Unavailable
            ) {
                SmartFlightCardDefaults.NoPadding
            } else {
                SmartFlightCardDefaults.ContentPadding
            },
    ) {
        CourseStateAnnouncement(state)
        when (state) {
            CourseState.Waiting -> StaticCourse(R.string.course_title, R.string.course_waiting)
            // Only a missing sensor offers hiding; a refused registration (Error) can be retried.
            CourseState.Unavailable ->
                MissingSensorPlaceholder(stringResource(R.string.missing_sensor_course), onHide) { CoursePreview() }
            CourseState.Error -> StaticCourse(R.string.compass_error, R.string.compass_error_body, onRetry)
            is CourseState.Available -> CourseReading(state)
        }
    }
}

@Composable
private fun StaticCourse(
    title: Int,
    body: Int,
    retry: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        Arrangement.Center,
        Alignment.CenterHorizontally,
    ) {
        CardHeader(R.drawable.ic_card_course, stringResource(title))
        ValueText(
            stringResource(body),
            Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
        )
        if (retry != null) CourseRetryAction(retry)
    }
}

/** Static, level compass rose behind the missing-sensor overlay. */
@Composable
private fun CoursePreview() {
    Box(Modifier.fillMaxSize().padding(24.dp)) {
        CardHeader(R.drawable.ic_card_course, stringResource(R.string.course_title), Modifier.align(Alignment.TopStart))
        Box(Modifier.align(Alignment.Center)) { CompassRose(0) }
    }
}

@Composable
private fun CourseReading(state: CourseState.Available) {
    val heading = NumberFormat.getIntegerInstance().format(state.headingDegrees)
    val headingValue = stringResource(R.string.course_degree_value, heading)
    val cardinal = compassCardinal(state.headingDegrees)
    val cardinalValue = stringResource(cardinal.displayResource())
    val cardinalSpoken = stringResource(cardinal.spokenResource())
    val bearing =
        state.gpsBearingDegrees?.let { stringResource(R.string.course_degree_value, NumberFormat.getIntegerInstance().format(it)) }
            ?: stringResource(R.string.course_unavailable)
    Column(Modifier.fillMaxWidth()) {
        CardHeader(R.drawable.ic_card_course, stringResource(R.string.course_title))
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            if (maxWidth >= 360.dp && LocalDensity.current.fontScale <= 1.3f) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HeadingValue(state.headingDegrees, heading, headingValue, cardinalValue, cardinalSpoken, Modifier.weight(1f))
                    CompassRose(state.headingDegrees)
                }
            } else {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                    HeadingValue(state.headingDegrees, heading, headingValue, cardinalValue, cardinalSpoken)
                    Box(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { CompassRose(state.headingDegrees) }
                }
            }
        }
        GpsBearing(state.gpsBearingDegrees, bearing)
    }
}

@Composable
private fun HeadingValue(
    headingDegrees: Int,
    heading: String,
    headingValue: String,
    cardinalValue: String,
    cardinalSpoken: String,
    modifier: Modifier = Modifier,
) {
    // A plural: the Polish word for "degrees" depends on the number (stopień, stopnie, stopni).
    val contentDescription = pluralStringResource(R.plurals.course_heading_spoken, headingDegrees, heading, cardinalSpoken)
    Column(
        modifier.testTag("course-heading").semantics(mergeDescendants = true) {
            this.contentDescription = contentDescription
        },
    ) {
        // As the original: the cyan abbreviation above the large heading.
        Text(cardinalValue, color = SmartFlightTheme.colors.accent, style = MaterialTheme.typography.titleMedium)
        // The key value: display style with tabular figures, so the digits do not jump while turning.
        ValueText(headingValue, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun GpsBearing(
    bearingDegrees: Int?,
    bearing: String,
) {
    val spokenBearing =
        if (bearingDegrees == null) {
            stringResource(R.string.course_unavailable_spoken)
        } else {
            bearing
        }
    val contentDescription = stringResource(R.string.course_bearing_spoken, spokenBearing)
    LabelValueRow(
        label = stringResource(R.string.course_gps_bearing),
        value = AnnotatedString(bearing),
        valueStyle = MaterialTheme.typography.titleMedium,
        modifier =
            Modifier.heightIn(min = 48.dp).semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
            },
    )
}

@Composable
private fun CourseStateAnnouncement(state: CourseState) {
    val announcement =
        when (state) {
            CourseState.Waiting -> stringResource(R.string.course_waiting)
            CourseState.Unavailable -> stringResource(R.string.compass_unavailable)
            CourseState.Error -> stringResource(R.string.compass_error)
            is CourseState.Available -> stringResource(R.string.course_available_announcement)
        }
    Box(
        Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = announcement
        },
    )
}

private fun CompassCardinal.displayResource(): Int =
    when (this) {
        CompassCardinal.North -> R.string.course_cardinal_north
        CompassCardinal.NorthEast -> R.string.course_cardinal_north_east
        CompassCardinal.East -> R.string.course_cardinal_east
        CompassCardinal.SouthEast -> R.string.course_cardinal_south_east
        CompassCardinal.South -> R.string.course_cardinal_south
        CompassCardinal.SouthWest -> R.string.course_cardinal_south_west
        CompassCardinal.West -> R.string.course_cardinal_west
        CompassCardinal.NorthWest -> R.string.course_cardinal_north_west
    }

private fun CompassCardinal.spokenResource(): Int =
    when (this) {
        CompassCardinal.North -> R.string.course_cardinal_north_spoken
        CompassCardinal.NorthEast -> R.string.course_cardinal_north_east_spoken
        CompassCardinal.East -> R.string.course_cardinal_east_spoken
        CompassCardinal.SouthEast -> R.string.course_cardinal_south_east_spoken
        CompassCardinal.South -> R.string.course_cardinal_south_spoken
        CompassCardinal.SouthWest -> R.string.course_cardinal_south_west_spoken
        CompassCardinal.West -> R.string.course_cardinal_west_spoken
        CompassCardinal.NorthWest -> R.string.course_cardinal_north_west_spoken
    }

/**
 * The compass dial of the redesign (TASK-041): a round face with an outline ring, ticks every 10°
 * (long ones at N, E, S and W), the four letters inside the ring and the airplane in the center,
 * which turns to the heading with a short linear animation the short way round
 * ([shortestRotationTarget]). Decorative: the heading text carries the description. The rotation
 * is applied in the draw phase only.
 */
@Composable
private fun CompassRose(headingDegrees: Int) {
    var target by remember { mutableFloatStateOf(headingDegrees.toFloat()) }
    LaunchedEffect(headingDegrees) { target = shortestRotationTarget(target, headingDegrees.toFloat()) }
    val rotation = animateFloatAsState(target, tween(PLANE_ROTATION_MILLIS, easing = LinearEasing), label = "plane rotation")
    val colors = SmartFlightTheme.colors
    Box(
        Modifier.size(COMPASS_ROSE_SIZE).testTag("course-direction-visual").semantics { hideFromAccessibility() },
        contentAlignment = Alignment.Center,
    ) {
        CompassDial(colors.card, colors.cardOutline, colors.labelText, Modifier.matchParentSize())
        val letterInset = Modifier.padding(DIAL_LETTER_INSET)
        CompassLetter(R.string.course_cardinal_north, letterInset.align(Alignment.TopCenter))
        CompassLetter(R.string.course_cardinal_east, letterInset.align(Alignment.CenterEnd))
        CompassLetter(R.string.course_cardinal_south, letterInset.align(Alignment.BottomCenter))
        CompassLetter(R.string.course_cardinal_west, letterInset.align(Alignment.CenterStart))
        Icon(
            painterResource(R.drawable.ic_plane),
            contentDescription = null,
            modifier = Modifier.size(PLANE_SIZE).testTag("course-plane").graphicsLayer { rotationZ = rotation.value },
            tint = colors.compassPlane,
        )
    }
}

/** The dial's face in [face], its [ring] and the ticks in [tick]: every 10°, long at the cardinals. */
@Composable
private fun CompassDial(
    face: Color,
    ring: Color,
    tick: Color,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val radius = size.minDimension / 2
        val ringWidth = DIAL_RING_WIDTH.toPx()
        drawCircle(face, radius)
        drawCircle(ring, radius - ringWidth / 2, style = Stroke(ringWidth))
        for (degrees in 0 until 360 step 10) {
            val cardinal = degrees % 90 == 0
            val length = (if (cardinal) DIAL_CARDINAL_TICK else DIAL_MINOR_TICK).toPx()
            val outer = radius - if (cardinal) 0f else (ringWidth - length) / 2
            val angle = Math.toRadians(degrees.toDouble())
            val direction = Offset(sin(angle).toFloat(), -cos(angle).toFloat())
            drawLine(
                if (cardinal) tick else tick.copy(alpha = MINOR_TICK_ALPHA),
                center + direction * outer,
                center + direction * (outer - length),
                strokeWidth = (if (cardinal) 2.dp else 1.dp).toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

/** A letter of the dial (20sp, muted). */
@Composable
private fun CompassLetter(
    letter: Int,
    modifier: Modifier,
) {
    LabelText(stringResource(letter), modifier, fontSize = 20.sp, fontWeight = FontWeight.Medium)
}

private val COMPASS_ROSE_SIZE = 156.dp
private val PLANE_SIZE = 64.dp
private val DIAL_RING_WIDTH = 10.dp
private val DIAL_CARDINAL_TICK = 16.dp
private val DIAL_MINOR_TICK = 5.dp
private val DIAL_LETTER_INSET = 22.dp
private const val MINOR_TICK_ALPHA = 0.6f
private const val PLANE_ROTATION_MILLIS = 200

@Composable
private fun CourseRetryAction(onRetry: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val hint = stringResource(R.string.course_try_again_hint)
    TextButton(
        onClick = onRetry,
        modifier =
            Modifier
                .padding(top = 12.dp)
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .then(
                    if (focused) {
                        Modifier.border(
                            2.dp,
                            SmartFlightTheme.colors.accent,
                            androidx.compose.foundation.shape
                                .RoundedCornerShape(4.dp),
                        )
                    } else {
                        Modifier
                    },
                ).onFocusChanged { focused = it.isFocused }
                .semantics {
                    role = Role.Button
                    stateDescription = hint
                },
    ) {
        Text(stringResource(R.string.course_try_again), color = SmartFlightTheme.colors.accent)
    }
}

@Preview(widthDp = 411)
@Composable
private fun CourseCardMissingSensorPreview() {
    SmartFlightTheme { CourseCard(CourseState.Unavailable, onRetry = {}, onHide = {}) }
}
