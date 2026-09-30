package kniezrec.com.flightinfo.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kniezrec.com.flightinfo.dashboard.HideableCard
import kniezrec.com.flightinfo.dashboard.data.CardVisibilityRepository
import kniezrec.com.flightinfo.display.DisplayPreferences
import kniezrec.com.flightinfo.display.data.DisplaySettingsRepository
import kniezrec.com.flightinfo.displayunits.UnitPreferences
import kniezrec.com.flightinfo.displayunits.data.UnitSettingsRepository
import kniezrec.com.flightinfo.monitoring.NotificationAccess
import kniezrec.com.flightinfo.monitoring.data.BackgroundNotificationSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the Settings screen shows. */
data class SettingsUiState(
    val units: UnitPreferences = UnitPreferences(),
    val display: DisplayPreferences = DisplayPreferences(),
    val showBackgroundNotification: Boolean = true,
    /** The background notification cannot be shown (Android 13+ permission or notifications off). */
    val notificationsBlocked: Boolean = false,
    /** Cards hidden because the device lacks their sensor. */
    val hiddenCards: Set<HideableCard> = emptySet(),
)

/** What the Activity does for the background notification's permission. */
enum class NotificationAction {
    /** Show the system POST_NOTIFICATIONS dialog. */
    RequestPermission,

    /** Open the app's notification settings (permission denied permanently or notifications off). */
    OpenSettings,
}

/**
 * State and actions of the Settings screen, over the settings repositories and the hidden cards.
 * Every setter persists at once; consumers (window effects, cards, the monitoring service, the
 * dashboard) observe the repositories themselves. The repositories are `internal` so tests can check their singleton scope.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        internal val unitSettingsRepository: UnitSettingsRepository,
        internal val displaySettingsRepository: DisplaySettingsRepository,
        internal val backgroundNotificationSettingsRepository: BackgroundNotificationSettingsRepository,
        internal val cardVisibilityRepository: CardVisibilityRepository,
    ) : ViewModel() {
        private val backgroundMonitoringRequestEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        private val notificationActionEvents = MutableSharedFlow<NotificationAction>(extraBufferCapacity = 1)
        private val notificationAccess = MutableStateFlow(NotificationAccess.Allowed)

        val state: StateFlow<SettingsUiState> =
            combine(
                unitSettingsRepository.units,
                displaySettingsRepository.display,
                backgroundNotificationSettingsRepository.settings,
                notificationAccess,
                cardVisibilityRepository.hiddenCards,
            ) { units, display, notification, access, hiddenCards ->
                SettingsUiState(
                    units,
                    display,
                    notification.showBackgroundNotification,
                    access != NotificationAccess.Allowed,
                    hiddenCards,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), currentState())

        /**
         * Requests for the Activity: show the POST_NOTIFICATIONS dialog or open the notification
         * settings. Nothing is kept for a collector that subscribes later.
         */
        val notificationActions: Flow<NotificationAction> = notificationActionEvents.asSharedFlow()

        /**
         * Emits after "show background notification" has been switched on and persisted. The Activity
         * starts background monitoring on it while it is resumed and location is granted. Nothing is
         * kept for a collector that subscribes later (onResume starts monitoring by itself).
         */
        val backgroundMonitoringRequests: Flow<Unit> = backgroundMonitoringRequestEvents.asSharedFlow()

        fun setUnits(value: UnitPreferences) {
            viewModelScope.launch { unitSettingsRepository.setUnits(value) }
        }

        fun setDisplay(value: DisplayPreferences) {
            viewModelScope.launch { displaySettingsRepository.set(value) }
        }

        /** "Show hidden cards": every hidden card returns to the dashboard, persistently. */
        fun showHiddenCards() {
            viewModelScope.launch { cardVisibilityRepository.showAll() }
        }

        /** Switching it on also asks for the notification permission when it can be requested. */
        fun setShowBackgroundNotification(enabled: Boolean) {
            viewModelScope.launch {
                backgroundNotificationSettingsRepository.setShowBackgroundNotification(enabled)
                if (enabled) {
                    backgroundMonitoringRequestEvents.tryEmit(Unit)
                    if (notificationAccess.value == NotificationAccess.Requestable) {
                        notificationActionEvents.tryEmit(NotificationAction.RequestPermission)
                    }
                }
            }
        }

        /** The Activity read the platform state (on resume and after a permission result). */
        fun refreshNotificationAccess(access: NotificationAccess) {
            notificationAccess.value = access
        }

        /** "Allow notifications" in Settings: the system dialog if it can still be shown, else the settings. */
        fun allowNotifications() {
            when (notificationAccess.value) {
                NotificationAccess.Allowed -> Unit
                NotificationAccess.Requestable -> notificationActionEvents.tryEmit(NotificationAction.RequestPermission)
                NotificationAccess.Blocked -> notificationActionEvents.tryEmit(NotificationAction.OpenSettings)
            }
        }

        /** After location is granted: ask for notifications right away if the background notification is on. */
        fun shouldRequestNotificationPermission(): Boolean =
            backgroundNotificationSettingsRepository.settings.value.showBackgroundNotification &&
                notificationAccess.value == NotificationAccess.Requestable

        private fun currentState() =
            SettingsUiState(
                units = unitSettingsRepository.units.value,
                display = displaySettingsRepository.display.value,
                showBackgroundNotification = backgroundNotificationSettingsRepository.settings.value.showBackgroundNotification,
                notificationsBlocked = notificationAccess.value != NotificationAccess.Allowed,
                hiddenCards = cardVisibilityRepository.hiddenCards.value,
            )

        internal companion object {
            /** Longer than a configuration change, as in the card ViewModels. */
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
