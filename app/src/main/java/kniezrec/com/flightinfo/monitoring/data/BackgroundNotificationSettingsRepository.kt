package kniezrec.com.flightinfo.monitoring.data

import android.content.SharedPreferences
import kniezrec.com.flightinfo.data.observedState
import kniezrec.com.flightinfo.di.ApplicationScope
import kniezrec.com.flightinfo.monitoring.BackgroundNotificationPreferences
import kniezrec.com.flightinfo.monitoring.data.di.MonitoringBehaviorPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Whether background monitoring may keep running (with its notification) while the app is hidden. */
interface BackgroundNotificationSettingsRepository {
    /** Current setting; a missing or non-boolean stored value means enabled. */
    val settings: StateFlow<BackgroundNotificationPreferences>

    /** Persists [enabled]; [settings] holds it when this returns. */
    suspend fun setShowBackgroundNotification(enabled: Boolean)
}

/** [BackgroundNotificationSettingsRepository] over the `monitoring_behavior` file. */
@Singleton
class SharedPreferencesBackgroundNotificationSettingsRepository
    @Inject
    constructor(
        @MonitoringBehaviorPreferences private val preferences: SharedPreferences,
        @ApplicationScope scope: CoroutineScope,
    ) : BackgroundNotificationSettingsRepository {
        private val state = preferences.observedState(scope) { readSettings() }

        override val settings: StateFlow<BackgroundNotificationPreferences> = state.asStateFlow()

        override suspend fun setShowBackgroundNotification(enabled: Boolean) {
            preferences.edit().putBoolean(KEY_SHOW_BACKGROUND_NOTIFICATION, enabled).apply()
            state.value = BackgroundNotificationPreferences(enabled)
        }

        private companion object {
            const val KEY_SHOW_BACKGROUND_NOTIFICATION = "monitoring_show_background_notification"

            fun SharedPreferences.readSettings() =
                BackgroundNotificationPreferences(
                    showBackgroundNotification =
                        if (all[KEY_SHOW_BACKGROUND_NOTIFICATION] is Boolean) {
                            getBoolean(KEY_SHOW_BACKGROUND_NOTIFICATION, true)
                        } else {
                            true
                        },
                )
        }
    }
