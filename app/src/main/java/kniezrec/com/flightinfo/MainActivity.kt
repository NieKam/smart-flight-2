package kniezrec.com.flightinfo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableIntStateOf
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
import kniezrec.com.flightinfo.about.AndroidAppVersionProvider
import kniezrec.com.flightinfo.about.AndroidExternalIntentLauncher
import kniezrec.com.flightinfo.course.ui.CourseViewModel
import kniezrec.com.flightinfo.display.data.DisplaySettingsRepository
import kniezrec.com.flightinfo.display.ui.applyDisplayPreferences
import kniezrec.com.flightinfo.displayunits.data.UnitSettingsRepository
import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.flight.ui.FlightParametersViewModel
import kniezrec.com.flightinfo.gnss.ui.GnssStatusViewModel
import kniezrec.com.flightinfo.horizon.ui.HorizonViewModel
import kniezrec.com.flightinfo.location.data.LocationRegistrationException
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.map.MapArchiveRepository
import kniezrec.com.flightinfo.map.MapSessionRules
import kniezrec.com.flightinfo.monitoring.AppVisibility
import kniezrec.com.flightinfo.monitoring.LocationForegroundService
import kniezrec.com.flightinfo.monitoring.data.BackgroundNotificationSettingsRepository
import kniezrec.com.flightinfo.nearby.ui.NearbyCityViewModel
import kniezrec.com.flightinfo.permission.FineLocationPermissionPlatform
import kniezrec.com.flightinfo.permission.LocationPermissionRequestHistory
import kniezrec.com.flightinfo.permission.LocationPermissionState
import kniezrec.com.flightinfo.permission.LocationPermissionStateController
import kniezrec.com.flightinfo.permission.locationPermissionRequest
import kniezrec.com.flightinfo.route.ui.RoutePickerViewModel
import kniezrec.com.flightinfo.route.ui.RouteViewModel
import kniezrec.com.flightinfo.ui.about.AboutDialog
import kniezrec.com.flightinfo.ui.gnss.GnssStatusScreen
import kniezrec.com.flightinfo.ui.gnss.MapCardState
import kniezrec.com.flightinfo.ui.permission.PermissionOnboardingScreen
import kniezrec.com.flightinfo.ui.permission.smartFlightPageColor
import kniezrec.com.flightinfo.ui.settings.UnitSettingsScreen
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Injected in super.onCreate(); nothing below may touch them earlier.
    @Inject lateinit var displaySettingsRepository: DisplaySettingsRepository

    @Inject lateinit var unitSettingsRepository: UnitSettingsRepository

    @Inject lateinit var backgroundNotificationSettingsRepository: BackgroundNotificationSettingsRepository

    @Inject lateinit var permissionRequestHistory: LocationPermissionRequestHistory

    @Inject lateinit var mapArchiveRepository: MapArchiveRepository

    @Inject lateinit var locationRepository: LocationRepository

    @Inject lateinit var appVisibility: AppVisibility

    private var permissionState by mutableStateOf(LocationPermissionState.Requestable)
    private var announcementVersion by mutableIntStateOf(0)
    private var mapState by mutableStateOf<MapCardState>(MapCardState.Inactive)
    private var showUnitSettings by mutableStateOf(false)
    private var showAbout by mutableStateOf(false)
    private val mapRules = MapSessionRules()
    private var mapLoadToken = 0L
    private var mapPositionVersion by mutableIntStateOf(0)
    private var isForeground by mutableStateOf(false)
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
            // Starts after onResume (observation already started) and ends before onPause.
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (permissionState == LocationPermissionState.Granted) collectLocation()
            }
        }
        setContent {
            SmartFlightTheme {
                val unitPreferences by unitSettingsRepository.units.collectAsStateWithLifecycle()
                val displayPreferences by displaySettingsRepository.display.collectAsStateWithLifecycle()
                val backgroundNotificationPreferences by
                    backgroundNotificationSettingsRepository.settings.collectAsStateWithLifecycle()
                BackHandler(enabled = showUnitSettings) { showUnitSettings = false }
                BackHandler(enabled = showAbout) { showAbout = false }
                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                val aboutVersion = remember { AndroidAppVersionProvider(this).read() }
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
                            val gnssState by gnssStatusViewModel.state.collectAsStateWithLifecycle()
                            val flightParametersState by flightParametersViewModel.state.collectAsStateWithLifecycle()
                            val courseState by courseViewModel.state.collectAsStateWithLifecycle()
                            val horizonState by horizonViewModel.state.collectAsStateWithLifecycle()
                            val nearbyCityState by nearbyCityViewModel.state.collectAsStateWithLifecycle()
                            val routeState by routeViewModel.state.collectAsStateWithLifecycle()
                            // Immediate, so the search field shows each typed character before the next input event.
                            val routePickerState by routePickerViewModel.state.collectAsStateWithLifecycle(
                                context = Dispatchers.Main.immediate,
                            )
                            // Settings is an overlay so the dashboard's AndroidView-backed map remains
                            // composed. This preserves its viewport, overlays, and in-place zoom policy.
                            Box(Modifier.fillMaxSize()) {
                                if (showUnitSettings) {
                                    UnitSettingsScreen(
                                        preferences = unitPreferences,
                                        onPreferenceChange = { value ->
                                            lifecycleScope.launch { unitSettingsRepository.setUnits(value) }
                                        },
                                        displayPreferences = displayPreferences,
                                        onDisplayPreferenceChange = { value ->
                                            // Window effects follow from the display collector above.
                                            lifecycleScope.launch { displaySettingsRepository.set(value) }
                                        },
                                        showBackgroundNotification = backgroundNotificationPreferences.showBackgroundNotification,
                                        onBackgroundNotificationChange = { enabled ->
                                            lifecycleScope.launch {
                                                // The running service observes the setting itself.
                                                backgroundNotificationSettingsRepository.setShowBackgroundNotification(enabled)
                                                if (enabled && isForeground && permissionState == LocationPermissionState.Granted) {
                                                    startBackgroundMonitoring()
                                                }
                                            }
                                        },
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
                                    mapRules = mapRules,
                                    largerMapZoom = displayPreferences.largerMapZoom,
                                    mapPositionVersion = mapPositionVersion,
                                    onMapRetry = { startMapLoad() },
                                    onMapUnavailable = { mapState = MapCardState.Unavailable },
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
                                    routePickerMapArchive = (mapState as? MapCardState.Ready)?.archive,
                                    onOpenSettings = { showUnitSettings = true },
                                    onOpenAbout = { showAbout = true },
                                    unitPreferences = unitPreferences,
                                    onOpenLocationSettings = {
                                        if (!openLocationSettings()) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    getString(R.string.location_settings_unavailable),
                                                )
                                            }
                                        }
                                    },
                                    onRetry = { if (isForeground) startObservation() },
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
                                announceStateChange = announcementVersion > 0,
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
        isForeground = true
        appVisibility.setVisible(true)
        refreshPermissionState()
        if (permissionState == LocationPermissionState.Granted) {
            startBackgroundMonitoring()
        } else {
            stopBackgroundMonitoring()
        }
    }

    override fun onPause() {
        stopMap()
        isForeground = false
        appVisibility.setVisible(false)
        super.onPause()
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            stopBackgroundMonitoring()
        }
        super.onDestroy()
    }

    private fun refreshPermissionState(announceChange: Boolean = false) {
        permissionState = permissionStateController.currentState()
        if (isForeground && permissionState == LocationPermissionState.Granted) {
            startObservation()
        } else if (permissionState != LocationPermissionState.Granted || isForeground) {
            stopMap()
            stopBackgroundMonitoring()
        }
        if (announceChange) announcementVersion++
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
        permissionStateController.recordPermissionRequest()
        permissionLauncher.launch(locationPermissionRequest)
    }

    private val permissionStateController by lazy {
        LocationPermissionStateController(
            platform =
                object : FineLocationPermissionPlatform {
                    override fun isFineLocationGranted(): Boolean =
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED

                    override fun isCoarseLocationGranted(): Boolean =
                        ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED

                    override fun shouldShowFineLocationRationale(): Boolean =
                        shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                },
            requestHistory = permissionRequestHistory,
        )
    }

    private fun startBackgroundMonitoring() {
        runCatching {
            ContextCompat.startForegroundService(this, Intent(this, LocationForegroundService::class.java))
        }
    }

    private fun stopBackgroundMonitoring() {
        stopService(Intent(this, LocationForegroundService::class.java))
    }

    private fun startObservation() {
        startMapLoad()
    }

    /**
     * Feeds the fixes of the shared location registration (the service and the card ViewModels
     * collect the same one) to the map. While the location is
     * switched off the fixes are not collected. A registration failure stops the feed until the
     * next resume.
     */
    private suspend fun collectLocation() {
        try {
            locationRepository.confirmedLocationEnabled.collectLatest { enabled ->
                if (enabled) locationRepository.fixes.collect { onLocationFix(it) }
            }
        } catch (_: LocationRegistrationException) {
            // Cards keep their state, as when the service failed to register before.
        }
    }

    private fun onLocationFix(fix: FlightLocationFix) {
        if (mapRules.accept(fix)) mapPositionVersion++
    }

    private fun startMapLoad() {
        if (!isForeground || permissionState != LocationPermissionState.Granted) return
        val token = ++mapLoadToken
        mapRules.reset()
        mapPositionVersion++
        mapState = MapCardState.Loading
        mapArchiveRepository.prepare { result ->
            mainExecutor.execute {
                if (token != mapLoadToken || !isForeground || permissionState != LocationPermissionState.Granted) return@execute
                mapState = result.fold({ MapCardState.Ready(it) }, { MapCardState.Unavailable })
            }
        }
    }

    private fun stopMap() {
        mapLoadToken++
        mapRules.reset()
        mapState = MapCardState.Inactive
    }
}
