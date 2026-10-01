package kniezrec.com.flightinfo.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kniezrec.com.flightinfo.R

/**
 * The original "missing sensor" overlay (`card_overlay_layout.xml`): a blurred, dimmed static
 * [preview] of the card's instrument with [message] ("This device doesn't have … Hide this card?")
 * and a "Hide" button. The preview is decoration only: it has no semantics and takes no input.
 */
@Composable
fun MissingSensorPlaceholder(
    message: String,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    val colors = SmartFlightTheme.colors
    Box(modifier.fillMaxWidth().testTag(MISSING_SENSOR_PLACEHOLDER_TAG)) {
        Box(
            Modifier
                .matchParentSize()
                .blur(PREVIEW_BLUR_RADIUS)
                .clearAndSetSemantics {},
        ) { preview() }
        Box(Modifier.matchParentSize().background(colors.overlay50))
        // Sizes the card: message in the upper part, the button at the bottom end (as the original).
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = PLACEHOLDER_MIN_HEIGHT)
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            ValueText(
                message,
                Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 16.dp),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 25.sp),
                textAlign = TextAlign.Center,
            )
            val hint = stringResource(R.string.hide_card_hint)
            OutlinedButton(
                onClick = onHide,
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .heightIn(min = 48.dp)
                        .semantics { stateDescription = hint },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, colors.valueText),
            ) { Text(stringResource(R.string.hide_card), color = colors.accent) }
        }
    }
}

/** Test tag of [MissingSensorPlaceholder]. */
const val MISSING_SENSOR_PLACEHOLDER_TAG = "missing-sensor-placeholder"

private val PREVIEW_BLUR_RADIUS = 8.dp
private val PLACEHOLDER_MIN_HEIGHT = 200.dp
