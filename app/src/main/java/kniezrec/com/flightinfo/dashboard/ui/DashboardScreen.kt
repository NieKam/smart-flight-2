package kniezrec.com.flightinfo.dashboard.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.about.AppVersion
import kniezrec.com.flightinfo.about.ui.AboutDialog
import kniezrec.com.flightinfo.course.ui.CourseCardContainer
import kniezrec.com.flightinfo.flight.ui.FlightParametersCardContainer
import kniezrec.com.flightinfo.gnss.ui.GnssStatusCardContainer
import kniezrec.com.flightinfo.horizon.ui.HorizonCardContainer
import kniezrec.com.flightinfo.map.ui.MapCardContainer
import kniezrec.com.flightinfo.map.ui.MapViewModel
import kniezrec.com.flightinfo.nearby.ui.NearbyCityCardContainer
import kniezrec.com.flightinfo.route.ui.RouteCardContainer
import kniezrec.com.flightinfo.route.ui.RoutePickerOverlay
import kniezrec.com.flightinfo.route.ui.RoutePickerViewModel
import kniezrec.com.flightinfo.settings.ui.SettingsOverlay
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme

/**
 * The dashboard: header and the scrolling list of cards, with the city picker, Settings and About
 * as overlays. Every card container obtains its own (Activity-scoped) ViewModel; the open state of
 * Settings and About is saved, so it survives a configuration change (the picker keeps its own in
 * [RoutePickerViewModel]).
 *
 * @param onOpenLocationSettings action of the GNSS card when location is switched off.
 * @param modifier insets of the dashboard and the Settings overlay (not of the About dialog).
 */
@Composable
fun DashboardScreen(
    onOpenLocationSettings: () -> Unit,
    aboutVersion: AppVersion,
    onSendFeedback: () -> Boolean,
    onRate: () -> Boolean,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    // Registered before the picker's own back handler, which therefore wins while it is open.
    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = showAbout) { showAbout = false }
    val units by viewModel.units.collectAsStateWithLifecycle()
    val routePickerViewModel: RoutePickerViewModel = hiltViewModel()
    val mapViewModel: MapViewModel = hiltViewModel()
    // Settings is an overlay so the dashboard's AndroidView-backed map remains composed. This
    // preserves its viewport, overlays, and in-place zoom policy.
    Box(Modifier.fillMaxSize()) {
        if (showSettings) {
            SettingsOverlay(onBack = { showSettings = false }, modifier = modifier.zIndex(1f))
        }
        Box(Modifier.fillMaxSize().then(modifier)) {
            Column(Modifier.fillMaxSize()) {
                DashboardHeader(onOpenSettings = { showSettings = true }, onOpenAbout = { showAbout = true })
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                ) {
                    val cardModifier = Modifier.padding(bottom = 12.dp).widthIn(max = 600.dp)
                    DashboardSlot {
                        // As before: the GNSS card has no retry of its own; its retry restarts the map observation.
                        GnssStatusCardContainer(onOpenLocationSettings = onOpenLocationSettings, onRetry = mapViewModel::retry)
                    }
                    DashboardSlot { FlightParametersCardContainer(units, cardModifier) }
                    DashboardSlot { CourseCardContainer(cardModifier) }
                    DashboardSlot { HorizonCardContainer(cardModifier) }
                    DashboardSlot { NearbyCityCardContainer(units.distance, cardModifier) }
                    DashboardSlot { RouteCardContainer(units.distance, routePickerViewModel::open, cardModifier) }
                    DashboardSlot { MapCardContainer(cardModifier, viewModel = mapViewModel) }
                }
            }
            RoutePickerOverlay(viewModel = routePickerViewModel, mapViewModel = mapViewModel)
        }
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

/** A full-width row of the card list with its card centered at the top. */
@Composable
private fun DashboardSlot(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { content() }
}

@Composable
fun DashboardHeader(
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        val compact = maxWidth < 360.dp
        if (compact) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.app_name),
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                    color = SmartFlightTheme.colors.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
                CompactDashboardActions(onOpenSettings, onOpenAbout)
            }
        } else {
            Text(
                stringResource(R.string.app_name),
                modifier = Modifier.align(Alignment.Center),
                color = SmartFlightTheme.colors.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
            Row(Modifier.align(Alignment.CenterEnd)) {
                DashboardAction(stringResource(R.string.settings_title), onOpenSettings)
                DashboardAction(stringResource(R.string.about_title), onOpenAbout)
            }
        }
    }
}

@Composable
private fun DashboardAction(
    label: String,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(label, color = SmartFlightTheme.colors.accent)
    }
}

@Composable
private fun CompactDashboardActions(
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.dashboard_more_options), color = SmartFlightTheme.colors.accent)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_title)) },
                onClick = {
                    expanded = false
                    onOpenSettings()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.about_title)) },
                onClick = {
                    expanded = false
                    onOpenAbout()
                },
            )
        }
    }
}
