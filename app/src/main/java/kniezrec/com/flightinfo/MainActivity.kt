package kniezrec.com.flightinfo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.SensorManager
import android.location.LocationManager
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
import kniezrec.com.flightinfo.about.AboutIntentFactory
import kniezrec.com.flightinfo.about.AndroidAppVersionProvider
import kniezrec.com.flightinfo.about.AndroidExternalIntentLauncher
import kniezrec.com.flightinfo.course.CourseController
import kniezrec.com.flightinfo.course.CourseState
import kniezrec.com.flightinfo.course.ForegroundCourseObservationCoordinator
import kniezrec.com.flightinfo.display.DisplayEffectSink
import kniezrec.com.flightinfo.display.DisplayPreferences
import kniezrec.com.flightinfo.display.DisplayPreferencesApplier
import kniezrec.com.flightinfo.display.DisplayPreferencesStore
import kniezrec.com.flightinfo.displayunits.UnitPreferences
import kniezrec.com.flightinfo.displayunits.UnitPreferencesStore
import kniezrec.com.flightinfo.flight.AndroidFlightLocationPlatform
import kniezrec.com.flightinfo.flight.AndroidPressurePlatform
import kniezrec.com.flightinfo.flight.FlightParametersController
import kniezrec.com.flightinfo.flight.FlightParametersState
import kniezrec.com.flightinfo.flight.PressureController
import kniezrec.com.flightinfo.gnss.AndroidGnssStatusPlatform
import kniezrec.com.flightinfo.gnss.GnssStatusController
import kniezrec.com.flightinfo.gnss.GnssStatusState
import kniezrec.com.flightinfo.horizon.HorizonController
import kniezrec.com.flightinfo.horizon.HorizonState
import kniezrec.com.flightinfo.map.MapArchiveRepository
import kniezrec.com.flightinfo.map.MapSessionRules
import kniezrec.com.flightinfo.monitoring.BackgroundMonitoringBridge
import kniezrec.com.flightinfo.monitoring.BackgroundNotificationPreferences
import kniezrec.com.flightinfo.monitoring.BackgroundNotificationPreferencesStore
import kniezrec.com.flightinfo.monitoring.LocationForegroundService
import kniezrec.com.flightinfo.nearby.AndroidNearbyCityRepository
import kniezrec.com.flightinfo.nearby.NearbyCityController
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.NearbyCityState
import kniezrec.com.flightinfo.orientation.AndroidOrientationSource
import kniezrec.com.flightinfo.orientation.SharedCourseOrientationPlatform
import kniezrec.com.flightinfo.orientation.SharedHorizonOrientationPlatform
import kniezrec.com.flightinfo.permission.FineLocationPermissionPlatform
import kniezrec.com.flightinfo.permission.LocationPermissionRequestHistory
import kniezrec.com.flightinfo.permission.LocationPermissionState
import kniezrec.com.flightinfo.permission.LocationPermissionStateController
import kniezrec.com.flightinfo.permission.locationPermissionRequest
import kniezrec.com.flightinfo.route.RouteController
import kniezrec.com.flightinfo.route.RouteEndpoint
import kniezrec.com.flightinfo.route.RouteState
import kniezrec.com.flightinfo.route.validCity
import kniezrec.com.flightinfo.ui.about.AboutDialog
import kniezrec.com.flightinfo.ui.gnss.GnssStatusScreen
import kniezrec.com.flightinfo.ui.gnss.MapCardState
import kniezrec.com.flightinfo.ui.permission.PermissionOnboardingScreen
import kniezrec.com.flightinfo.ui.permission.smartFlightPageColor
import kniezrec.com.flightinfo.ui.settings.UnitSettingsScreen
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private var permissionState by mutableStateOf(LocationPermissionState.Requestable)
    private var announcementVersion by mutableIntStateOf(0)
    private var gnssState by mutableStateOf<GnssStatusState>(GnssStatusState.Waiting)
    private var flightParametersState by mutableStateOf<FlightParametersState>(FlightParametersState.Waiting)
    private var pressureMillibars: Double? = null
    private var courseState by mutableStateOf<CourseState>(CourseState.Waiting)
    private var horizonState by mutableStateOf<HorizonState>(HorizonState.Waiting)
    private var nearbyCityState by mutableStateOf<NearbyCityState>(NearbyCityState.WaitingForPosition)
    private var mapState by mutableStateOf<MapCardState>(MapCardState.Inactive)
    private var routeState by mutableStateOf(RouteState())
    private var routePicker by mutableStateOf<RouteEndpoint?>(null)
    private var routeResults by mutableStateOf<List<NearbyCityRecord>>(emptyList())
    private var routeSearchLoading by mutableStateOf(false)
    private var routeSearchError by mutableStateOf<String?>(null)
    private var routeNearestDraft by mutableStateOf<NearbyCityRecord?>(null)
    private var showUnitSettings by mutableStateOf(false)
    private var showAbout by mutableStateOf(false)
    private var unitPreferences by mutableStateOf(UnitPreferences())
    private var displayPreferences by mutableStateOf(DisplayPreferences())
    private var backgroundNotificationPreferences by mutableStateOf(BackgroundNotificationPreferences())
    private val displayPreferencesApplier by lazy {
        DisplayPreferencesApplier(
            sink =
                object : DisplayEffectSink {
                    override fun setKeepScreenAlwaysOn(enabled: Boolean) {
                        if (enabled) {
                            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }

                    override fun requestOrientation(orientation: Int) {
                        requestedOrientation = orientation
                    }
                },
            portraitOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            sensorOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR,
        )
    }
    private var lastRouteSearchQuery = ""
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
        BackgroundMonitoringBridge.setEligibilityLostHandler {
            flightParametersController.stop()
            gnssStatusController.stop()
        }
        BackgroundMonitoringBridge.setEventHandlers(
            onLocation = flightParametersController::acceptLocationFix,
            onGnssStatus = gnssStatusController::acceptStatus,
        )
        unitPreferences = unitPreferencesStore.read()
        displayPreferences = displayPreferencesStore.read()
        backgroundNotificationPreferences = backgroundNotificationPreferencesStore.read()
        applyDisplayPreferences()
        setContent {
            SmartFlightTheme {
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
                            // Settings is an overlay so the dashboard's AndroidView-backed map remains
                            // composed. This preserves its viewport, overlays, and in-place zoom policy.
                            Box(Modifier.fillMaxSize()) {
                                if (showUnitSettings) {
                                    UnitSettingsScreen(
                                        preferences = unitPreferences,
                                        onPreferenceChange = { value ->
                                            unitPreferencesStore.write(value)
                                            unitPreferences = value
                                        },
                                        displayPreferences = displayPreferences,
                                        onDisplayPreferenceChange = { value ->
                                            displayPreferencesStore.write(value)
                                            displayPreferences = value
                                            applyDisplayPreferences()
                                        },
                                        showBackgroundNotification = backgroundNotificationPreferences.showBackgroundNotification,
                                        onBackgroundNotificationChange = { enabled ->
                                            val value = BackgroundNotificationPreferences(enabled)
                                            backgroundNotificationPreferencesStore.write(value)
                                            backgroundNotificationPreferences = value
                                            BackgroundMonitoringBridge.setNotificationEnabled(enabled)
                                            if (enabled && isForeground && permissionState == LocationPermissionState.Granted) {
                                                startBackgroundMonitoring()
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
                                    onCourseRetry = { courseController.retry(isForeground) },
                                    horizonState = horizonState,
                                    onHorizonCalibrate = { horizonController.calibrate() },
                                    onHorizonRetry = { horizonController.retry(isForeground) },
                                    nearbyCityState = nearbyCityState,
                                    onNearbyCityRetry = { nearbyCityController.retry() },
                                    mapState = mapState,
                                    mapRules = mapRules,
                                    largerMapZoom = displayPreferences.largerMapZoom,
                                    mapPositionVersion = mapPositionVersion,
                                    onMapRetry = { startMapLoad() },
                                    onMapUnavailable = { mapState = MapCardState.Unavailable },
                                    routeState = routeState,
                                    onRouteChoose = {
                                        routePicker = it
                                        routeResults = emptyList()
                                        routeSearchError = null
                                        routeNearestDraft = null
                                        lastRouteSearchQuery = ""
                                    },
                                    onRouteClear = { routeController.clear(it) },
                                    onRouteClearAll = { routeController.clearRoute() },
                                    routePicker = routePicker,
                                    routePickerInitial =
                                        if (routePicker ==
                                            RouteEndpoint.DEPARTURE
                                        ) {
                                            routeState.departure
                                        } else {
                                            routeState.destination
                                        },
                                    routeSearchResults = routeResults,
                                    routeSearchLoading = routeSearchLoading,
                                    routeSearchError = routeSearchError,
                                    onRouteSearch = { query ->
                                        lastRouteSearchQuery = query
                                        routeSearchLoading = true
                                        routeSearchError = null
                                        routeController.search(query) { result ->
                                            routeSearchLoading = false
                                            result.fold(
                                                { routeResults = it },
                                                { routeSearchError = getString(R.string.route_error) },
                                            )
                                        }
                                    },
                                    onRouteConfirm = { city ->
                                        routePicker?.let { endpoint ->
                                            if (routeController.choose(endpoint, city)) {
                                                routeNearestDraft = null
                                                routePicker = null
                                                true
                                            } else {
                                                false
                                            }
                                        } ?: false
                                    },
                                    onRouteCancel = {
                                        routeNearestDraft = null
                                        routePicker = null
                                    },
                                    onRouteRetry = {
                                        routePicker?.let {
                                            routeSearchLoading = true
                                            routeSearchError = null
                                            routeController.search(lastRouteSearchQuery, reload = true) { result ->
                                                routeSearchLoading = false
                                                result.fold(
                                                    { routeResults = it },
                                                    { routeSearchError = getString(R.string.route_error) },
                                                )
                                            }
                                        }
                                    },
                                    onRouteRestoreRetry = { routeController.retryRestore() },
                                    onRouteNearest = { coordinate ->
                                        routeSearchLoading = true
                                        routeSearchError = null
                                        routeController.nearest(coordinate) { result ->
                                            routeSearchLoading = false
                                            result.fold(
                                                { city ->
                                                    if (city == null) {
                                                        routeNearestDraft = null
                                                        routeResults = emptyList()
                                                        routeSearchError = getString(R.string.route_no_city_at_location)
                                                    } else if (!validCity(city)) {
                                                        routeNearestDraft = null
                                                        routeResults = emptyList()
                                                        routeSearchError = getString(R.string.route_invalid_city)
                                                    } else {
                                                        routeNearestDraft = city
                                                        routeResults = listOf(city)
                                                    }
                                                },
                                                { routeSearchError = getString(R.string.route_error) },
                                            )
                                        }
                                    },
                                    routeNearestLoading = routeSearchLoading,
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
        BackgroundMonitoringBridge.beginSession()
        BackgroundMonitoringBridge.setActivityVisible(true)
        refreshPermissionState()
        unitPreferences = unitPreferencesStore.read()
        displayPreferences = displayPreferencesStore.read()
        backgroundNotificationPreferences = backgroundNotificationPreferencesStore.read()
        applyDisplayPreferences()
        if (permissionState == LocationPermissionState.Granted) {
            startBackgroundMonitoring()
        } else {
            stopBackgroundMonitoring()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isForeground && permissionState == LocationPermissionState.Granted) {
            horizonController.onDisplayRotationChanged()
        }
    }

    override fun onPause() {
        stopMap()
        isForeground = false
        pressureController.stop()
        courseObservationCoordinator.stopForegroundOnly()
        routeController.stop()
        horizonController.stop()
        BackgroundMonitoringBridge.setActivityVisible(false)
        super.onPause()
    }

    override fun onDestroy() {
        pressureController.stop()
        cityLookupExecutor.shutdownNow()
        mapArchiveRepository.close()
        if (!isChangingConfigurations) {
            BackgroundMonitoringBridge.clear()
            BackgroundMonitoringBridge.clearEventHandlers()
            stopBackgroundMonitoring()
        }
        super.onDestroy()
    }

    private fun refreshPermissionState(announceChange: Boolean = false) {
        permissionState = permissionStateController.currentState()
        if (isForeground && permissionState == LocationPermissionState.Granted) {
            startObservation()
        } else if (permissionState != LocationPermissionState.Granted || isForeground) {
            gnssStatusController.stop()
            pressureController.stop()
            courseObservationCoordinator.stop()
            routeController.stop()
            horizonController.stop()
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
            requestHistory =
                object : LocationPermissionRequestHistory {
                    override var hasRequestedFineLocation: Boolean
                        get() = permissionPreferences.getBoolean(HAS_REQUESTED_PERMISSION, false)
                        set(value) {
                            permissionPreferences.edit().putBoolean(HAS_REQUESTED_PERMISSION, value).apply()
                        }
                },
        )
    }

    private val displayPreferencesStore by lazy {
        DisplayPreferencesStore(getSharedPreferences("display_behavior", MODE_PRIVATE))
    }

    private val backgroundNotificationPreferencesStore by lazy {
        BackgroundNotificationPreferencesStore(getSharedPreferences(BackgroundNotificationPreferencesStore.PREFERENCES_NAME, MODE_PRIVATE))
    }

    private val unitPreferencesStore by lazy {
        UnitPreferencesStore(getSharedPreferences("display_units", MODE_PRIVATE))
    }

    private val permissionPreferences by lazy {
        getSharedPreferences(PERMISSION_PREFERENCES, MODE_PRIVATE)
    }

    private val gnssStatusController by lazy {
        GnssStatusController(
            platform = AndroidGnssStatusPlatform(getSystemService(LocationManager::class.java), packageManager, mainExecutor),
            onStateChanged = { gnssState = it },
        )
    }

    private val flightParametersController by lazy {
        FlightParametersController(
            platform = AndroidFlightLocationPlatform(getSystemService(LocationManager::class.java), packageManager, mainExecutor),
            onStateChanged = { state ->
                flightParametersState =
                    (state as? FlightParametersState.Readings)?.copy(pressureMillibars = pressureMillibars) ?: state
            },
            onRegistrationFailed = { gnssStatusController.showError() },
            onLocationFix = {
                BackgroundMonitoringBridge.onUsableLocationFix(it)
                courseController.onGpsBearing(it.bearingDegrees)
                nearbyCityController.onLocationFix(it)
                routeController.onFix(it)
                if (mapRules.accept(it)) mapPositionVersion++
            },
        )
    }

    private val pressureController by lazy {
        PressureController(
            platform = AndroidPressurePlatform(getSystemService(SensorManager::class.java), mainExecutor),
            onPressureChanged = { pressure ->
                pressureMillibars = pressure
                flightParametersState =
                    (flightParametersState as? FlightParametersState.Readings)?.copy(
                        pressureMillibars = pressure,
                    ) ?: flightParametersState
            },
        )
    }

    private val courseController by lazy {
        CourseController(SharedCourseOrientationPlatform(orientationSource)) { courseState = it }
    }

    private val cityLookupExecutor by lazy { Executors.newSingleThreadExecutor() }

    private val routeController by lazy {
        RouteController(
            repository = AndroidNearbyCityRepository(applicationContext),
            preferences = routePreferences,
            worker = cityLookupExecutor,
            callbackExecutor = mainExecutor,
            onStateChanged = { routeState = it },
        )
    }

    private val routePreferences by lazy { getSharedPreferences("route", MODE_PRIVATE) }

    private val nearbyCityController by lazy {
        NearbyCityController(
            repository = AndroidNearbyCityRepository(applicationContext),
            worker = cityLookupExecutor,
            callbackExecutor = mainExecutor,
            onStateChanged = { nearbyCityState = it },
        )
    }

    private val orientationSource by lazy { AndroidOrientationSource(this, mainExecutor) }

    private val horizonController by lazy {
        HorizonController(SharedHorizonOrientationPlatform(orientationSource)) { horizonState = it }
    }

    private val courseObservationCoordinator by lazy {
        ForegroundCourseObservationCoordinator(flightParametersController, courseController, nearbyCityController)
    }

    private fun applyDisplayPreferences() {
        displayPreferencesApplier.apply(displayPreferences, requestedOrientation)
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
        pressureController.start()
        routeController.start()
        gnssStatusController.attachToExternalSession()
        flightParametersController.attachToExternalSession()
        if (gnssState is GnssStatusState.Waiting || gnssState is GnssStatusState.Available) {
            courseObservationCoordinator.start()
        } else {
            courseObservationCoordinator.stop()
        }
        horizonController.start()
        startMapLoad()
    }

    private val mapArchiveRepository by lazy { MapArchiveRepository(applicationContext) }

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

    private companion object {
        const val HAS_REQUESTED_PERMISSION = "has_requested_location_permission"
        const val PERMISSION_PREFERENCES = "location_permission"
    }
}
