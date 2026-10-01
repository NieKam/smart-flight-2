package kniezrec.com.flightinfo.display.ui

import android.app.Activity
import android.app.UiModeManager
import android.content.pm.ActivityInfo
import android.view.WindowManager
import kniezrec.com.flightinfo.display.DisplayPreferences
import kniezrec.com.flightinfo.display.ThemeMode

/** Orientation [preferences] ask for, or `null` when [currentOrientation] already is that one. */
internal fun orientationToRequest(
    preferences: DisplayPreferences,
    currentOrientation: Int,
): Int? {
    val requested =
        if (preferences.portraitOrientation) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    return requested.takeIf { it != currentOrientation }
}

/** Applies keep-screen-on and the requested orientation; the orientation is requested only when it changes. */
internal fun Activity.applyDisplayPreferences(preferences: DisplayPreferences) {
    if (preferences.keepScreenAlwaysOn) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    orientationToRequest(preferences, requestedOrientation)?.let { requestedOrientation = it }
}

/** The per-app night mode of [UiModeManager.setApplicationNightMode] for this Theme setting. */
internal fun ThemeMode.applicationNightMode(): Int =
    when (this) {
        // AUTO leaves the app's night mode undefined, so it follows the system.
        ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
        ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
        ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
    }

/**
 * Persists [mode] as the app's own night mode, so the system starts the app with the matching
 * configuration and `values-night` window background (no flash of the other theme at launch). The
 * system recreates the Activity when the effective night mode changes.
 */
internal fun Activity.applyApplicationNightMode(mode: ThemeMode) {
    getSystemService(UiModeManager::class.java)?.setApplicationNightMode(mode.applicationNightMode())
}
