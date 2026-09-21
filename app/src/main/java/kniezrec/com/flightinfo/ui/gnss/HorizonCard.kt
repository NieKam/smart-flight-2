package kniezrec.com.flightinfo.ui.gnss

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.horizon.HorizonState
import kniezrec.com.flightinfo.ui.permission.actionCyan
import kniezrec.com.flightinfo.ui.permission.cardPurple
import java.text.NumberFormat

@Composable
internal fun HorizonCard(
    state: HorizonState,
    onCalibrate: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier.fillMaxWidth().heightIn(min = 160.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cardPurple),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        when (state) {
            HorizonState.Waiting -> HorizonStatic(R.string.horizon_title, R.string.horizon_waiting)
            HorizonState.Unavailable -> HorizonStatic(R.string.horizon_unavailable, R.string.horizon_unavailable_body)
            HorizonState.Error -> HorizonStatic(R.string.horizon_error, R.string.horizon_error_body, onRetry)
            is HorizonState.Available -> HorizonAvailable(state, onCalibrate)
        }
    }
}

@Composable
private fun HorizonStatic(title: Int, body: Int, retry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(title), color = horizonText, style = horizonTitle().copy(textAlign = TextAlign.Center))
        Text(
            stringResource(body),
            Modifier.padding(top = 12.dp),
            color = horizonText,
            style = horizonBody().copy(textAlign = TextAlign.Center),
        )
        if (retry != null) HorizonAction(R.string.horizon_try_again, R.string.horizon_try_again_hint, retry)
    }
}

@Composable
private fun HorizonAvailable(state: HorizonState.Available, onCalibrate: () -> Unit) {
    val pitch = attitudeValue(state.pitchDegrees, R.string.horizon_up, R.string.horizon_down)
    val roll = attitudeValue(state.rollDegrees, R.string.horizon_right, R.string.horizon_left)
    val summary = stringResource(R.string.horizon_summary, pitch, roll)
    val spoken = stringResource(R.string.horizon_summary_spoken, pitch, roll)
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
        Text(stringResource(R.string.horizon_title), color = horizonText, style = horizonTitle())
        Text(
            summary,
            Modifier.padding(top = 12.dp).semantics(mergeDescendants = true) { contentDescription = spoken },
            color = horizonText,
            style = horizonBody(),
        )
        HorizonInstrument(state, Modifier.padding(top = 12.dp).fillMaxWidth())
        Box(Modifier.fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.Center) {
            HorizonAction(R.string.horizon_calibrate, R.string.horizon_calibrate_hint, onCalibrate)
        }
    }
}

@Composable
private fun attitudeValue(value: Int, positive: Int, negative: Int): String =
    when {
        value == 0 -> stringResource(R.string.horizon_level)
        value > 0 -> stringResource(R.string.horizon_degrees_direction, NumberFormat.getIntegerInstance().format(value), stringResource(positive))
        else -> stringResource(R.string.horizon_degrees_direction, NumberFormat.getIntegerInstance().format(-value), stringResource(negative))
    }

@Composable
private fun HorizonInstrument(state: HorizonState.Available, modifier: Modifier) {
    Box(
        modifier.heightIn(min = 160.dp, max = 200.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize().graphicsLayer {
            translationY = state.verticalOffsetFraction * size.height
            rotationZ = state.visualRollDegrees
        }) {
            val sky = Color(0xFF7775B5)
            val ground = Color(0xFF3F3D70)
            drawRect(sky, topLeft = Offset(-size.width, -size.height), size = androidx.compose.ui.geometry.Size(size.width * 3, size.height * 1.5f))
            drawRect(ground, topLeft = Offset(-size.width, size.height / 2), size = androidx.compose.ui.geometry.Size(size.width * 3, size.height * 2))
            drawLine(horizonText, Offset(-size.width, size.height / 2), Offset(size.width * 2, size.height / 2), 1.dp.toPx())
            val tick = horizonText.copy(alpha = .7f)
            for (index in -3..3) {
                val y = size.height / 2 + index * size.height / 9
                val length = if (index == 0) size.width * .13f else size.width * .08f
                drawLine(tick, Offset(size.width * .05f, y), Offset(size.width * .05f + length, y), 2.dp.toPx())
                drawLine(tick, Offset(size.width * .95f - length, y), Offset(size.width * .95f, y), 2.dp.toPx())
            }
        })
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
private fun HorizonAction(label: Int, hint: Int, callback: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val actionHint = stringResource(hint)
    TextButton(
        onClick = callback,
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .then(if (focused) Modifier.border(2.dp, actionCyan, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .semantics { role = Role.Button; stateDescription = actionHint },
    ) { Text(stringResource(label), color = actionCyan, style = horizonBody().copy(fontWeight = FontWeight.Medium)) }
}

private val horizonText = Color(0xFFD9D9ED)

@Composable
private fun horizonTitle() = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)

@Composable
private fun horizonBody() = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 25.sp)
