package kniezrec.com.flightinfo.ui.theme

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * A muted label (`TextLabel` of the original app): row labels, card titles and secondary text in
 * [SmartFlightColors.labelText].
 */
@Composable
fun LabelText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = SmartFlightTheme.colors.labelText,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        style = style,
    )
}

/**
 * A value (`TextValue` of the original app): values, body text and primary text in
 * [SmartFlightColors.valueText].
 */
@Composable
fun ValueText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = SmartFlightTheme.colors.valueText,
        fontSize = fontSize,
        fontWeight = fontWeight,
        textAlign = textAlign,
        style = style,
    )
}

/** A value with styled parts, such as a smaller unit ([withSmallerUnit]), in [SmartFlightColors.valueText]. */
@Composable
fun ValueText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        color = SmartFlightTheme.colors.valueText,
        textAlign = textAlign,
        style = style,
    )
}

/**
 * [text] with its [unit] (the last occurrence, as the value templates put the unit last) drawn at
 * [UNIT_RELATIVE_SIZE] of the number and in normal weight: "**12 500** ft". Text without the unit
 * (for example "—") is returned unstyled. Only the look changes: the text itself is unchanged.
 */
fun withSmallerUnit(
    text: String,
    unit: String,
): AnnotatedString {
    val start = if (unit.isBlank()) -1 else text.lastIndexOf(unit)
    return buildAnnotatedString {
        append(text)
        if (start >= 0) {
            addStyle(SpanStyle(fontSize = UNIT_RELATIVE_SIZE, fontWeight = FontWeight.Normal), start, start + unit.length)
        }
    }
}

/**
 * A card row: the muted [label] ([androidx.compose.material3.Typography.labelLarge]) at the start
 * and the [value] in [valueStyle] at the end, sharing a baseline. The label wraps and the value
 * never takes more than [VALUE_MAX_WIDTH_FRACTION] of the row; at a large font scale the value goes
 * below the label, so neither is clipped. [modifier] carries the row's semantics and touch height.
 */
@Composable
fun LabelValueRow(
    label: String,
    value: AnnotatedString,
    valueStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val labelStyle = MaterialTheme.typography.labelLarge
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        if (LocalDensity.current.fontScale > STACKED_FONT_SCALE) {
            // Each on full-width lines of its own: both wrap instead of being cut off.
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                LabelText(label, Modifier.fillMaxWidth(), style = labelStyle)
                ValueText(value, Modifier.fillMaxWidth(), style = valueStyle)
            }
        } else {
            val valueMaxWidth = maxWidth * VALUE_MAX_WIDTH_FRACTION
            Row(Modifier.fillMaxWidth()) {
                LabelText(label, Modifier.weight(1f).padding(end = 12.dp).alignByBaseline(), style = labelStyle)
                ValueText(
                    value,
                    Modifier.widthIn(max = valueMaxWidth).alignByBaseline(),
                    style = valueStyle,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** Above this font scale [LabelValueRow] stacks the value below its label. */
private const val STACKED_FONT_SCALE = 1.3f

/** Share of the row a value may take next to its label. */
private const val VALUE_MAX_WIDTH_FRACTION = 0.65f
