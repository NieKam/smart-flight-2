package kniezrec.com.flightinfo.displayunits.data

import android.content.SharedPreferences
import kniezrec.com.flightinfo.data.observedState
import kniezrec.com.flightinfo.di.ApplicationScope
import kniezrec.com.flightinfo.displayunits.AltitudeUnit
import kniezrec.com.flightinfo.displayunits.DistanceUnit
import kniezrec.com.flightinfo.displayunits.PressureUnit
import kniezrec.com.flightinfo.displayunits.SpeedUnit
import kniezrec.com.flightinfo.displayunits.UnitKey
import kniezrec.com.flightinfo.displayunits.UnitPreferences
import kniezrec.com.flightinfo.displayunits.VerticalSpeedUnit
import kniezrec.com.flightinfo.displayunits.data.di.DisplayUnitsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Display units selected in Settings. */
interface UnitSettingsRepository {
    /** Current selection; missing or unknown stored values fall back to the metric defaults one by one. */
    val units: StateFlow<UnitPreferences>

    /** Persists [value]; [units] holds it when this returns. */
    suspend fun setUnits(value: UnitPreferences)
}

/** [UnitSettingsRepository] over the `display_units` file. */
@Singleton
class SharedPreferencesUnitSettingsRepository
    @Inject
    constructor(
        @DisplayUnitsPreferences private val preferences: SharedPreferences,
        @ApplicationScope scope: CoroutineScope,
    ) : UnitSettingsRepository {
        private val state = preferences.observedState(scope) { readUnits() }

        override val units: StateFlow<UnitPreferences> = state.asStateFlow()

        override suspend fun setUnits(value: UnitPreferences) {
            preferences
                .edit()
                .putString(KEY_SPEED, value.speed.key)
                .putString(KEY_ALTITUDE, value.altitude.key)
                .putString(KEY_DISTANCE, value.distance.key)
                .putString(KEY_VERTICAL_SPEED, value.verticalSpeed.key)
                .putString(KEY_PRESSURE, value.pressure.key)
                .apply()
            state.value = value
        }

        private companion object {
            const val KEY_PREFIX = "display_units_"
            const val KEY_SPEED = KEY_PREFIX + "speed"
            const val KEY_ALTITUDE = KEY_PREFIX + "altitude"
            const val KEY_DISTANCE = KEY_PREFIX + "distance"
            const val KEY_VERTICAL_SPEED = KEY_PREFIX + "vertical_speed"
            const val KEY_PRESSURE = KEY_PREFIX + "pressure"

            fun SharedPreferences.readUnits(): UnitPreferences =
                UnitPreferences(
                    speed = enumOrDefault(KEY_SPEED, SpeedUnit.entries),
                    altitude = enumOrDefault(KEY_ALTITUDE, AltitudeUnit.entries),
                    distance = enumOrDefault(KEY_DISTANCE, DistanceUnit.entries),
                    verticalSpeed = enumOrDefault(KEY_VERTICAL_SPEED, VerticalSpeedUnit.entries),
                    pressure = enumOrDefault(KEY_PRESSURE, PressureUnit.entries),
                )

            fun <T : UnitKey> SharedPreferences.enumOrDefault(
                key: String,
                values: List<T>,
            ): T {
                val stored = getString(key, null)
                return values.firstOrNull { it.key == stored } ?: values.first()
            }
        }
    }
