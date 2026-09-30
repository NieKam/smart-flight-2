package kniezrec.com.flightinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kniezrec.com.flightinfo.about.AppVersion
import kniezrec.com.flightinfo.dashboard.ui.DashboardScreen
import kniezrec.com.flightinfo.permission.LocationPermissionState
import kniezrec.com.flightinfo.permission.ui.LocationPermissionViewModel
import kniezrec.com.flightinfo.permission.ui.PermissionOnboardingScreen
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme
import kotlinx.coroutines.launch

/**
 * The app's single screen: the permission onboarding until location is granted, then the
 * dashboard. The Activity supplies the Android-bound actions; each returns false when the system
 * could not open what was asked, which is reported in a snackbar.
 *
 * @param onRequestLocationPermission launches the platform permission request.
 * @param onOpenAppSettings opens the app's system settings (permission denied permanently).
 * @param onOpenLocationSettings opens the system location settings.
 * @param onSendFeedback opens an email to the given feedback address.
 * @param onRate opens the app's store page.
 */
@Composable
fun AppRoot(
    onRequestLocationPermission: () -> Unit,
    onOpenAppSettings: () -> Boolean,
    onOpenLocationSettings: () -> Boolean,
    aboutVersion: AppVersion,
    onSendFeedback: (address: String) -> Boolean,
    onRate: () -> Boolean,
    permissionViewModel: LocationPermissionViewModel = hiltViewModel(),
) {
    val permissionState by permissionViewModel.state.collectAsStateWithLifecycle()
    val announcePermissionChange by permissionViewModel.announceChange.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val settingsUnavailable = stringResource(R.string.settings_unavailable)
    val locationSettingsUnavailable = stringResource(R.string.location_settings_unavailable)
    val feedbackAddress = stringResource(R.string.about_feedback_address)
    AppScaffold(snackbarHostState) { contentModifier ->
        if (permissionState == LocationPermissionState.Granted) {
            DashboardScreen(
                onOpenLocationSettings = {
                    if (!onOpenLocationSettings()) {
                        scope.launch { snackbarHostState.showSnackbar(locationSettingsUnavailable) }
                    }
                },
                aboutVersion = aboutVersion,
                onSendFeedback = { onSendFeedback(feedbackAddress) },
                onRate = onRate,
                modifier = contentModifier,
            )
        } else {
            PermissionOnboardingScreen(
                state = permissionState,
                onGrantPermission = onRequestLocationPermission,
                onOpenSettings = {
                    if (!onOpenAppSettings()) {
                        scope.launch { snackbarHostState.showSnackbar(settingsUnavailable) }
                    }
                },
                modifier = contentModifier,
                announceStateChange = announcePermissionChange,
            )
        }
    }
}

/**
 * The window chrome under every screen (edge to edge): the page background, the status-bar area in
 * the top bar's card color (as the original `colorPrimaryDark`) and the snackbar host. [content]
 * gets the modifier that keeps it inside the safe drawing area.
 */
@Composable
internal fun AppScaffold(
    snackbarHostState: SnackbarHostState,
    content: @Composable (contentModifier: Modifier) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = SmartFlightTheme.colors.page) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = SmartFlightTheme.colors.page,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            Box(Modifier.fillMaxSize()) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.safeDrawing)
                        .background(SmartFlightTheme.colors.card),
                )
                content(Modifier.padding(innerPadding).safeDrawingPadding())
            }
        }
    }
}
