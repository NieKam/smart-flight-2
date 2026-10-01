package kniezrec.com.flightinfo.flight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import kniezrec.com.flightinfo.ui.theme.LabelValueRow
import kniezrec.com.flightinfo.ui.theme.SmartFlightCard
import kniezrec.com.flightinfo.ui.theme.SmartFlightCardDefaults
import kniezrec.com.flightinfo.ui.theme.ValueText
import kniezrec.com.flightinfo.ui.theme.withSmallerUnit

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

        Spacer(modifier = Modifier.height(12.dp))

        val speedUnit = stringResource(preferences.speed.labels.symbol)
        ParameterRow(
            R.string.flight_speed,
            speedUnit,
            state.speedKilometresPerHour?.let {
                format(convertSpeed(it, preferences.speed), R.string.flight_speed_value, false, speedUnit)
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

        val verticalSpeedUnit = stringResource(preferences.verticalSpeed.labels.symbol)
        ParameterRow(
            R.string.flight_vertical_speed,
            verticalSpeedUnit,
            state.verticalSpeedMetresPerSecond?.let {
                format(
                    convertVerticalSpeed(it, preferences.verticalSpeed),
                    R.string.flight_vertical_speed_value,
                    true,
                    verticalSpeedUnit,
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

        val altitudeUnit = stringResource(preferences.altitude.labels.symbol)
        ParameterRow(
            R.string.flight_altitude,
            altitudeUnit,
            state.altitudeMetres?.let {
                format(convertAltitude(it, preferences.altitude), R.string.flight_altitude_value, false, altitudeUnit)
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

        val pressureUnit = stringResource(preferences.pressure.labels.symbol)
        ParameterRow(
            R.string.flight_pressure,
            pressureUnit,
            state.pressureMillibars?.let {
                format(convertPressure(it, preferences.pressure), R.string.flight_pressure_value, false, pressureUnit)
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
        text = stringResource(R.string.flight_parameters_title),
        style = MaterialTheme.typography.titleLarge.copy(textAlign = textAlign),
    )
}

/**
 * A label and its key value (Material `headlineMedium`, tabular figures) with the [unit] drawn
 * smaller; read as one "label, value" description.
 */
@Composable
private fun ParameterRow(
    label: Int,
    unit: String,
    value: String?,
    accessibilityValue: String? = null,
) {
    val labelText = stringResource(label)
    val displayedValue = value ?: stringResource(R.string.flight_unavailable)
    val spokenValue =
        (accessibilityValue ?: value) ?: stringResource(R.string.flight_unavailable_accessibility)
    val rowDescription = stringResource(R.string.flight_row_accessibility, labelText, spokenValue)

    LabelValueRow(
        label = labelText,
        value = withSmallerUnit(displayedValue, unit),
        valueStyle = MaterialTheme.typography.headlineMedium,
        modifier =
            Modifier
                .heightIn(min = 48.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = rowDescription
                },
    )
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
