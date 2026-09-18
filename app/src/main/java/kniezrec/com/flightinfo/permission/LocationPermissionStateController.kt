package kniezrec.com.flightinfo.permission

/** Platform checks needed to render the location-permission entry state. */
internal interface FineLocationPermissionPlatform {
    fun isFineLocationGranted(): Boolean

    fun shouldShowFineLocationRationale(): Boolean
}

/** Durable record that distinguishes a first launch from a no-rationale denial. */
internal interface LocationPermissionRequestHistory {
    var hasRequestedFineLocation: Boolean
}

/**
 * Small lifecycle boundary for platform permission checks and durable request history.
 *
 * The activity owns the Android implementations; keeping this logic independent makes a
 * recreation and a return from Settings testable without adding a screen architecture layer.
 */
internal class LocationPermissionStateController(
    private val platform: FineLocationPermissionPlatform,
    private val requestHistory: LocationPermissionRequestHistory,
) {
    fun currentState(): LocationPermissionState =
        locationPermissionState(
            isGranted = platform.isFineLocationGranted(),
            hasRequestedPermission = requestHistory.hasRequestedFineLocation,
            shouldShowRationale = platform.shouldShowFineLocationRationale(),
        )

    fun recordPermissionRequest() {
        requestHistory.hasRequestedFineLocation = true
    }
}
