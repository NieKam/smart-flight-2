package kniezrec.com.flightinfo.display

import android.content.SharedPreferences

data class DisplayPreferences(
    val keepScreenAlwaysOn: Boolean = false,
    val portraitOrientation: Boolean = true,
    val largerMapZoom: Boolean = false,
)

interface DisplayEffectSink {
    fun setKeepScreenAlwaysOn(enabled: Boolean)

    fun requestOrientation(orientation: Int)
}

class DisplayPreferencesApplier(
    private val sink: DisplayEffectSink,
    private val portraitOrientation: Int,
    private val sensorOrientation: Int,
) {
    fun apply(
        preferences: DisplayPreferences,
        currentOrientation: Int,
    ) {
        sink.setKeepScreenAlwaysOn(preferences.keepScreenAlwaysOn)
        val requested = if (preferences.portraitOrientation) portraitOrientation else sensorOrientation
        if (currentOrientation != requested) sink.requestOrientation(requested)
    }
}

class DisplayPreferencesStore(
    private val preferences: SharedPreferences,
) {
    fun read() =
        DisplayPreferences(
            keepScreenAlwaysOn = booleanOrDefault(KEY_KEEP_SCREEN, false),
            portraitOrientation = booleanOrDefault(KEY_PORTRAIT, true),
            largerMapZoom = booleanOrDefault(KEY_LARGER_ZOOM, false),
        )

    fun write(value: DisplayPreferences) {
        preferences
            .edit()
            .putBoolean(KEY_KEEP_SCREEN, value.keepScreenAlwaysOn)
            .putBoolean(KEY_PORTRAIT, value.portraitOrientation)
            .putBoolean(KEY_LARGER_ZOOM, value.largerMapZoom)
            .commit()
    }

    private fun booleanOrDefault(
        key: String,
        default: Boolean,
    ): Boolean = if (preferences.all[key] is Boolean) preferences.getBoolean(key, default) else default

    private companion object {
        const val KEY_PREFIX = "display_behavior_"
        const val KEY_KEEP_SCREEN = KEY_PREFIX + "keep_screen_always_on"
        const val KEY_PORTRAIT = KEY_PREFIX + "portrait_orientation"
        const val KEY_LARGER_ZOOM = KEY_PREFIX + "larger_map_zoom"
    }
}
