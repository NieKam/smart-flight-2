package kniezrec.com.flightinfo.monitoring

import android.content.SharedPreferences

data class BackgroundNotificationPreferences(
    val showBackgroundNotification: Boolean = true,
)

class BackgroundNotificationPreferencesStore(
    private val preferences: SharedPreferences,
) {
    fun read() =
        BackgroundNotificationPreferences(
            showBackgroundNotification =
                if (preferences.all[KEY_SHOW_BACKGROUND_NOTIFICATION] is Boolean) {
                    preferences.getBoolean(KEY_SHOW_BACKGROUND_NOTIFICATION, true)
                } else {
                    true
                },
        )

    fun write(value: BackgroundNotificationPreferences) {
        preferences.edit().putBoolean(KEY_SHOW_BACKGROUND_NOTIFICATION, value.showBackgroundNotification).commit()
    }

    companion object {
        const val KEY_SHOW_BACKGROUND_NOTIFICATION = "monitoring_show_background_notification"
        const val PREFERENCES_NAME = "monitoring_behavior"
    }
}
