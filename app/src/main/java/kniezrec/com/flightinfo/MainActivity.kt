package kniezrec.com.flightinfo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kniezrec.com.flightinfo.about.AboutIntentFactory
import kniezrec.com.flightinfo.about.AndroidExternalIntentLauncher
import kniezrec.com.flightinfo.about.AppVersionProvider
import kniezrec.com.flightinfo.display.data.DisplaySettingsRepository
import kniezrec.com.flightinfo.display.ui.applyDisplayPreferences
import kniezrec.com.flightinfo.monitoring.AppVisibility
import kniezrec.com.flightinfo.monitoring.LocationForegroundService
import kniezrec.com.flightinfo.permission.AndroidFineLocationPermissionPlatform
import kniezrec.com.flightinfo.permission.LocationPermissionState
import kniezrec.com.flightinfo.permission.locationPermissionRequest
import kniezrec.com.flightinfo.permission.snapshot
import kniezrec.com.flightinfo.permission.ui.LocationPermissionViewModel
import kniezrec.com.flightinfo.settings.ui.SettingsViewModel
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The app's only Activity. It keeps what is bound to Android: the content ([AppRoot]), the
 * permission launcher and the permission refresh on resume, window effects of the display
 * settings, starting and stopping background monitoring, external intents and [AppVisibility].
 * Screen state lives in the ViewModels the composables obtain.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Injected in super.onCreate(); nothing below may touch them earlier.
    @Inject lateinit var displaySettingsRepository: DisplaySettingsRepository

    @Inject lateinit var appVersionProvider: AppVersionProvider

    @Inject lateinit var appVisibility: AppVisibility

    private val permissionViewModel: LocationPermissionViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val permissionPlatform = AndroidFineLocationPermissionPlatform(this)
    private val externalIntentLauncher = AndroidExternalIntentLauncher(this)
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshPermissionState(announceChange = true)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent bars with light icons in every system theme: the app's top bar draws the card
        // color behind the status bar and the page shows behind the navigation bar (no white scrim).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
        )
        refreshPermissionState()
        // Synchronous current value: window flags and orientation are set before the first frame.
        applyDisplayPreferences(displaySettingsRepository.display.value)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                displaySettingsRepository.display.collect { applyDisplayPreferences(it) }
            }
        }
        lifecycleScope.launch {
            // Resumed only, as before: switching the notification on starts monitoring while visible.
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                settingsViewModel.backgroundMonitoringRequests.collect {
                    if (permissionViewModel.state.value == LocationPermissionState.Granted) startBackgroundMonitoring()
                }
            }
        }
        val aboutVersion = appVersionProvider.read()
        setContent {
            SmartFlightTheme {
                AppRoot(
                    onRequestLocationPermission = { requestLocationPermission() },
                    onOpenAppSettings = { openAppSettings() },
                    onOpenLocationSettings = { openLocationSettings() },
                    aboutVersion = aboutVersion,
                    onSendFeedback = { address -> externalIntentLauncher.launch(AboutIntentFactory.feedback(address)) },
                    onRate = { externalIntentLauncher.launchRate(packageName) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        appVisibility.setVisible(true)
        if (refreshPermissionState() == LocationPermissionState.Granted) {
            startBackgroundMonitoring()
        } else {
            stopBackgroundMonitoring()
        }
    }

    override fun onPause() {
        appVisibility.setVisible(false)
        super.onPause()
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            stopBackgroundMonitoring()
        }
        super.onDestroy()
    }

    /** Reads the platform permission state now (the rationale check is Activity-bound). */
    private fun refreshPermissionState(announceChange: Boolean = false): LocationPermissionState {
        val state = permissionViewModel.refresh(permissionPlatform.snapshot(), announceChange)
        if (state != LocationPermissionState.Granted) stopBackgroundMonitoring()
        return state
    }

    private fun openAppSettings(): Boolean =
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                },
            )
        }.isSuccess

    private fun openLocationSettings(): Boolean = runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }.isSuccess

    private fun requestLocationPermission() {
        // Record the launch before invoking the platform dialog so a later process restart can
        // distinguish a first launch from Android's no-rationale, settings-required state.
        permissionViewModel.onRequestLaunched()
        permissionLauncher.launch(locationPermissionRequest)
    }

    private fun startBackgroundMonitoring() {
        runCatching {
            ContextCompat.startForegroundService(this, Intent(this, LocationForegroundService::class.java))
        }
    }

    private fun stopBackgroundMonitoring() {
        stopService(Intent(this, LocationForegroundService::class.java))
    }
}
