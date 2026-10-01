package kniezrec.com.flightinfo.flight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.displayunits.UnitPreferences
import kniezrec.com.flightinfo.displayunits.convertAltitude
import kniezrec.com.flightinfo.displayunits.convertPressure
import kniezrec.com.flightinfo.displayunits.convertSpeed
import kniezrec.com.flightinfo.displayunits.convertVerticalSpeed
import kniezrec.com.flightinfo.displayunits.formatUnitNumber
import kniezrec.com.flightinfo.displayunits.ui.labels
import kniezrec.com.flightinfo.flight.FlightParametersState
import kniezrec.com.flightinfo.ui.theme.LabelText
import kniezrec.com.flightinfo.ui.theme.SmartFlightCard
import kniezrec.com.flightinfo.ui.theme.SmartFlightCardDefaults
import kniezrec.com.flightinfo.ui.theme.ValueText

@Composable
internal fun FlightParametersCard(
    state: FlightParametersState,
    preferences: UnitPreferences = UnitPreferences(),
    modifier: Modifier = Modifier,
) {
    var wasWaiting by remember { mutableStateOf(state is FlightParametersState.Waiting) }
    var announceAvailability by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        when (state) {
            FlightParametersState.Waiting -> {
                wasWaiting = true
                announceAvailability = false
            }

            is FlightParametersState.Readings -> {
                if (wasWaiting) announceAvailability = true
                wasWaiting = false
            }
        }
    }

    SmartFlightCard(modifier, minHeight = SmartFlightCardDefaults.MinHeight) {
        Box {
            when (state) {
                FlightParametersState.Waiting -> FlightParametersWaiting()
                is FlightParametersState.Readings -> FlightParametersReadings(state, preferences)
            }

            if (announceAvailability) {
                FlightParametersAvailabilityAnnouncement()
            }
        }
    }
}

@Composable
private fun FlightParametersAvailabilityAnnouncement() {
    val availabilityText =
        androidx.compose.ui.res
            .stringResource(R.string.flight_parameters_available)

    Box(
        Modifier.semantics {
            contentDescription = availabilityText
            liveRegion = LiveRegionMode.Polite
        },
    )
}

@Composable
private fun FlightParametersWaiting() {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FlightParametersTitle(textAlign = TextAlign.Center)

        ValueText(
            text =
                androidx.compose.ui.res
                    .stringResource(R.string.flight_parameters_waiting),
            modifier = Modifier.padding(top = 12.dp),
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp,
                    lineHeight = 25.sp,
                    textAlign = TextAlign.Center,
                ),
        )
    }
}

@Composable
private fun FlightParametersReadings(
    state: FlightParametersState.Readings,
    preferences: UnitPreferences,
) {
    Column(Modifier.fillMaxWidth()) {
        FlightParametersTitle()

        Spacer(modifier = Modifier.height(16.dp))

        ParameterRow(
            R.string.flight_speed,
            state.speedKilometresPerHour?.let {
                format(
                    convertSpeed(it, preferences.speed),
                    R.string.flight_speed_value,
                    false,
                    stringResource(preferences.speed.labels.symbol),
                )
            },
            state.speedKilometresPerHour?.let {
                format(
                    convertSpeed(it, preferences.speed),
                    R.string.flight_value_accessibility,
                    false,
                    stringResource(preferences.speed.labels.accessibility),
                )
            },
        )

        Spacer(modifier = Modifier.height(4.dp))

        ParameterRow(
            R.string.flight_vertical_speed,
            state.verticalSpeedMetresPerSecond?.let {
                format(
                    convertVerticalSpeed(it, preferences.verticalSpeed),
                    R.string.flight_vertical_speed_value,
                    true,
                    stringResource(preferences.verticalSpeed.labels.symbol),
                )
            },
            state.verticalSpeedMetresPerSecond?.let {
                format(
                    convertVerticalSpeed(it, preferences.verticalSpeed),
                    R.string.flight_value_accessibility,
                    true,
                    stringResource(preferences.verticalSpeed.labels.accessibility),
                )
            },
        )

        Spacer(modifier = Modifier.height(4.dp))

        ParameterRow(
            R.string.flight_altitude,
            state.altitudeMetres?.let {
                format(
                    convertAltitude(it, preferences.altitude),
                    R.string.flight_altitude_value,
                    false,
                    stringResource(preferences.altitude.labels.symbol),
                )
            },
            state.altitudeMetres?.let {
                format(
                    convertAltitude(it, preferences.altitude),
                    R.string.flight_value_accessibility,
                    false,
                    stringResource(preferences.altitude.labels.accessibility),
                )
            },
        )

        Spacer(modifier = Modifier.height(4.dp))

        ParameterRow(
            R.string.flight_pressure,
            state.pressureMillibars?.let {
                format(
                    convertPressure(it, preferences.pressure),
                    R.string.flight_pressure_value,
                    false,
                    stringResource(preferences.pressure.labels.symbol),
                )
            },
            accessibilityValue =
                state.pressureMillibars?.let {
                    format(
                        convertPressure(it, preferences.pressure),
                        R.string.flight_value_accessibility,
                        false,
                        stringResource(preferences.pressure.labels.accessibility),
                    )
                },
        )
    }
}

@Composable
private fun FlightParametersTitle(textAlign: TextAlign = TextAlign.Start) {
    LabelText(
        text =
            androidx.compose.ui.res
                .stringResource(R.string.flight_parameters_title),
        style =
            MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                textAlign = textAlign,
            ),
    )
}

@Composable
private fun ParameterRow(
    label: Int,
    value: String?,
    accessibilityValue: String? = null,
) {
    val labelText =
        androidx.compose.ui.res
            .stringResource(label)
    val displayedValue =
        value ?: androidx.compose.ui.res
            .stringResource(R.string.flight_unavailable)
    val spokenValue =
        (accessibilityValue ?: value) ?: androidx.compose.ui.res
            .stringResource(R.string.flight_unavailable_accessibility)
    val rowDescription =
        androidx.compose.ui.res
            .stringResource(R.string.flight_row_accessibility, labelText, spokenValue)

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelText(
            labelText,
            Modifier.weight(1f),
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp,
                    lineHeight = 25.sp,
                ),
        )

        ValueText(
            displayedValue,
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp,
                    lineHeight = 25.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                ),
        )
    }
}

@Composable
private fun format(
    value: Double?,
    template: Int,
    signed: Boolean,
    unit: String,
): String {
    val number =
        formatUnitNumber(value, signed) ?: return androidx.compose.ui.res
            .stringResource(R.string.flight_unavailable)
    return androidx.compose.ui.res
        .stringResource(template, number, unit)
}
