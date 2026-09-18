package kniezrec.com.flightinfo.permission

import android.Manifest

/**
 * Fine location remains the app's only authorization requirement. Android 12+ requires this
 * companion coarse declaration in the same request before it will show the location prompt.
 */
internal val locationPermissionRequest =
    arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
