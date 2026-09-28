package kniezrec.com.flightinfo.display.ui

import android.content.pm.ActivityInfo
import kniezrec.com.flightinfo.display.DisplayPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayWindowEffectsTest {
    @Test
    fun portraitIsRequestedOnlyWhenNotAlreadyRequested() {
        val portrait = DisplayPreferences(portraitOrientation = true)

        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            orientationToRequest(portrait, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            orientationToRequest(portrait, ActivityInfo.SCREEN_ORIENTATION_SENSOR),
        )
        assertNull(orientationToRequest(portrait, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT))
    }

    @Test
    fun sensorIsRequestedOnlyWhenNotAlreadyRequested() {
        val sensor = DisplayPreferences(portraitOrientation = false)

        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR,
            orientationToRequest(sensor, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
        )
        assertNull(orientationToRequest(sensor, ActivityInfo.SCREEN_ORIENTATION_SENSOR))
    }
}
