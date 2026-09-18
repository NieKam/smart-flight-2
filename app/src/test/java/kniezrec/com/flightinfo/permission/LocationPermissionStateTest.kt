package kniezrec.com.flightinfo.permission

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationPermissionStateTest {
    @Test
    fun grantedPermissionShowsGrantedState() {
        assertEquals(
            LocationPermissionState.Granted,
            locationPermissionState(
                isFineLocationGranted = true,
                isCoarseLocationGranted = false,
                hasRequestedPermission = true,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun initialStateIsRequestableEvenThoughRationaleIsFalse() {
        assertEquals(
            LocationPermissionState.Requestable,
            locationPermissionState(
                isFineLocationGranted = false,
                isCoarseLocationGranted = false,
                hasRequestedPermission = false,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun ordinaryDenialRemainsRequestable() {
        assertEquals(
            LocationPermissionState.Requestable,
            locationPermissionState(
                isFineLocationGranted = false,
                isCoarseLocationGranted = false,
                hasRequestedPermission = true,
                shouldShowRationale = true,
            ),
        )
    }

    @Test
    fun permanentDenialRequiresSettingsAfterARequest() {
        assertEquals(
            LocationPermissionState.SettingsRequired,
            locationPermissionState(
                isFineLocationGranted = false,
                isCoarseLocationGranted = false,
                hasRequestedPermission = true,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun approximateOnlyPermissionRemainsRequestable() {
        assertEquals(
            LocationPermissionState.Requestable,
            locationPermissionState(
                isFineLocationGranted = false,
                isCoarseLocationGranted = true,
                hasRequestedPermission = true,
                shouldShowRationale = false,
            ),
        )
    }
}
