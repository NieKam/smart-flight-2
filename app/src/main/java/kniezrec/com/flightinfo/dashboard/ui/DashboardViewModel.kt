package kniezrec.com.flightinfo.dashboard.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kniezrec.com.flightinfo.displayunits.UnitPreferences
import kniezrec.com.flightinfo.displayunits.data.UnitSettingsRepository
import kniezrec.com.flightinfo.location.data.LocationRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Screen-level state of the dashboard: the display units shared by the Flight parameters, Nearby
 * city and Route cards.
 *
 * Units are a presentation concern of the stateless cards, not part of the card data: they are read
 * once here and passed down, so the card ViewModels stay about their own data and one source serves
 * the three cards. The Settings overlay has its own ViewModel; the dashboard does not depend on it.
 * [isLocationEnabled] serves the "Enable GPS" prompt shown when the dashboard starts.
 */
@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        unitSettingsRepository: UnitSettingsRepository,
        private val locationRepository: LocationRepository,
    ) : ViewModel() {
        val units: StateFlow<UnitPreferences> = unitSettingsRepository.units

        /** A fresh read of the GPS state. */
        fun isLocationEnabled(): Boolean = locationRepository.isLocationEnabled()
    }
