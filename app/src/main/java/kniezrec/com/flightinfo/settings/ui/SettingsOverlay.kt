package kniezrec.com.flightinfo.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * [UnitSettingsScreen] bound to [SettingsViewModel]. Every change is persisted at once; window
 * effects and the cards follow from the settings repositories.
 */
@Composable
fun SettingsOverlay(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    UnitSettingsScreen(
        preferences = state.units,
        onPreferenceChange = viewModel::setUnits,
        displayPreferences = state.display,
        onDisplayPreferenceChange = viewModel::setDisplay,
        showBackgroundNotification = state.showBackgroundNotification,
        onBackgroundNotificationChange = viewModel::setShowBackgroundNotification,
        notificationsBlocked = state.notificationsBlocked,
        onAllowNotifications = viewModel::allowNotifications,
        hiddenCards = state.hiddenCards,
        onShowHiddenCards = viewModel::showHiddenCards,
        onBack = onBack,
        modifier = modifier,
    )
}
