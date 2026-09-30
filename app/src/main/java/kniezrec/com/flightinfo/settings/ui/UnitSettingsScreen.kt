package kniezrec.com.flightinfo.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.unit.dp
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.dashboard.HideableCard
import kniezrec.com.flightinfo.display.DisplayPreferences
import kniezrec.com.flightinfo.displayunits.AltitudeUnit
import kniezrec.com.flightinfo.displayunits.DistanceUnit
import kniezrec.com.flightinfo.displayunits.PressureUnit
import kniezrec.com.flightinfo.displayunits.SpeedUnit
import kniezrec.com.flightinfo.displayunits.UnitKey
import kniezrec.com.flightinfo.displayunits.UnitPreferences
import kniezrec.com.flightinfo.displayunits.VerticalSpeedUnit
import kniezrec.com.flightinfo.displayunits.ui.labels
import kniezrec.com.flightinfo.ui.theme.LabelText
import kniezrec.com.flightinfo.ui.theme.SmartFlightAlertDialog
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme
import kniezrec.com.flightinfo.ui.theme.ValueText
import kniezrec.com.flightinfo.ui.theme.smartFlightRadioButtonColors
import kniezrec.com.flightinfo.ui.theme.smartFlightSwitchColors

private sealed class Selector<T : UnitKey>(
    val title: Int,
    val options: List<T>,
) {
    class Speed : Selector<SpeedUnit>(R.string.unit_speed, SpeedUnit.entries)

    class Altitude : Selector<AltitudeUnit>(R.string.unit_altitude, AltitudeUnit.entries)

    class Distance : Selector<DistanceUnit>(R.string.unit_distance, DistanceUnit.entries)

    class VerticalSpeed : Selector<VerticalSpeedUnit>(R.string.unit_vertical_speed, VerticalSpeedUnit.entries)

    class Pressure : Selector<PressureUnit>(R.string.unit_pressure, PressureUnit.entries)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun UnitSettingsScreen(
    preferences: UnitPreferences,
    onPreferenceChange: (UnitPreferences) -> Unit,
    onBack: () -> Unit,
    displayPreferences: DisplayPreferences = DisplayPreferences(),
    onDisplayPreferenceChange: (DisplayPreferences) -> Unit = {},
    showBackgroundNotification: Boolean = true,
    onBackgroundNotificationChange: (Boolean) -> Unit = {},
    notificationsBlocked: Boolean = false,
    onAllowNotifications: () -> Unit = {},
    hiddenCards: Set<HideableCard> = emptySet(),
    onShowHiddenCards: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selector by remember { mutableStateOf<Selector<*>?>(null) }
    val colors = SmartFlightTheme.colors
    Scaffold(
        modifier = modifier,
        containerColor = colors.page,
        contentColor = colors.valueText,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = colors.card,
                        scrolledContainerColor = colors.card,
                        navigationIconContentColor = colors.toolbarTitle,
                        titleContentColor = colors.toolbarTitle,
                        actionIconContentColor = colors.toolbarTitle,
                    ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                    ) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_up)) }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 24.dp),
            contentAlignment = androidx.compose.ui.Alignment.TopCenter,
        ) {
            Column(
                Modifier.fillMaxWidth().widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                ValueText(stringResource(R.string.display_section), style = MaterialTheme.typography.titleLarge)
                Column(Modifier.padding(top = 8.dp)) {
                    displaysettingRow(
                        R.string.keep_screen_always_on,
                        if (displayPreferences.keepScreenAlwaysOn) R.string.settings_on else R.string.settings_off,
                        displayPreferences.keepScreenAlwaysOn,
                    ) {
                        onDisplayPreferenceChange(displayPreferences.copy(keepScreenAlwaysOn = !displayPreferences.keepScreenAlwaysOn))
                    }
                    displaysettingRow(
                        R.string.portrait_orientation,
                        if (displayPreferences.portraitOrientation) R.string.orientation_portrait else R.string.orientation_sensor,
                        displayPreferences.portraitOrientation,
                    ) {
                        onDisplayPreferenceChange(displayPreferences.copy(portraitOrientation = !displayPreferences.portraitOrientation))
                    }
                    displaysettingRow(
                        R.string.larger_map_zoom,
                        if (displayPreferences.largerMapZoom) R.string.settings_on else R.string.settings_off,
                        displayPreferences.largerMapZoom,
                        if (displayPreferences.largerMapZoom) R.string.larger_map_zoom_warning else null,
                    ) {
                        onDisplayPreferenceChange(displayPreferences.copy(largerMapZoom = !displayPreferences.largerMapZoom))
                    }
                    ShowHiddenCardsRow(hiddenCards, onShowHiddenCards)
                }
                ValueText(
                    stringResource(R.string.monitoring_section),
                    Modifier.padding(top = 24.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
                Column(Modifier.padding(top = 8.dp)) {
                    // The effective state: switched on but not shown is "Notifications are blocked".
                    val notificationBlocked = showBackgroundNotification && notificationsBlocked
                    displaysettingRow(
                        R.string.show_background_notification,
                        when {
                            notificationBlocked -> R.string.notifications_blocked
                            showBackgroundNotification -> R.string.settings_on
                            else -> R.string.settings_off
                        },
                        showBackgroundNotification,
                        description = R.string.background_notification_settings_description,
                    ) { onBackgroundNotificationChange(!showBackgroundNotification) }
                    if (notificationBlocked) {
                        val hint = stringResource(R.string.allow_notifications_hint)
                        TextButton(
                            onClick = onAllowNotifications,
                            modifier =
                                Modifier.padding(top = 4.dp).heightIn(min = 48.dp).semantics {
                                    stateDescription = hint
                                },
                        ) { Text(stringResource(R.string.allow_notifications), color = colors.accent) }
                    }
                }
                ValueText(
                    stringResource(R.string.units_section),
                    Modifier.padding(top = 24.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
                Column(Modifier.padding(top = 8.dp)) {
                    settingRow(stringResource(R.string.unit_speed), unitText(preferences.speed)) { selector = Selector.Speed() }
                    settingRow(stringResource(R.string.unit_altitude), unitText(preferences.altitude)) { selector = Selector.Altitude() }
                    settingRow(stringResource(R.string.unit_distance), unitText(preferences.distance)) { selector = Selector.Distance() }
                    settingRow(stringResource(R.string.unit_vertical_speed), unitText(preferences.verticalSpeed)) {
                        selector =
                            Selector.VerticalSpeed()
                    }
                    settingRow(stringResource(R.string.unit_pressure), unitText(preferences.pressure)) { selector = Selector.Pressure() }
                }
            }
        }
    }
    selector?.let { current -> UnitChoiceDialog(current, preferences, onPreferenceChange) { selector = null } }
}

@Composable
private fun displaysettingRow(
    label: Int,
    summary: Int,
    checked: Boolean,
    warning: Int? = null,
    description: Int? = null,
    onClick: () -> Unit,
) {
    val labelText = stringResource(label)
    val summaryText = stringResource(summary)
    val warningText = warning?.let { stringResource(it) }
    val explanationText = description?.let { stringResource(it) }
    val descriptionText =
        if (explanationText != null) {
            stringResource(R.string.display_setting_warning_description, labelText, summaryText, explanationText)
        } else if (warningText == null) {
            stringResource(R.string.display_setting_description, labelText, summaryText)
        } else {
            stringResource(R.string.display_setting_warning_description, labelText, summaryText, warningText)
        }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = descriptionText
                    role = Role.Switch
                    stateDescription = summaryText
                    toggleableState =
                        androidx.compose.ui.state
                            .ToggleableState(checked)
                }.padding(vertical = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                ValueText(labelText, style = MaterialTheme.typography.bodyLarge)
                LabelText(summaryText, style = MaterialTheme.typography.bodyMedium)
            }
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.padding(start = 12.dp),
                colors = smartFlightSwitchColors(),
            )
        }
        warning?.let {
            ValueText(
                stringResource(it),
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        HorizontalDivider()
    }
}

/**
 * "Show hidden cards": lists the hidden cards, disabled while nothing is hidden. The confirmation
 * is the subtitle itself: it turns to "No hidden cards" at once (announced as a polite live
 * region) and the row disables, so no separate snackbar is needed.
 */
@Composable
private fun ShowHiddenCardsRow(
    hiddenCards: Set<HideableCard>,
    onClick: () -> Unit,
) {
    val enabled = hiddenCards.isNotEmpty()
    val summary =
        if (enabled) {
            HideableCard.entries
                .filter { it in hiddenCards }
                .map { stringResource(it.nameResource()) }
                .joinToString(", ")
        } else {
            stringResource(R.string.no_hidden_cards)
        }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(vertical = 12.dp)
                .alpha(if (enabled) 1f else DISABLED_ROW_ALPHA),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                ValueText(stringResource(R.string.show_hidden_cards), style = MaterialTheme.typography.bodyLarge)
                LabelText(
                    summary,
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        HorizontalDivider()
    }
}

private fun HideableCard.nameResource(): Int =
    when (this) {
        HideableCard.Course -> R.string.hidden_card_course
        HideableCard.Horizon -> R.string.hidden_card_horizon
    }

private const val DISABLED_ROW_ALPHA = 0.5f

@Composable
private fun settingRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    val rowDescription = stringResource(R.string.settings_row_description, label, value)
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = rowDescription
                    role = Role.Button
                }.padding(vertical = 12.dp),
        ) {
            ValueText(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            LabelText(value, style = MaterialTheme.typography.bodyLarge)
        }
        HorizontalDivider()
    }
}

@Composable
private fun UnitChoiceDialog(
    selector: Selector<*>,
    preferences: UnitPreferences,
    onPreferenceChange: (UnitPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected: UnitKey =
        when (selector) {
            is Selector.Speed -> preferences.speed
            is Selector.Altitude -> preferences.altitude
            is Selector.Distance -> preferences.distance
            is Selector.VerticalSpeed -> preferences.verticalSpeed
            is Selector.Pressure -> preferences.pressure
        }
    SmartFlightAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(selector.title)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                selector.options.forEach { option ->
                    val isSelected = option == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                onPreferenceChange(preferences.with(selector, option))
                                onDismiss()
                            }.semantics {
                                role = Role.RadioButton
                                this.selected = isSelected
                            },
                    ) {
                        RadioButton(selected = isSelected, onClick = null, colors = smartFlightRadioButtonColors())
                        Text(optionText(option), Modifier.weight(1f).padding(start = 12.dp).padding(vertical = 14.dp))
                    }
                }
            }
        },
        confirmButton = {},
    )
}

private fun UnitPreferences.with(
    selector: Selector<*>,
    value: Any,
): UnitPreferences =
    when (selector) {
        is Selector.Speed -> copy(speed = value as SpeedUnit)
        is Selector.Altitude -> copy(altitude = value as AltitudeUnit)
        is Selector.Distance -> copy(distance = value as DistanceUnit)
        is Selector.VerticalSpeed -> copy(verticalSpeed = value as VerticalSpeedUnit)
        is Selector.Pressure -> copy(pressure = value as PressureUnit)
    }

@Composable private fun unitText(value: UnitKey) = stringResource(value.labels.symbol)

@Composable private fun optionText(value: UnitKey) = stringResource(value.labels.longName)
