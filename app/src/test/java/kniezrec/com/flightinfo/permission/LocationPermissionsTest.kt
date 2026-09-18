package kniezrec.com.flightinfo.permission

import android.Manifest
import kniezrec.com.flightinfo.ui.permission.actionCyan
import kniezrec.com.flightinfo.ui.permission.cardPurple
import kniezrec.com.flightinfo.ui.permission.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationPermissionsTest {
    @Test
    fun platformCompatibleRequestIncludesFineAndCoarseLocation() {
        assertEquals(2, locationPermissionRequest.size)
        assertTrue(locationPermissionRequest.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(locationPermissionRequest.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    @Test
    fun actionCyanHasAccessibleContrastOnTheCard() {
        assertTrue(contrastRatio(actionCyan, cardPurple) >= 4.5f)
    }
}
