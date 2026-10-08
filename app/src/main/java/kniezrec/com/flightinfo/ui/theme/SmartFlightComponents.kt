package kniezrec.com.flightinfo.ui.theme

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * An [AlertDialog] on the raised surface ([SmartFlightColors.raised], Material
 * `surfaceContainerHigh`, one tone above the cards): muted title, value-colored content; buttons use
 * the accent (the scheme's primary).
 */
@Composable
fun SmartFlightAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val colors = SmartFlightTheme.colors
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = title,
        text = text,
        containerColor = colors.raised,
        iconContentColor = colors.labelText,
        titleContentColor = colors.labelText,
        textContentColor = colors.valueText,
    )
}

/** Filled buttons as in the original picker: card container, light label, muted when disabled. */
@Composable
fun smartFlightButtonColors(): ButtonColors {
    val colors = SmartFlightTheme.colors
    return ButtonDefaults.buttonColors(
        containerColor = colors.card,
        contentColor = colors.valueText,
        disabledContainerColor = colors.card,
        disabledContentColor = colors.labelText,
    )
}

/** Text fields on the page: transparent container, light text, accent indicator and cursor. */
@Composable
fun smartFlightTextFieldColors(): TextFieldColors {
    val colors = SmartFlightTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.valueText,
        unfocusedTextColor = colors.valueText,
        disabledTextColor = colors.labelText,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        cursorColor = colors.accent,
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.labelText,
        disabledBorderColor = colors.labelText,
        focusedLabelColor = colors.accent,
        unfocusedLabelColor = colors.labelText,
        disabledLabelColor = colors.labelText,
        focusedPlaceholderColor = colors.labelText,
        unfocusedPlaceholderColor = colors.labelText,
        disabledPlaceholderColor = colors.labelText,
    )
}

/**
 * Material 3 switches of the redesign (TASK-048): checked, an [SmartFlightColors.onAccent] thumb on
 * an accent track; unchecked, a muted thumb and border on a [SmartFlightColors.cardOutline] track.
 */
@Composable
fun smartFlightSwitchColors(): SwitchColors {
    val colors = SmartFlightTheme.colors
    return SwitchDefaults.colors(
        checkedThumbColor = colors.onAccent,
        checkedTrackColor = colors.accent,
        checkedBorderColor = Color.Transparent,
        uncheckedThumbColor = colors.labelText,
        uncheckedTrackColor = colors.cardOutline,
        uncheckedBorderColor = colors.labelText,
    )
}

/** Radio buttons: accent when selected, muted otherwise. */
@Composable
fun smartFlightRadioButtonColors(): RadioButtonColors {
    val colors = SmartFlightTheme.colors
    return RadioButtonDefaults.colors(
        selectedColor = colors.accent,
        unselectedColor = colors.labelText,
    )
}
