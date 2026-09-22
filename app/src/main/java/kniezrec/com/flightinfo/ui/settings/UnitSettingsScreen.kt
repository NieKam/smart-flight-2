package kniezrec.com.flightinfo.ui.settings

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.unit.dp
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.display.DisplayPreferences
import kniezrec.com.flightinfo.displayunits.AltitudeUnit
import kniezrec.com.flightinfo.displayunits.DistanceUnit
import kniezrec.com.flightinfo.displayunits.PressureUnit
import kniezrec.com.flightinfo.displayunits.SpeedUnit
import kniezrec.com.flightinfo.displayunits.UnitPreferences
import kniezrec.com.flightinfo.displayunits.VerticalSpeedUnit

private sealed class Selector<T>(
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
    modifier: Modifier = Modifier,
) {
    var selector by remember { mutableStateOf<Selector<*>?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                Text(stringResource(R.string.display_section), style = MaterialTheme.typography.titleLarge)
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
                }
                Text(
                    stringResource(R.string.monitoring_section),
                    Modifier.padding(top = 24.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
                Column(Modifier.padding(top = 8.dp)) {
                    displaysettingRow(
                        R.string.show_background_notification,
                        if (showBackgroundNotification) R.string.settings_on else R.string.settings_off,
                        showBackgroundNotification,
                        description = R.string.background_notification_settings_description,
                    ) { onBackgroundNotificationChange(!showBackgroundNotification) }
                }
                Text(stringResource(R.string.units_section), Modifier.padding(top = 24.dp), style = MaterialTheme.typography.titleLarge)
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
                Text(labelText, style = MaterialTheme.typography.bodyLarge)
                Text(summaryText, style = MaterialTheme.typography.bodyMedium)
            }
            androidx.compose.material3.Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 12.dp))
        }
        warning?.let {
            Text(
                stringResource(it),
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        HorizontalDivider()
    }
}

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
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        HorizontalDivider()
    }
}

@Suppress("UNCHECKED_CAST")
@Composable
private fun <T> UnitChoiceDialog(
    selector: Selector<T>,
    preferences: UnitPreferences,
    onPreferenceChange: (UnitPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    val selected: T =
        when (selector) {
            is Selector.Speed -> preferences.speed as T
            is Selector.Altitude -> preferences.altitude as T
            is Selector.Distance -> preferences.distance as T
            is Selector.VerticalSpeed -> preferences.verticalSpeed as T
            is Selector.Pressure -> preferences.pressure as T
        }
    AlertDialog(
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
                        RadioButton(selected = isSelected, onClick = null)
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

@Composable private fun unitText(value: SpeedUnit) =
    stringResource(
        when (value) {
            SpeedUnit.KILOMETRES_PER_HOUR -> R.string.unit_kmh
            SpeedUnit.MILES_PER_HOUR -> R.string.unit_mph
            SpeedUnit.KNOTS -> R.string.unit_kt
        },
    )

@Composable private fun unitText(value: AltitudeUnit) =
    stringResource(
        if (value ==
            AltitudeUnit.FEET
        ) {
            R.string.unit_ft
        } else {
            R.string.unit_m
        },
    )

@Composable private fun unitText(value: DistanceUnit) =
    stringResource(
        if (value ==
            DistanceUnit.MILES
        ) {
            R.string.unit_mi
        } else {
            R.string.unit_km
        },
    )

@Composable private fun unitText(value: VerticalSpeedUnit) =
    stringResource(
        when (value) {
            VerticalSpeedUnit.METRES_PER_SECOND -> R.string.unit_ms
            VerticalSpeedUnit.METRES_PER_MINUTE -> R.string.unit_mmin
            VerticalSpeedUnit.FEET_PER_MINUTE -> R.string.unit_ftmin
        },
    )

@Composable private fun unitText(value: PressureUnit) =
    stringResource(
        if (value ==
            PressureUnit.INCHES_OF_MERCURY
        ) {
            R.string.unit_inhg
        } else {
            R.string.unit_mbar
        },
    )

@Composable private fun optionText(value: Any) =
    stringResource(
        when (value) {
            SpeedUnit.KILOMETRES_PER_HOUR -> R.string.option_kmh
            SpeedUnit.MILES_PER_HOUR -> R.string.option_mph
            SpeedUnit.KNOTS -> R.string.option_kt
            AltitudeUnit.METRES -> R.string.option_m
            AltitudeUnit.FEET -> R.string.option_ft
            DistanceUnit.KILOMETRES -> R.string.option_km
            DistanceUnit.MILES -> R.string.option_mi
            VerticalSpeedUnit.METRES_PER_SECOND -> R.string.option_ms
            VerticalSpeedUnit.METRES_PER_MINUTE -> R.string.option_mmin
            VerticalSpeedUnit.FEET_PER_MINUTE -> R.string.option_ftmin
            PressureUnit.MILLIBAR -> R.string.option_mbar
            PressureUnit.INCHES_OF_MERCURY -> R.string.option_inhg
            else -> error("Unknown unit")
        },
    )
