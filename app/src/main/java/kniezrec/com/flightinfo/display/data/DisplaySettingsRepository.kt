package kniezrec.com.flightinfo.display.data

import android.content.SharedPreferences
import kniezrec.com.flightinfo.data.observedState
import kniezrec.com.flightinfo.di.ApplicationScope
import kniezrec.com.flightinfo.display.DisplayPreferences
import kniezrec.com.flightinfo.display.data.di.DisplayBehaviorPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Display behavior selected in Settings (keep screen on, orientation, map zoom). */
interface DisplaySettingsRepository {
    /** Current settings; a missing or non-boolean stored value falls back to its own default. */
    val display: StateFlow<DisplayPreferences>

    /** Persists [value]; [display] holds it when this returns. */
    suspend fun set(value: DisplayPreferences)
}

/** [DisplaySettingsRepository] over the `display_behavior` file. */
@Singleton
class SharedPreferencesDisplaySettingsRepository
    @Inject
    constructor(
        @DisplayBehaviorPreferences private val preferences: SharedPreferences,
        @ApplicationScope scope: CoroutineScope,
    ) : DisplaySettingsRepository {
        private val state = preferences.observedState(scope) { readDisplay() }

        override val display: StateFlow<DisplayPreferences> = state.asStateFlow()

        override suspend fun set(value: DisplayPreferences) {
            preferences
                .edit()
                .putBoolean(KEY_KEEP_SCREEN, value.keepScreenAlwaysOn)
                .putBoolean(KEY_PORTRAIT, value.portraitOrientation)
                .putBoolean(KEY_LARGER_ZOOM, value.largerMapZoom)
                .apply()
            state.value = value
        }

        private companion object {
            const val KEY_PREFIX = "display_behavior_"
            const val KEY_KEEP_SCREEN = KEY_PREFIX + "keep_screen_always_on"
            const val KEY_PORTRAIT = KEY_PREFIX + "portrait_orientation"
            const val KEY_LARGER_ZOOM = KEY_PREFIX + "larger_map_zoom"

            fun SharedPreferences.readDisplay() =
                DisplayPreferences(
                    keepScreenAlwaysOn = booleanOrDefault(KEY_KEEP_SCREEN, false),
                    portraitOrientation = booleanOrDefault(KEY_PORTRAIT, true),
                    largerMapZoom = booleanOrDefault(KEY_LARGER_ZOOM, false),
                )

            fun SharedPreferences.booleanOrDefault(
                key: String,
                default: Boolean,
            ): Boolean = if (all[key] is Boolean) getBoolean(key, default) else default
        }
    }
