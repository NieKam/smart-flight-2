package kniezrec.com.flightinfo.display.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import kniezrec.com.flightinfo.display.DisplayPreferences

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
