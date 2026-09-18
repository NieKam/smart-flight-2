package kniezrec.com.flightinfo

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationPermissionStateTest {
    @Test
    fun grantedPermissionShowsGrantedState() {
        assertEquals(
            LocationPermissionState.Granted,
            locationPermissionState(
                isGranted = true,
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
                isGranted = false,
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
                isGranted = false,
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
                isGranted = false,
                hasRequestedPermission = true,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun persistedRequestHistoryKeepsPermanentDenialInSettingsStateAfterRelaunch() {
        assertEquals(
            LocationPermissionState.SettingsRequired,
            locationPermissionState(
                isGranted = false,
                hasRequestedPermission = true,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun grantInSettingsShowsGrantedStateWhenTheActivityResumes() {
        assertEquals(
            LocationPermissionState.Granted,
            locationPermissionState(
                isGranted = true,
                hasRequestedPermission = true,
                shouldShowRationale = false,
            ),
        )
    }
}
