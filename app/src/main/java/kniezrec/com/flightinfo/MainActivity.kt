package kniezrec.com.flightinfo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kniezrec.com.flightinfo.about.AboutIntentFactory
import kniezrec.com.flightinfo.about.AndroidExternalIntentLauncher
import kniezrec.com.flightinfo.about.AppVersionProvider
import kniezrec.com.flightinfo.course.ui.CourseViewModel
import kniezrec.com.flightinfo.display.data.DisplaySettingsRepository
import kniezrec.com.flightinfo.display.ui.applyDisplayPreferences
import kniezrec.com.flightinfo.flight.ui.FlightParametersViewModel
import kniezrec.com.flightinfo.gnss.ui.GnssStatusViewModel
import kniezrec.com.flightinfo.horizon.ui.HorizonViewModel
import kniezrec.com.flightinfo.map.ui.MapUiState
import kniezrec.com.flightinfo.map.ui.MapViewModel
import kniezrec.com.flightinfo.monitoring.AppVisibility
import kniezrec.com.flightinfo.monitoring.LocationForegroundService
import kniezrec.com.flightinfo.nearby.ui.NearbyCityViewModel
import kniezrec.com.flightinfo.permission.AndroidFineLocationPermissionPlatform
import kniezrec.com.flightinfo.permission.LocationPermissionState
import kniezrec.com.flightinfo.permission.locationPermissionRequest
import kniezrec.com.flightinfo.permission.snapshot
import kniezrec.com.flightinfo.permission.ui.LocationPermissionViewModel
import kniezrec.com.flightinfo.route.ui.RoutePickerViewModel
import kniezrec.com.flightinfo.route.ui.RouteViewModel
import kniezrec.com.flightinfo.settings.ui.SettingsViewModel
import kniezrec.com.flightinfo.ui.about.AboutDialog
import kniezrec.com.flightinfo.ui.gnss.GnssStatusScreen
import kniezrec.com.flightinfo.ui.permission.PermissionOnboardingScreen
import kniezrec.com.flightinfo.ui.permission.smartFlightPageColor
import kniezrec.com.flightinfo.ui.settings.UnitSettingsScreen
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Injected in super.onCreate(); nothing below may touch them earlier.
    @Inject lateinit var displaySettingsRepository: DisplaySettingsRepository

    @Inject lateinit var appVersionProvider: AppVersionProvider

    @Inject lateinit var appVisibility: AppVisibility

    private val permissionViewModel: LocationPermissionViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val permissionPlatform = AndroidFineLocationPermissionPlatform(this)
    private var showUnitSettings by mutableStateOf(false)
    private var showAbout by mutableStateOf(false)
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshPermissionState(announceChange = true)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
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
        setContent {
            SmartFlightTheme {
                val permissionState by permissionViewModel.state.collectAsStateWithLifecycle()
                val announcePermissionChange by permissionViewModel.announceChange.collectAsStateWithLifecycle()
                val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
                BackHandler(enabled = showUnitSettings) { showUnitSettings = false }
                BackHandler(enabled = showAbout) { showAbout = false }
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val aboutVersion = remember { appVersionProvider.read() }
                val externalLauncher = remember { AndroidExternalIntentLauncher(this) }
                Surface(modifier = Modifier.fillMaxSize(), color = smartFlightPageColor) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = smartFlightPageColor,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                    ) { innerPadding ->
                        if (permissionState == LocationPermissionState.Granted) {
                            val gnssStatusViewModel: GnssStatusViewModel = hiltViewModel()
                            val flightParametersViewModel: FlightParametersViewModel = hiltViewModel()
                            val courseViewModel: CourseViewModel = hiltViewModel()
                            val horizonViewModel: HorizonViewModel = hiltViewModel()
                            val nearbyCityViewModel: NearbyCityViewModel = hiltViewModel()
                            val routeViewModel: RouteViewModel = hiltViewModel()
                            val routePickerViewModel: RoutePickerViewModel = hiltViewModel()
                            val mapViewModel: MapViewModel = hiltViewModel()
                            val gnssState by gnssStatusViewModel.state.collectAsStateWithLifecycle()
                            val flightParametersState by flightParametersViewModel.state.collectAsStateWithLifecycle()
                            val courseState by courseViewModel.state.collectAsStateWithLifecycle()
                            val horizonState by horizonViewModel.state.collectAsStateWithLifecycle()
                            val nearbyCityState by nearbyCityViewModel.state.collectAsStateWithLifecycle()
                            val routeState by routeViewModel.state.collectAsStateWithLifecycle()
                            val mapState by mapViewModel.state.collectAsStateWithLifecycle()
                            // Immediate, so the search field shows each typed character before the next input event.
                            val routePickerState by routePickerViewModel.state.collectAsStateWithLifecycle(
                                context = Dispatchers.Main.immediate,
                            )
                            // Settings is an overlay so the dashboard's AndroidView-backed map remains
                            // composed. This preserves its viewport, overlays, and in-place zoom policy.
                            Box(Modifier.fillMaxSize()) {
                                if (showUnitSettings) {
                                    UnitSettingsScreen(
                                        preferences = settingsState.units,
                                        onPreferenceChange = settingsViewModel::setUnits,
                                        displayPreferences = settingsState.display,
                                        // Window effects follow from the display collector above.
                                        onDisplayPreferenceChange = settingsViewModel::setDisplay,
                                        showBackgroundNotification = settingsState.showBackgroundNotification,
                                        onBackgroundNotificationChange = settingsViewModel::setShowBackgroundNotification,
                                        onBack = { showUnitSettings = false },
                                        modifier = Modifier.padding(innerPadding).safeDrawingPadding().zIndex(1f),
                                    )
                                }
                                GnssStatusScreen(
                                    state = gnssState,
                                    flightParametersState = flightParametersState,
                                    courseState = courseState,
                                    onCourseRetry = courseViewModel::retry,
                                    horizonState = horizonState,
                                    onHorizonCalibrate = horizonViewModel::calibrate,
                                    onHorizonRetry = horizonViewModel::retry,
                                    nearbyCityState = nearbyCityState,
                                    onNearbyCityRetry = nearbyCityViewModel::retry,
                                    mapState = mapState,
                                    onMapRetry = mapViewModel::retry,
                                    onMapUnavailable = mapViewModel::onMapOpenFailed,
                                    onMapCentered = mapViewModel::onCentered,
                                    routeState = routeState,
                                    onRouteChoose = routePickerViewModel::open,
                                    onRouteClear = routeViewModel::clear,
                                    onRouteClearAll = routeViewModel::clearAll,
                                    onRouteRestoreRetry = routeViewModel::retryRestore,
                                    routePickerState = routePickerState,
                                    onRoutePickerQueryChange = routePickerViewModel::updateQuery,
                                    onRouteSearch = routePickerViewModel::search,
                                    onRouteNearest = routePickerViewModel::nearest,
                                    onRouteSelect = routePickerViewModel::select,
                                    onRouteConfirm = { routePickerViewModel.confirm() },
                                    onRouteCancel = routePickerViewModel::close,
                                    onRouteRetry = routePickerViewModel::retry,
                                    routePickerMapArchive = (mapState as? MapUiState.Ready)?.archive,
                                    onOpenSettings = { showUnitSettings = true },
                                    onOpenAbout = { showAbout = true },
                                    unitPreferences = settingsState.units,
                                    onOpenLocationSettings = {
                                        if (!openLocationSettings()) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    getString(R.string.location_settings_unavailable),
                                                )
                                            }
                                        }
                                    },
                                    // As before: restarts the map observation (the GNSS card has no retry of its own).
                                    onRetry = mapViewModel::retry,
                                    modifier = Modifier.padding(innerPadding).safeDrawingPadding(),
                                )
                            }
                        } else {
                            PermissionOnboardingScreen(
                                state = permissionState,
                                onGrantPermission = { requestLocationPermission() },
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
                                announceStateChange = announcePermissionChange,
                            )
                        }
                    }
                    if (showAbout && permissionState == LocationPermissionState.Granted) {
                        AboutDialog(
                            version = aboutVersion,
                            onSendFeedback = {
                                externalLauncher.launch(
                                    AboutIntentFactory.feedback(getString(R.string.about_feedback_address)),
                                )
                            },
                            onRate = { externalLauncher.launchRate(packageName) },
                            onDismiss = { showAbout = false },
                        )
                    }
                }
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
