package kniezrec.com.flightinfo.permission.ui

import kniezrec.com.flightinfo.permission.FineLocationPermissionPlatform
import kniezrec.com.flightinfo.permission.LocationPermissionRequestHistory
import kniezrec.com.flightinfo.permission.LocationPermissionState
import kniezrec.com.flightinfo.permission.PermissionSnapshot
import kniezrec.com.flightinfo.permission.snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ports the former `LocationPermissionStateControllerTest` and covers the announce flag. */
class LocationPermissionViewModelTest {
    private val denied = PermissionSnapshot(isFineLocationGranted = false, isCoarseLocationGranted = false, shouldShowRationale = false)

    @Test
    fun startsRequestableBeforeTheFirstRefresh() {
        assertEquals(LocationPermissionState.Requestable, LocationPermissionViewModel(FakeRequestHistory()).state.value)
    }

    @Test
    fun firstLaunchWithoutRationaleIsRequestable() {
        val viewModel = LocationPermissionViewModel(FakeRequestHistory())

        assertEquals(LocationPermissionState.Requestable, viewModel.refresh(denied))
        assertEquals(LocationPermissionState.Requestable, viewModel.state.value)
    }

    @Test
    fun requestHistorySurvivesViewModelRecreation() {
        val history = FakeRequestHistory()

        LocationPermissionViewModel(history).onRequestLaunched()

        assertTrue(history.hasRequestedFineLocation)
        val recreated = LocationPermissionViewModel(history)
        recreated.refresh(denied)
        assertEquals(LocationPermissionState.SettingsRequired, recreated.state.value)
    }

    @Test
    fun rationaleAfterARequestKeepsItRequestable() {
        val viewModel = LocationPermissionViewModel(FakeRequestHistory(hasRequested = true))

        viewModel.refresh(denied.copy(shouldShowRationale = true))

        assertEquals(LocationPermissionState.Requestable, viewModel.state.value)
    }

    @Test
    fun coarseOnlyGrantIsRequestable() {
        val viewModel = LocationPermissionViewModel(FakeRequestHistory(hasRequested = true))

        viewModel.refresh(denied.copy(isCoarseLocationGranted = true))

        assertEquals(LocationPermissionState.Requestable, viewModel.state.value)
    }

    @Test
    fun refreshUsesTheCurrentGrantAfterReturningFromSettings() {
        val platform = FakePermissionPlatform(isFineGranted = false, isCoarseGranted = false, shouldShowRationale = false)
        val viewModel = LocationPermissionViewModel(FakeRequestHistory(hasRequested = true))
        viewModel.refresh(platform.snapshot())
        assertEquals(LocationPermissionState.SettingsRequired, viewModel.state.value)

        platform.isFineGranted = true

        assertEquals(LocationPermissionState.Granted, viewModel.refresh(platform.snapshot()))
        assertEquals(LocationPermissionState.Granted, viewModel.state.value)
    }

    @Test
    fun announceChangeIsSetByAPermissionResultAndStaysSet() {
        val viewModel = LocationPermissionViewModel(FakeRequestHistory())
        viewModel.refresh(denied)
        assertFalse(viewModel.announceChange.value)

        viewModel.refresh(denied, announceChange = true)
        assertTrue(viewModel.announceChange.value)

        viewModel.refresh(denied)
        assertTrue(viewModel.announceChange.value)
    }

    @Test
    fun snapshotReadsEveryPlatformCheck() {
        val platform = FakePermissionPlatform(isFineGranted = true, isCoarseGranted = false, shouldShowRationale = true)

        assertEquals(
            PermissionSnapshot(isFineLocationGranted = true, isCoarseLocationGranted = false, shouldShowRationale = true),
            platform.snapshot(),
        )
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
