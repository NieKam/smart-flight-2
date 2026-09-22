package kniezrec.com.flightinfo.ui.gnss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kniezrec.com.flightinfo.flight.FlightParametersState
import kniezrec.com.flightinfo.ui.permission.cardPurple

private val textColor = Color(0xFFD9D9ED)

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

    Card(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp),
        shape =
            androidx.compose.foundation.shape
                .RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cardPurple),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
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
        Modifier
            .fillMaxWidth()
            .heightIn(min = 160.dp)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FlightParametersTitle(textAlign = TextAlign.Center)

        Text(
            text =
                androidx.compose.ui.res
                    .stringResource(R.string.flight_parameters_waiting),
            modifier = Modifier.padding(top = 12.dp),
            color = textColor,
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
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        FlightParametersTitle()

        Spacer(modifier = Modifier.height(16.dp))

        ParameterRow(
            R.string.flight_speed,
            state.speedKilometresPerHour?.let {
                format(
                    convertSpeed(it, preferences.speed),
                    R.string.flight_speed_value,
                    false,
                    stringResource(
                        when (preferences.speed) {
                            kniezrec.com.flightinfo.displayunits.SpeedUnit.KILOMETRES_PER_HOUR -> R.string.unit_kmh
                            kniezrec.com.flightinfo.displayunits.SpeedUnit.MILES_PER_HOUR -> R.string.unit_mph
                            kniezrec.com.flightinfo.displayunits.SpeedUnit.KNOTS -> R.string.unit_kt
                        },
                    ),
                )
            },
            state.speedKilometresPerHour?.let {
                format(
                    convertSpeed(it, preferences.speed),
                    R.string.flight_value_accessibility,
                    false,
                    stringResource(speedAccessibilityUnit(preferences.speed)),
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
                    stringResource(
                        when (preferences.verticalSpeed) {
                            kniezrec.com.flightinfo.displayunits.VerticalSpeedUnit.METRES_PER_SECOND -> R.string.unit_ms
                            kniezrec.com.flightinfo.displayunits.VerticalSpeedUnit.METRES_PER_MINUTE -> R.string.unit_mmin
                            kniezrec.com.flightinfo.displayunits.VerticalSpeedUnit.FEET_PER_MINUTE -> R.string.unit_ftmin
                        },
                    ),
                )
            },
            state.verticalSpeedMetresPerSecond?.let {
                format(
                    convertVerticalSpeed(it, preferences.verticalSpeed),
                    R.string.flight_value_accessibility,
                    true,
                    stringResource(verticalSpeedAccessibilityUnit(preferences.verticalSpeed)),
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
                    stringResource(
                        if (preferences.altitude ==
                            kniezrec.com.flightinfo.displayunits.AltitudeUnit.FEET
                        ) {
                            R.string.unit_ft
                        } else {
                            R.string.unit_m
                        },
                    ),
                )
            },
            state.altitudeMetres?.let {
                format(
                    convertAltitude(it, preferences.altitude),
                    R.string.flight_value_accessibility,
                    false,
                    stringResource(
                        if (preferences.altitude ==
                            kniezrec.com.flightinfo.displayunits.AltitudeUnit.FEET
                        ) {
                            R.string.unit_ft_accessibility
                        } else {
                            R.string.unit_m_accessibility
                        },
                    ),
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
                    stringResource(
                        if (preferences.pressure ==
                            kniezrec.com.flightinfo.displayunits.PressureUnit.INCHES_OF_MERCURY
                        ) {
                            R.string.unit_inhg
                        } else {
                            R.string.unit_mbar
                        },
                    ),
                )
            },
            accessibilityValue =
                state.pressureMillibars?.let {
                    format(
                        convertPressure(it, preferences.pressure),
                        R.string.flight_value_accessibility,
                        false,
                        stringResource(
                            if (preferences.pressure ==
                                kniezrec.com.flightinfo.displayunits.PressureUnit.INCHES_OF_MERCURY
                            ) {
                                R.string.unit_inhg_accessibility
                            } else {
                                R.string.unit_mbar_accessibility
                            },
                        ),
                    )
                },
        )
    }
}

@Composable
private fun speedAccessibilityUnit(unit: kniezrec.com.flightinfo.displayunits.SpeedUnit): Int =
    when (unit) {
        kniezrec.com.flightinfo.displayunits.SpeedUnit.KILOMETRES_PER_HOUR -> R.string.unit_kmh_accessibility
        kniezrec.com.flightinfo.displayunits.SpeedUnit.MILES_PER_HOUR -> R.string.unit_mph_accessibility
        kniezrec.com.flightinfo.displayunits.SpeedUnit.KNOTS -> R.string.unit_kt_accessibility
    }

@Composable
private fun verticalSpeedAccessibilityUnit(unit: kniezrec.com.flightinfo.displayunits.VerticalSpeedUnit): Int =
    when (unit) {
        kniezrec.com.flightinfo.displayunits.VerticalSpeedUnit.METRES_PER_SECOND -> R.string.unit_ms_accessibility
        kniezrec.com.flightinfo.displayunits.VerticalSpeedUnit.METRES_PER_MINUTE -> R.string.unit_mmin_accessibility
        kniezrec.com.flightinfo.displayunits.VerticalSpeedUnit.FEET_PER_MINUTE -> R.string.unit_ftmin_accessibility
    }

@Composable
private fun FlightParametersTitle(textAlign: TextAlign = TextAlign.Start) {
    Text(
        text =
            androidx.compose.ui.res
                .stringResource(R.string.flight_parameters_title),
        color = textColor,
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
            .stringResource(R.string.flight_value_accessibility, labelText, spokenValue)

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            labelText,
            Modifier.weight(1f),
            color = textColor,
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp,
                    lineHeight = 25.sp,
                ),
        )

        Text(
            displayedValue,
            color = textColor,
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
