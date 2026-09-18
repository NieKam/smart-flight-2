package kniezrec.com.flightinfo.permission

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationPermissionStateControllerTest {
    @Test
    fun requestHistorySurvivesControllerRecreation() {
        val platform =
            FakePermissionPlatform(
                isFineGranted = false,
                isCoarseGranted = false,
                shouldShowRationale = false,
            )
        val history = FakeRequestHistory()

        LocationPermissionStateController(platform, history).recordPermissionRequest()

        assertEquals(
            LocationPermissionState.SettingsRequired,
            LocationPermissionStateController(platform, history).currentState(),
        )
    }

    @Test
    fun refreshUsesTheCurrentGrantAfterReturningFromSettings() {
        val platform =
            FakePermissionPlatform(
                isFineGranted = false,
                isCoarseGranted = false,
                shouldShowRationale = false,
            )
        val controller =
            LocationPermissionStateController(platform, FakeRequestHistory(hasRequested = true))
        assertEquals(LocationPermissionState.SettingsRequired, controller.currentState())

        platform.isFineGranted = true

        assertEquals(LocationPermissionState.Granted, controller.currentState())
    }

    private class FakePermissionPlatform(
        var isFineGranted: Boolean,
        var isCoarseGranted: Boolean,
        var shouldShowRationale: Boolean,
    ) : FineLocationPermissionPlatform {
        override fun isFineLocationGranted(): Boolean = isFineGranted

        override fun isCoarseLocationGranted(): Boolean = isCoarseGranted

        override fun shouldShowFineLocationRationale(): Boolean = shouldShowRationale
    }

    private class FakeRequestHistory(
        hasRequested: Boolean = false,
    ) : LocationPermissionRequestHistory {
        override var hasRequestedFineLocation: Boolean = hasRequested
    }
}
