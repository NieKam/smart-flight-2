package kniezrec.com.flightinfo.horizon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.horizon.HorizonState
import kniezrec.com.flightinfo.ui.theme.LabelText
import kniezrec.com.flightinfo.ui.theme.MissingSensorPlaceholder
import kniezrec.com.flightinfo.ui.theme.SmartFlightCard
import kniezrec.com.flightinfo.ui.theme.SmartFlightCardDefaults
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme
import kniezrec.com.flightinfo.ui.theme.ValueText
import java.text.NumberFormat

@Composable
internal fun HorizonCard(
    state: HorizonState,
    onCalibrate: () -> Unit,
    onResetToAbsolute: () -> Unit,
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
                HorizonState.Unavailable
            ) {
                SmartFlightCardDefaults.NoPadding
            } else {
                SmartFlightCardDefaults.ContentPadding
            },
    ) {
        HorizonStateAnnouncement(state)
        when (state) {
            HorizonState.Waiting -> HorizonStatic(R.string.horizon_title, R.string.horizon_waiting)
            HorizonState.Recalibrating -> HorizonStatic(R.string.horizon_title, R.string.horizon_waiting)
            // Only a missing sensor offers hiding; a refused registration (Error) can be retried.
            HorizonState.Unavailable ->
                MissingSensorPlaceholder(stringResource(R.string.missing_sensor_horizon), onHide) { HorizonPreview() }
            HorizonState.Error -> HorizonStatic(R.string.horizon_error, R.string.horizon_error_body, onRetry)
            is HorizonState.Available -> HorizonAvailable(state, onCalibrate, onResetToAbsolute)
        }
    }
}

@Composable
private fun HorizonStateAnnouncement(state: HorizonState) {
    var previousState by remember { mutableStateOf<HorizonState?>(null) }
    var announcement by remember { mutableStateOf<String?>(null) }
    val nextAnnouncement =
        when {
            state == HorizonState.Recalibrating -> stringResource(R.string.horizon_recalibrating_announcement)
            state is HorizonState.Available && previousState == HorizonState.Recalibrating ->
                stringResource(
                    R.string.horizon_calibrated_announcement,
                )
            state == HorizonState.Unavailable && previousState != HorizonState.Unavailable ->
                stringResource(
                    R.string.horizon_unavailable_announcement,
                )
            state == HorizonState.Error && previousState != HorizonState.Error -> stringResource(R.string.horizon_error_announcement)
            else -> null
        }
    SideEffect {
        previousState = state
        if (nextAnnouncement != null) announcement = nextAnnouncement
    }
    announcement?.let { text ->
        Box(
            Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = text
            },
        )
    }
}

@Composable
private fun HorizonStatic(
    title: Int,
    body: Int,
    retry: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LabelText(stringResource(title), style = horizonTitle().copy(textAlign = TextAlign.Center))
        ValueText(
            stringResource(body),
            Modifier.padding(top = 12.dp),
            style = horizonBody().copy(textAlign = TextAlign.Center),
        )
        if (retry != null) HorizonAction(R.string.horizon_try_again, R.string.horizon_try_again_hint, retry)
    }
}

@Composable
private fun HorizonAvailable(
    state: HorizonState.Available,
    onCalibrate: () -> Unit,
    onResetToAbsolute: () -> Unit,
) {
    val pitch = attitudeValue(state.pitchDegrees, R.string.horizon_up, R.string.horizon_down)
    val roll = attitudeValue(state.rollDegrees, R.string.horizon_right, R.string.horizon_left)
    val summary = stringResource(R.string.horizon_summary, pitch, roll)
    val spoken = stringResource(R.string.horizon_summary_spoken, pitch, roll)
    Column(Modifier.fillMaxWidth()) {
        LabelText(stringResource(R.string.horizon_title), style = horizonTitle())
        ValueText(
            summary,
            Modifier.padding(top = 12.dp).semantics(mergeDescendants = true) { contentDescription = spoken },
            style = horizonBody(),
        )
        HorizonInstrument(state, Modifier.padding(top = 12.dp).fillMaxWidth())
        Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
            // Long-press (or the "Reset to level" accessibility action) shows the absolute pitch.
            HorizonAction(
                R.string.horizon_calibrate,
                R.string.horizon_calibrate_hint,
                onCalibrate,
                longClickLabel = R.string.horizon_reset_to_level,
                onLongClick = onResetToAbsolute,
            )
        }
    }
}

/** Static, level instrument behind the missing-sensor overlay. */
@Composable
private fun HorizonPreview() {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {
        LabelText(stringResource(R.string.horizon_title), style = horizonTitle())
        HorizonInstrument(HorizonState.Available(0, 0, 0f, 0f), Modifier.padding(top = 12.dp).fillMaxWidth())
    }
}

@Composable
private fun attitudeValue(
    value: Int,
    positive: Int,
    negative: Int,
): String =
    when {
        value == 0 -> stringResource(R.string.horizon_level)
        value > 0 ->
            stringResource(
                R.string.horizon_degrees_direction,
                NumberFormat.getIntegerInstance().format(value),
                stringResource(positive),
            )
        else ->
            stringResource(
                R.string.horizon_degrees_direction,
                NumberFormat.getIntegerInstance().format(-value),
                stringResource(negative),
            )
    }

@Composable
private fun HorizonInstrument(
    state: HorizonState.Available,
    modifier: Modifier,
) {
    val horizonText = SmartFlightTheme.colors.horizonLine
    val sky = SmartFlightTheme.colors.horizonSky
    val ground = SmartFlightTheme.colors.horizonGround
    Box(
        modifier.heightIn(min = 160.dp, max = 200.dp).clip(
            androidx.compose.foundation.shape
                .RoundedCornerShape(6.dp),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier.matchParentSize().graphicsLayer {
                translationY = state.verticalOffsetFraction * size.height
                rotationZ = state.visualRollDegrees
            },
        ) {
            drawRect(
                sky,
                topLeft = Offset(-size.width, -size.height),
                size =
                    androidx.compose.ui.geometry.Size(
                        size.width * 3,
                        size.height * 1.5f,
                    ),
            )
            drawRect(
                ground,
                topLeft = Offset(-size.width, size.height / 2),
                size =
                    androidx.compose.ui.geometry.Size(
                        size.width * 3,
                        size.height * 2,
                    ),
            )
            drawLine(horizonText, Offset(-size.width, size.height / 2), Offset(size.width * 2, size.height / 2), 1.dp.toPx())
            val tick = horizonText.copy(alpha = .7f)
            for (index in -3..3) {
                val y = size.height / 2 + index * size.height / 9
                val length = if (index == 0) size.width * .13f else size.width * .08f
                drawLine(tick, Offset(size.width * .05f, y), Offset(size.width * .05f + length, y), 2.dp.toPx())
                drawLine(tick, Offset(size.width * .95f - length, y), Offset(size.width * .95f, y), 2.dp.toPx())
            }
        }
        Canvas(Modifier.matchParentSize()) {
            val y = size.height / 2
            val center = Offset(size.width / 2, y)
            drawCircle(horizonText, 3.dp.toPx(), center)
            drawLine(horizonText, Offset(size.width * .27f, y), Offset(size.width * .45f, y), 3.dp.toPx())
            drawLine(horizonText, Offset(size.width * .55f, y), Offset(size.width * .73f, y), 3.dp.toPx())
            drawLine(horizonText, Offset(size.width * .45f, y), Offset(size.width * .49f, y + 6.dp.toPx()), 2.dp.toPx())
            drawLine(horizonText, Offset(size.width * .55f, y), Offset(size.width * .51f, y + 6.dp.toPx()), 2.dp.toPx())
        }
    }
}

@Composable
private fun HorizonAction(
    label: Int,
    hint: Int,
    callback: () -> Unit,
    longClickLabel: Int? = null,
    onLongClick: (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val actionHint = stringResource(hint)
    val longClickText = longClickLabel?.let { stringResource(it) }
    // A text button that also takes a long press (TextButton has no long click).
    Box(
        modifier =
            Modifier
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
                .clip(ButtonDefaults.textShape)
                .combinedClickable(
                    role = Role.Button,
                    onLongClickLabel = longClickText,
                    onLongClick = onLongClick,
                    onClick = callback,
                ).semantics {
                    stateDescription = actionHint
                    if (onLongClick != null && longClickText != null) {
                        customActions =
                            listOf(
                                CustomAccessibilityAction(longClickText) {
                                    onLongClick()
                                    true
                                },
                            )
                    }
                }.padding(ButtonDefaults.TextButtonContentPadding),
        contentAlignment = Alignment.Center,
    ) { Text(stringResource(label), color = SmartFlightTheme.colors.accent, style = horizonBody().copy(fontWeight = FontWeight.Medium)) }
}

@Composable
private fun horizonTitle() = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)

@Composable
private fun horizonBody() = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 25.sp)

@Preview(widthDp = 411)
@Composable
private fun HorizonCardMissingSensorPreview() {
    SmartFlightTheme { HorizonCard(HorizonState.Unavailable, onCalibrate = {}, onResetToAbsolute = {}, onRetry = {}, onHide = {}) }
}
