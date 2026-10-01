package kniezrec.com.flightinfo.route.ui

import kniezrec.com.flightinfo.nearby.NearbyCoordinate
import org.junit.Assert.assertEquals
import org.junit.Test

class PickerCameraTest {
    private val mountainView = NearbyCoordinate(37.39, -122.08)

    @Test fun `the camera is centered on the city`() {
        val camera = pickerCamera(mountainView, currentZoom = 4.0)

        assertEquals(37.39, camera.latitude, 0.0)
        assertEquals(-122.08, camera.longitude, 0.0)
    }

    @Test fun `at the world zoom the camera zooms in to the region`() {
        assertEquals(PICKER_CITY_ZOOM, pickerCamera(mountainView, PICKER_WORLD_ZOOM).zoom, 0.0)
        assertEquals(PICKER_CITY_ZOOM, pickerCamera(mountainView, currentZoom = 1.0).zoom, 0.0)
    }

    @Test fun `a zoom chosen by the user is kept`() {
        assertEquals(4.0, pickerCamera(mountainView, currentZoom = 4.0).zoom, 0.0)
        assertEquals(6.0, pickerCamera(mountainView, currentZoom = 6.0).zoom, 0.0)
        assertEquals(3.5, pickerCamera(mountainView, currentZoom = 3.5).zoom, 0.0)
    }
}
