package kniezrec.com.flightinfo.orientation.data

import android.app.Application
import android.hardware.display.DisplayManager
import android.view.Surface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.orientation.DisplayRotation
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDisplay

@RunWith(AndroidJUnit4::class)
class DisplayRotationProviderTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val provider = AndroidDisplayRotationProvider(application.getSystemService(DisplayManager::class.java))

    @Test fun `surface rotations map to display rotations`() {
        assertEquals(DisplayRotation.Portrait, displayRotationFromSurface(Surface.ROTATION_0))
        assertEquals(DisplayRotation.Landscape, displayRotationFromSurface(Surface.ROTATION_90))
        assertEquals(DisplayRotation.ReversePortrait, displayRotationFromSurface(Surface.ROTATION_180))
        assertEquals(DisplayRotation.ReverseLandscape, displayRotationFromSurface(Surface.ROTATION_270))
        assertEquals(DisplayRotation.Portrait, displayRotationFromSurface(-1))
    }

    @Test fun `the current rotation of the default display is read from an application context`() {
        assertEquals(DisplayRotation.Portrait, provider.current())

        shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)

        assertEquals(DisplayRotation.Landscape, provider.current())
    }
}
