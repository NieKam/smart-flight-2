package kniezrec.com.flightinfo.dashboard.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.ui.theme.SmartFlightAlertDialog

/**
 * The original "Enable GPS" prompt: shown when the Activity starts with GPS off. The start that
 * follows a configuration change is not a new start, so rotating does not show it again (an open
 * prompt stays open). GPS switched off while the Activity is started does not show it; the GNSS
 * card shows that state.
 *
 * @param isLocationEnabled a fresh read of the GPS state.
 * @param onOpenLocationSettings "Yes": opens the system location settings.
 */
@Composable
internal fun EnableGpsPrompt(
    isLocationEnabled: () -> Boolean,
    onOpenLocationSettings: () -> Unit,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    var stoppedForConfigurationChange by rememberSaveable { mutableStateOf(false) }
    val activity = LocalActivity.current
    LifecycleStartEffect(Unit) {
        if (stoppedForConfigurationChange) {
            stoppedForConfigurationChange = false
        } else if (!isLocationEnabled()) {
            visible = true
        }
        // Saved with the instance state, which Android writes after onStop.
        onStopOrDispose { stoppedForConfigurationChange = activity?.isChangingConfigurations == true }
    }
    if (visible) {
        EnableGpsDialog(
            onConfirm = {
                visible = false
                onOpenLocationSettings()
            },
            onDismiss = { visible = false },
        )
    }
}

@Composable
internal fun EnableGpsDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    SmartFlightAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.enable_gps_title)) },
        text = { Text(stringResource(R.string.enable_gps_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.yes)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.no)) }
        },
    )
}
