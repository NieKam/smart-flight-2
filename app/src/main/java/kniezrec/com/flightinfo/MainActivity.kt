package kniezrec.com.flightinfo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme

class MainActivity : ComponentActivity() {
    private var hasRequestedPermission = false
    private var permissionState by mutableStateOf(LocationPermissionState.Requestable)
    private var announcementVersion by mutableIntStateOf(0)
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasRequestedPermission = true
        refreshPermissionState(announceChange = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hasRequestedPermission = savedInstanceState?.getBoolean(HAS_REQUESTED_PERMISSION) ?: false
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        refreshPermissionState()
        setContent {
            SmartFlightTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                Surface(modifier = Modifier.fillMaxSize(), color = smartFlightPageColor) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = smartFlightPageColor,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                    ) { innerPadding ->
                        PermissionOnboardingScreen(
                            state = permissionState,
                            onGrantPermission = {
                                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            },
                            onOpenSettings = {
                                if (!openAppSettings()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            getString(R.string.settings_unavailable),
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.padding(innerPadding).safeDrawingPadding(),
                            announceStateChange = announcementVersion > 0,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(HAS_REQUESTED_PERMISSION, hasRequestedPermission)
        super.onSaveInstanceState(outState)
    }

    private fun refreshPermissionState(announceChange: Boolean = false) {
        val isGranted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        permissionState = locationPermissionState(
            isGranted = isGranted,
            hasRequestedPermission = hasRequestedPermission,
            shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION),
        )
        if (announceChange) announcementVersion++
    }

    private fun openAppSettings(): Boolean = runCatching {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        })
    }.isSuccess

    private companion object {
        const val HAS_REQUESTED_PERMISSION = "has_requested_location_permission"
    }
}
