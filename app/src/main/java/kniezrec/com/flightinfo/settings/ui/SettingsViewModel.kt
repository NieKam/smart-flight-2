package kniezrec.com.flightinfo.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kniezrec.com.flightinfo.display.DisplayPreferences
import kniezrec.com.flightinfo.display.data.DisplaySettingsRepository
import kniezrec.com.flightinfo.displayunits.UnitPreferences
import kniezrec.com.flightinfo.displayunits.data.UnitSettingsRepository
import kniezrec.com.flightinfo.monitoring.data.BackgroundNotificationSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
)

/**
 * State and actions of the Settings screen, over the three settings repositories. Every setter
 * persists at once; consumers (window effects, cards, the monitoring service) observe the
 * repositories themselves.
 */
@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val unitSettingsRepository: UnitSettingsRepository,
        private val displaySettingsRepository: DisplaySettingsRepository,
        private val backgroundNotificationSettingsRepository: BackgroundNotificationSettingsRepository,
    ) : ViewModel() {
        private val backgroundMonitoringRequestEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        val state: StateFlow<SettingsUiState> =
            combine(
                unitSettingsRepository.units,
                displaySettingsRepository.display,
                backgroundNotificationSettingsRepository.settings,
            ) { units, display, notification ->
                SettingsUiState(units, display, notification.showBackgroundNotification)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), currentState())

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

        fun setShowBackgroundNotification(enabled: Boolean) {
            viewModelScope.launch {
                backgroundNotificationSettingsRepository.setShowBackgroundNotification(enabled)
                if (enabled) backgroundMonitoringRequestEvents.tryEmit(Unit)
            }
        }

        private fun currentState() =
            SettingsUiState(
                units = unitSettingsRepository.units.value,
                display = displaySettingsRepository.display.value,
                showBackgroundNotification = backgroundNotificationSettingsRepository.settings.value.showBackgroundNotification,
            )

        internal companion object {
            /** Longer than a configuration change, as in the card ViewModels. */
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }
