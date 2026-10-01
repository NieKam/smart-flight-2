package kniezrec.com.flightinfo.orientation.data

import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import kniezrec.com.flightinfo.orientation.DisplayRotation
import javax.inject.Inject

/** Current rotation of the display that orientation samples are made relative to. */
interface DisplayRotationProvider {
    fun current(): DisplayRotation
}

/**
 * Rotation of the default display, read from the application-level [DisplayManager] (an
 * application context is not associated with a display, so `Context.display` cannot be used by a
 * singleton). On multi-display devices and foldables this is the default display, not necessarily
 * the one showing the activity; acceptable for this app.
 */
internal class AndroidDisplayRotationProvider
    @Inject
    constructor(
        private val displayManager: DisplayManager,
    ) : DisplayRotationProvider {
        override fun current(): DisplayRotation =
            displayRotationFromSurface(displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0)
    }

/** Maps a `Surface.ROTATION_*` value; unknown values count as the natural orientation. */
internal fun displayRotationFromSurface(rotation: Int): DisplayRotation =
    when (rotation) {
        Surface.ROTATION_90 -> DisplayRotation.Landscape
        Surface.ROTATION_180 -> DisplayRotation.ReversePortrait
        Surface.ROTATION_270 -> DisplayRotation.ReverseLandscape
        else -> DisplayRotation.Portrait
    }
