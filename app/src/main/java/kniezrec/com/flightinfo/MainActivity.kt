package kniezrec.com.flightinfo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kniezrec.com.flightinfo.course.CourseController
import kniezrec.com.flightinfo.course.CourseState
import kniezrec.com.flightinfo.course.ForegroundCourseObservationCoordinator
import kniezrec.com.flightinfo.flight.AndroidFlightLocationPlatform
import kniezrec.com.flightinfo.flight.FlightParametersController
import kniezrec.com.flightinfo.flight.FlightParametersState
import kniezrec.com.flightinfo.gnss.AndroidGnssStatusPlatform
import kniezrec.com.flightinfo.gnss.GnssStatusController
import kniezrec.com.flightinfo.gnss.GnssStatusState
import kniezrec.com.flightinfo.horizon.HorizonController
import kniezrec.com.flightinfo.horizon.HorizonState
import kniezrec.com.flightinfo.map.MapArchiveRepository
import kniezrec.com.flightinfo.map.MapSessionRules
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
import kniezrec.com.flightinfo.ui.gnss.GnssStatusScreen
import kniezrec.com.flightinfo.ui.gnss.MapCardState
import kniezrec.com.flightinfo.ui.permission.PermissionOnboardingScreen
import kniezrec.com.flightinfo.ui.permission.smartFlightPageColor
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private var permissionState by mutableStateOf(LocationPermissionState.Requestable)
    private var announcementVersion by mutableIntStateOf(0)
    private var gnssState by mutableStateOf<GnssStatusState>(GnssStatusState.Waiting)
    private var flightParametersState by mutableStateOf<FlightParametersState>(FlightParametersState.Waiting)
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
                        if (permissionState == LocationPermissionState.Granted) {
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
                                        result.fold({ routeResults = it }, { routeSearchError = getString(R.string.route_error) })
                                    }
                                },
                                onRouteConfirm = { city ->
                                    routePicker?.let { endpoint ->
                                        if (routeController.choose(endpoint, city)) {
                                            routeNearestDraft = null
                                            routePicker = null
                                            true
                                        } else false
                                    } ?: false
                                },
                                onRouteCancel = { routeNearestDraft = null; routePicker = null },
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
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isForeground = true
        refreshPermissionState()
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
        gnssStatusController.stop()
        courseObservationCoordinator.stop()
        routeController.stop()
        horizonController.stop()
        super.onPause()
    }

    override fun onDestroy() {
        cityLookupExecutor.shutdownNow()
        mapArchiveRepository.close()
        super.onDestroy()
    }

    private fun refreshPermissionState(announceChange: Boolean = false) {
        permissionState = permissionStateController.currentState()
        if (isForeground && permissionState == LocationPermissionState.Granted) {
            startObservation()
        } else {
            gnssStatusController.stop()
            courseObservationCoordinator.stop()
            routeController.stop()
            horizonController.stop()
            stopMap()
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
            onStateChanged = { flightParametersState = it },
            onRegistrationFailed = { gnssStatusController.showError() },
            onLocationFix = {
                courseController.onGpsBearing(it.bearingDegrees)
                nearbyCityController.onLocationFix(it)
                routeController.onFix(it)
                if (mapRules.accept(it)) mapPositionVersion++
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

    private fun startObservation() {
        routeController.start()
        gnssStatusController.start()
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
