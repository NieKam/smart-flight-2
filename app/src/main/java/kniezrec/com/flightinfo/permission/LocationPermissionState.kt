package kniezrec.com.flightinfo.permission

/** The app-owned presentation state derived from Android's current permission state. */
enum class LocationPermissionState {
    Requestable,
    SettingsRequired,
    Granted,
}

/**
 * Android returns false from shouldShowRequestPermissionRationale both before the first request
 * and after a permanent denial. Request history distinguishes those two cases.
 */
fun locationPermissionState(
    isFineLocationGranted: Boolean,
    isCoarseLocationGranted: Boolean,
    hasRequestedPermission: Boolean,
    shouldShowRationale: Boolean,
): LocationPermissionState =
    when {
        isFineLocationGranted -> LocationPermissionState.Granted
        isCoarseLocationGranted -> LocationPermissionState.Requestable
        hasRequestedPermission && !shouldShowRationale -> LocationPermissionState.SettingsRequired
        else -> LocationPermissionState.Requestable
    }
