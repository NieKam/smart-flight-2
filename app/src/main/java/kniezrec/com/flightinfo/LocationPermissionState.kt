package kniezrec.com.flightinfo

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
    isGranted: Boolean,
    hasRequestedPermission: Boolean,
    shouldShowRationale: Boolean,
): LocationPermissionState =
    when {
        isGranted -> LocationPermissionState.Granted
        hasRequestedPermission && !shouldShowRationale -> LocationPermissionState.SettingsRequired
        else -> LocationPermissionState.Requestable
    }
