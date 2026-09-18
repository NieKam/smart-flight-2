package kniezrec.com.flightinfo

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationPermissionStateControllerTest {
    @Test
    fun requestHistorySurvivesControllerRecreation() {
        val platform = FakePermissionPlatform(isGranted = false, shouldShowRationale = false)
        val history = FakeRequestHistory()

        LocationPermissionStateController(platform, history).recordPermissionRequest()

        assertEquals(
            LocationPermissionState.SettingsRequired,
            LocationPermissionStateController(platform, history).currentState(),
        )
    }

    @Test
    fun refreshUsesTheCurrentGrantAfterReturningFromSettings() {
        val platform = FakePermissionPlatform(isGranted = false, shouldShowRationale = false)
        val controller = LocationPermissionStateController(platform, FakeRequestHistory(hasRequested = true))
        assertEquals(LocationPermissionState.SettingsRequired, controller.currentState())

        platform.isGranted = true

        assertEquals(LocationPermissionState.Granted, controller.currentState())
    }

    private class FakePermissionPlatform(
        var isGranted: Boolean,
        var shouldShowRationale: Boolean,
    ) : FineLocationPermissionPlatform {
        override fun isFineLocationGranted(): Boolean = isGranted

        override fun shouldShowFineLocationRationale(): Boolean = shouldShowRationale
    }

    private class FakeRequestHistory(
        hasRequested: Boolean = false,
    ) : LocationPermissionRequestHistory {
        override var hasRequestedFineLocation: Boolean = hasRequested
    }
}
