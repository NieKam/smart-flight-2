package kniezrec.com.flightinfo.map.ui

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme

/** Test tag of the max-zoom tip snackbar. */
internal const val MAP_ZOOM_TIP_TAG = "map-zoom-tip"

/**
 * The max-zoom tip ("This is max zoom. Force bigger in settings.") with a "Settings" action, as the
 * original app's top snackbar: shown once each time [requested] turns true, in the snackbar colors
 * (`inverseSurface` container, `inverseOnSurface` text, `inversePrimary` action), so it stands out
 * from the page in every theme.
 *
 * The screen places it at the top, below the toolbar, as the original: there it covers neither the
 * map's own buttons nor the app's bottom snackbars.
 *
 * @param onShown the tip was dismissed, timed out or acted on; called after [onOpenSettings].
 * @param onOpenSettings the "Settings" action was tapped.
 */
@Composable
fun MapZoomTipHost(
    requested: Boolean,
    onShown: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hostState = remember { SnackbarHostState() }
    val message = stringResource(R.string.map_zoom_tip)
    val action = stringResource(R.string.settings_title)
    val currentOnShown by rememberUpdatedState(onShown)
    val currentOnOpenSettings by rememberUpdatedState(onOpenSettings)
    LaunchedEffect(requested) {
        if (!requested) return@LaunchedEffect
        val result = hostState.showSnackbar(message, actionLabel = action, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) currentOnOpenSettings()
        currentOnShown()
    }
    val colors = SmartFlightTheme.colors
    SnackbarHost(hostState, modifier) { data ->
        Snackbar(
            snackbarData = data,
            modifier = Modifier.testTag(MAP_ZOOM_TIP_TAG),
            containerColor = colors.inverseSurface,
            contentColor = colors.inverseOnSurface,
            actionColor = colors.inversePrimary,
        )
    }
}
