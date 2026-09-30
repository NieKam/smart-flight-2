package kniezrec.com.flightinfo.permission

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/** [FineLocationPermissionPlatform] over [activity]; the rationale check needs an Activity. */
internal class AndroidFineLocationPermissionPlatform(
    private val activity: Activity,
) : FineLocationPermissionPlatform {
    override fun isFineLocationGranted(): Boolean = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)

    override fun isCoarseLocationGranted(): Boolean = isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    override fun shouldShowFineLocationRationale(): Boolean =
        activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
}
