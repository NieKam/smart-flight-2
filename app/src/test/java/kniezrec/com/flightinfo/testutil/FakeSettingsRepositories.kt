package kniezrec.com.flightinfo.testutil

import kniezrec.com.flightinfo.dashboard.HideableCard
import kniezrec.com.flightinfo.dashboard.data.CardVisibilityRepository
import kniezrec.com.flightinfo.display.DisplayPreferences
import kniezrec.com.flightinfo.display.data.DisplaySettingsRepository
import kniezrec.com.flightinfo.displayunits.UnitPreferences
import kniezrec.com.flightinfo.displayunits.data.UnitSettingsRepository
import kniezrec.com.flightinfo.monitoring.BackgroundNotificationPreferences
import kniezrec.com.flightinfo.monitoring.data.BackgroundNotificationSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [UnitSettingsRepository] for consumers of the unit settings. */
class FakeUnitSettingsRepository(
    initial: UnitPreferences = UnitPreferences(),
) : UnitSettingsRepository {
    private val state = MutableStateFlow(initial)

    override val units: StateFlow<UnitPreferences> = state.asStateFlow()

    override suspend fun setUnits(value: UnitPreferences) {
        state.value = value
    }
}

/** In-memory [DisplaySettingsRepository] for consumers of the display settings. */
class FakeDisplaySettingsRepository(
    initial: DisplayPreferences = DisplayPreferences(),
) : DisplaySettingsRepository {
    private val state = MutableStateFlow(initial)

    override val display: StateFlow<DisplayPreferences> = state.asStateFlow()

    override suspend fun set(value: DisplayPreferences) {
        state.value = value
    }
}

/** In-memory [BackgroundNotificationSettingsRepository] for consumers of the notification setting. */
class FakeBackgroundNotificationSettingsRepository(
    showBackgroundNotification: Boolean = true,
) : BackgroundNotificationSettingsRepository {
    private val state = MutableStateFlow(BackgroundNotificationPreferences(showBackgroundNotification))

    override val settings: StateFlow<BackgroundNotificationPreferences> = state.asStateFlow()

    override suspend fun setShowBackgroundNotification(enabled: Boolean) {
        state.value = BackgroundNotificationPreferences(enabled)
    }
}

/** In-memory [CardVisibilityRepository] for the dashboard and Settings. */
class FakeCardVisibilityRepository(
    initial: Set<HideableCard> = emptySet(),
) : CardVisibilityRepository {
    private val state = MutableStateFlow(initial)

    override val hiddenCards: StateFlow<Set<HideableCard>> = state.asStateFlow()

    override suspend fun hide(card: HideableCard) {
        state.value += card
    }

    override suspend fun showAll() {
        state.value = emptySet()
    }
}
