package kniezrec.com.flightinfo.permission

/** Platform checks needed to render the location-permission entry state. */
internal interface FineLocationPermissionPlatform {
    fun isFineLocationGranted(): Boolean

    fun isCoarseLocationGranted(): Boolean

    fun shouldShowFineLocationRationale(): Boolean
}

/** Durable record that distinguishes a first launch from a no-rationale denial. */
interface LocationPermissionRequestHistory {
    var hasRequestedFineLocation: Boolean
}

/**
 * The platform permission checks read at one moment. The rationale check is Activity-bound, so the
 * Activity reads a fresh snapshot on every refresh; it is never cached in a longer-lived object.
 */
data class PermissionSnapshot(
    val isFineLocationGranted: Boolean,
    val isCoarseLocationGranted: Boolean,
    val shouldShowRationale: Boolean,
)

internal fun FineLocationPermissionPlatform.snapshot(): PermissionSnapshot =
    PermissionSnapshot(
        isFineLocationGranted = isFineLocationGranted(),
        isCoarseLocationGranted = isCoarseLocationGranted(),
        shouldShowRationale = shouldShowFineLocationRationale(),
    )
