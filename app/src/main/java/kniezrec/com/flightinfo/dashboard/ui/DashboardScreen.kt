package kniezrec.com.flightinfo.dashboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.about.AppVersion
import kniezrec.com.flightinfo.about.ui.AboutDialog
import kniezrec.com.flightinfo.course.ui.CourseCardContainer
import kniezrec.com.flightinfo.dashboard.HideableCard
import kniezrec.com.flightinfo.flight.ui.FlightParametersCardContainer
import kniezrec.com.flightinfo.gnss.ui.GnssStatusCardContainer
import kniezrec.com.flightinfo.horizon.ui.HorizonCardContainer
import kniezrec.com.flightinfo.map.ui.MapCardContainer
import kniezrec.com.flightinfo.map.ui.MapViewModel
import kniezrec.com.flightinfo.map.ui.MapZoomTipHost
import kniezrec.com.flightinfo.nearby.ui.NearbyCityCardContainer
import kniezrec.com.flightinfo.route.ui.RouteCardContainer
import kniezrec.com.flightinfo.route.ui.RoutePickerOverlay
import kniezrec.com.flightinfo.route.ui.RoutePickerViewModel
import kniezrec.com.flightinfo.settings.ui.SettingsOverlay
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme
import kniezrec.com.flightinfo.ui.theme.rememberTopBarContainerColor
import kniezrec.com.flightinfo.ui.theme.smartFlightTopAppBarColors
import kniezrec.com.flightinfo.ui.theme.topBarBackground

/**
 * The dashboard: top app bar and the scrolling list of cards, with the city picker, Settings and About
 * as overlays. Every card container obtains its own (Activity-scoped) ViewModel; the open state of
 * Settings and About is saved, so it survives a configuration change (the picker keeps its own in
 * [RoutePickerViewModel]).
 *
 * While fine location is not granted ([permissionCard] not null) the dashboard shows, as the
 * original app, the permission card first and then only the cards that do not need location
 * (Course without GPS bearing, Horizon); Settings and About stay available. The location cards are
 * not composed, so their ViewModels are not created and nothing collects location. Course and
 * Horizon cards the user hid (device without their sensor) are not composed either.
 *
 * The map's max-zoom tip is shown at the top of the card list; its "Settings" action opens Settings
 * with the "Larger map zoom" row highlighted.
 *
 * @param permissionCard the location permission card, or null once location is granted.
 * @param onOpenLocationSettings action of the GNSS card when location is switched off, and "Yes" of
 *   the "Enable GPS" prompt shown when the dashboard starts with GPS off.
 * @param modifier insets of the dashboard and the Settings overlay (not of the About dialog).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    permissionCard: (@Composable (Modifier) -> Unit)?,
    onOpenLocationSettings: () -> Unit,
    aboutVersion: AppVersion,
    onSendFeedback: () -> Boolean,
    onRate: () -> Boolean,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    // Settings opened from the map's max-zoom tip: the "Larger map zoom" row flashes once.
    var highlightLargerMapZoom by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val closeSettings = {
        showSettings = false
        highlightLargerMapZoom = false
    }
    // Registered before the picker's own back handler, which therefore wins while it is open.
    BackHandler(enabled = showSettings, onBack = closeSettings)
    BackHandler(enabled = showAbout) { showAbout = false }
    val locationGranted = permissionCard == null
    val hiddenCards by viewModel.hiddenCards.collectAsStateWithLifecycle()
    // The top bar takes its scrolled tone while either card list scrolls under it.
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    // Settings is an overlay so the dashboard's AndroidView-backed map remains composed. This
    // preserves its viewport, overlays, and in-place zoom policy.
    Box(Modifier.fillMaxSize()) {
        if (showSettings) {
            SettingsOverlay(
                onBack = closeSettings,
                highlightLargerMapZoom = highlightLargerMapZoom,
                onHighlightFinished = { highlightLargerMapZoom = false },
                modifier = modifier.zIndex(1f),
            )
        }
        Box(Modifier.fillMaxSize().then(modifier)) {
            Column(Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
                DashboardHeader(
                    onOpenSettings = { showSettings = true },
                    onOpenAbout = { showAbout = true },
                    scrollBehavior = scrollBehavior,
                )
                val cardModifier = Modifier.padding(bottom = 12.dp).widthIn(max = 600.dp)
                if (permissionCard != null) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    ) {
                        // The permission card, then the cards that work without location, in the
                        // order of LocationDashboardCards.
                        DashboardSlot(DashboardCardTags.PERMISSION) { permissionCard(cardModifier) }
                        if (HideableCard.Course !in hiddenCards) {
                            DashboardSlot(DashboardCardTags.COURSE) {
                                CourseCardContainer(
                                    locationPermitted = false,
                                    onHide = { viewModel.hide(HideableCard.Course) },
                                    modifier = cardModifier,
                                )
                            }
                        }
                        if (HideableCard.Horizon !in hiddenCards) {
                            DashboardSlot(DashboardCardTags.HORIZON) {
                                HorizonCardContainer(onHide = { viewModel.hide(HideableCard.Horizon) }, cardModifier)
                            }
                        }
                    }
                } else {
                    LocationDashboardCards(
                        viewModel = viewModel,
                        hiddenCards = hiddenCards,
                        cardModifier = cardModifier,
                        onOpenLocationSettings = onOpenLocationSettings,
                        onOpenLargerMapZoomSetting = {
                            highlightLargerMapZoom = true
                            showSettings = true
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    if (locationGranted) {
        EnableGpsPrompt(isLocationEnabled = viewModel::isLocationEnabled, onOpenLocationSettings = onOpenLocationSettings)
    }
    if (showAbout) {
        AboutDialog(
            version = aboutVersion,
            onSendFeedback = onSendFeedback,
            onRate = onRate,
            onDismiss = { showAbout = false },
        )
    }
}

/**
 * Every card with location granted, the map's max-zoom tip at the top of the list, and the city
 * picker overlay over them. The expanded map fits in the list's viewport (this box's height).
 *
 * Card order (TASK-034, decided by the planner): Satellites, Course, Horizon, Flight parameters,
 * Nearby city, Route, Map. It is the original app's order (`CardViewContainer`) with only the
 * Satellites card moved to the top, as in the store screenshots:
 * - Satellites first: it is the status gate for everything below. Speed, altitude, nearby city,
 *   route progress and the map position all need a GPS fix; this card tells the user that the app
 *   is still waiting, that location is off, or to move closer to the window.
 * - Course and Horizon next: they work from sensors without a fix, so the top of the screen is
 *   useful from the first second; kept together in the original relative order.
 * - Then the fix-dependent data (Flight parameters, Nearby city, Route), and the Map last: it is
 *   the tallest card and would push everything else off-screen.
 *
 * Without location permission the dashboard shows the permission card, then Course and Horizon.
 * Course and Horizon cards the user hid are skipped in both layouts.
 */
@Composable
private fun LocationDashboardCards(
    viewModel: DashboardViewModel,
    hiddenCards: Set<HideableCard>,
    cardModifier: Modifier,
    onOpenLocationSettings: () -> Unit,
    onOpenLargerMapZoomSetting: () -> Unit,
    modifier: Modifier,
) {
    val units by viewModel.units.collectAsStateWithLifecycle()
    val routePickerViewModel: RoutePickerViewModel = hiltViewModel()
    val mapViewModel: MapViewModel = hiltViewModel()
    val zoomTip by mapViewModel.zoomTip.collectAsStateWithLifecycle()
    BoxWithConstraints(modifier) {
        val viewportHeight = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            // The card order: see the KDoc of this function.
            DashboardSlot(DashboardCardTags.SATELLITES) { GnssStatusCardContainer(onOpenLocationSettings = onOpenLocationSettings) }
            if (HideableCard.Course !in hiddenCards) {
                DashboardSlot(DashboardCardTags.COURSE) {
                    CourseCardContainer(
                        locationPermitted = true,
                        onHide = { viewModel.hide(HideableCard.Course) },
                        modifier = cardModifier,
                    )
                }
            }
            if (HideableCard.Horizon !in hiddenCards) {
                DashboardSlot(DashboardCardTags.HORIZON) {
                    HorizonCardContainer(onHide = { viewModel.hide(HideableCard.Horizon) }, cardModifier)
                }
            }
            DashboardSlot(DashboardCardTags.FLIGHT_PARAMETERS) { FlightParametersCardContainer(units, cardModifier) }
            DashboardSlot(DashboardCardTags.NEARBY_CITY) { NearbyCityCardContainer(units.distance, cardModifier) }
            DashboardSlot(DashboardCardTags.ROUTE) { RouteCardContainer(units.distance, routePickerViewModel::open, cardModifier) }
            DashboardSlot(DashboardCardTags.MAP) {
                MapCardContainer(cardModifier, maxMapHeight = viewportHeight, viewModel = mapViewModel)
            }
        }
        MapZoomTipHost(
            requested = zoomTip,
            onShown = mapViewModel::onZoomTipShown,
            onOpenSettings = onOpenLargerMapZoomSetting,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        RoutePickerOverlay(viewModel = routePickerViewModel, mapViewModel = mapViewModel)
    }
}

/** Test tags of the dashboard's card slots, in the card order of [LocationDashboardCards]. */
internal object DashboardCardTags {
    const val PERMISSION = "dashboard_card_permission"
    const val SATELLITES = "dashboard_card_satellites"
    const val COURSE = "dashboard_card_course"
    const val HORIZON = "dashboard_card_horizon"
    const val FLIGHT_PARAMETERS = "dashboard_card_flight_parameters"
    const val NEARBY_CITY = "dashboard_card_nearby_city"
    const val ROUTE = "dashboard_card_route"
    const val MAP = "dashboard_card_map"
}

/** A full-width row of the card list, tagged [tag], with its card centered at the top. */
@Composable
private fun DashboardSlot(
    tag: String,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxWidth().testTag(tag), contentAlignment = Alignment.TopCenter) { content() }
}

/**
 * The top app bar as in the original app (`activity_main.xml`, `menu/app_menu.xml`): the centered
 * title "Smart Flight" and a "⋮" overflow button whose menu holds Settings and About, on every screen
 * width. Its container ([SmartFlightColors.topBar][kniezrec.com.flightinfo.ui.theme.SmartFlightColors.topBar])
 * changes to the scrolled tone while the card list scrolls under it ([scrollBehavior]); the status
 * bar above it is painted in the same color.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardHeader(
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val containerColor = rememberTopBarContainerColor(scrollBehavior)
    CenterAlignedTopAppBar(
        title = {
            Text(
                stringResource(R.string.app_name),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = modifier.fillMaxWidth().topBarBackground(containerColor),
        actions = { DashboardOverflowMenu(onOpenSettings, onOpenAbout) },
        // The dashboard is already inside the safe drawing area; topBarBackground paints the status bar.
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = smartFlightTopAppBarColors(),
        scrollBehavior = scrollBehavior,
    )
}

/** The "⋮" button and its menu (Settings, About) in the raised (menu) tone with value text. */
@Composable
private fun DashboardOverflowMenu(
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = SmartFlightTheme.colors
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.dashboard_more_options))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = colors.raised,
        ) {
            val itemColors = MenuDefaults.itemColors(textColor = colors.valueText)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_title)) },
                onClick = {
                    expanded = false
                    onOpenSettings()
                },
                colors = itemColors,
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.about_title)) },
                onClick = {
                    expanded = false
                    onOpenAbout()
                },
                colors = itemColors,
            )
        }
    }
}
