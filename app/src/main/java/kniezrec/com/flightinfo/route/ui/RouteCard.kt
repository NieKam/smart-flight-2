package kniezrec.com.flightinfo.route.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.displayunits.DistanceUnit
import kniezrec.com.flightinfo.displayunits.convertDistance
import kniezrec.com.flightinfo.displayunits.formatUnitNumber
import kniezrec.com.flightinfo.displayunits.ui.labels
import kniezrec.com.flightinfo.route.RouteDetails
import kniezrec.com.flightinfo.route.RouteEndpoint
import kniezrec.com.flightinfo.route.RouteState
import kniezrec.com.flightinfo.ui.theme.LabelText
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme
import kniezrec.com.flightinfo.ui.theme.ValueText
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun RouteCard(
    state: RouteState,
    onChoose: (RouteEndpoint) -> Unit,
    onClear: (RouteEndpoint) -> Unit,
    onClearAll: () -> Unit,
    onRestoreRetry: () -> Unit = {},
    distanceUnit: DistanceUnit = DistanceUnit.KILOMETRES,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth().heightIn(min = 120.dp), colors = CardDefaults.cardColors(containerColor = SmartFlightTheme.colors.card)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LabelText(stringResource(R.string.route_title))
            EndpointRow(RouteEndpoint.DEPARTURE, state.departure?.name, onChoose, onClear)
            EndpointRow(RouteEndpoint.DESTINATION, state.destination?.name, onChoose, onClear)
            state.details?.let { details ->
                Detail(
                    R.string.route_distance,
                    formatDistance(details.fixedDistanceKm, distanceUnit),
                    formatDistanceSpoken(details.fixedDistanceKm, distanceUnit),
                )
                Detail(
                    R.string.route_remaining,
                    details.remainingDistanceKm?.let {
                        formatDistance(
                            it,
                            distanceUnit,
                        )
                    } ?: stringResource(R.string.route_waiting_position),
                    details.remainingDistanceKm?.let { formatDistanceSpoken(it, distanceUnit) }
                        ?: stringResource(R.string.route_waiting_position),
                )
                Detail(
                    R.string.route_arrival,
                    arrivalText(details) ?: stringResource(R.string.route_waiting_speed),
                )
            }
            if (state.departure != null ||
                state.destination != null
            ) {
                TextButton(onClick = onClearAll, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.route_clear_all), color = SmartFlightTheme.colors.accent)
                }
            }
            state.error?.let {
                Text(
                    stringResource(
                        when (it) {
                            kniezrec.com.flightinfo.route.RouteError.RESTORE -> R.string.route_restore_error
                        },
                    ),
                    color =
                        SmartFlightTheme.colors.error,
                )
            }
            if (state.error != null) {
                TextButton(onClick = onRestoreRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.route_retry), color = SmartFlightTheme.colors.accent)
                }
            }
        }
    }
}

@Composable private fun EndpointRow(
    endpoint: RouteEndpoint,
    city: String?,
    onChoose: (RouteEndpoint) -> Unit,
    onClear: (RouteEndpoint) -> Unit,
) {
    val role = if (endpoint == RouteEndpoint.DEPARTURE) R.string.route_departure else R.string.route_destination
    val choose = if (endpoint == RouteEndpoint.DEPARTURE) R.string.route_choose_departure else R.string.route_choose_destination
    val roleText = stringResource(role)
    val chooseText = stringResource(choose)
    val icon = if (endpoint == RouteEndpoint.DEPARTURE) R.drawable.ic_route_departure else R.drawable.ic_route_destination
    val iconDescription =
        if (endpoint ==
            RouteEndpoint.DEPARTURE
        ) {
            R.string.route_departure_icon_description
        } else {
            R.string.route_destination_icon_description
        }
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(painterResource(icon), stringResource(iconDescription), tint = SmartFlightTheme.colors.accent)
        TextButton(
            onClick = { onChoose(endpoint) },
            modifier =
                Modifier.weight(1f).semantics {
                    contentDescription =
                        "$roleText, ${city ?: chooseText}"
                },
        ) { Text("$roleText: ${city ?: chooseText}", color = SmartFlightTheme.colors.accent) }
        if (city !=
            null
        ) {
            TextButton(onClick = { onChoose(endpoint) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.route_edit), color = SmartFlightTheme.colors.accent)
            }
            TextButton(
                onClick = {
                    onClear(endpoint)
                },
                modifier =
                    Modifier.heightIn(
                        min = 48.dp,
                    ),
            ) { Text(stringResource(R.string.route_clear), color = SmartFlightTheme.colors.accent) }
        }
    }
}

@Composable private fun Detail(
    label: Int,
    value: String,
    spoken: String = value,
) {
    val detailDescription = stringResource(R.string.route_detail_description, stringResource(label), spoken)
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp).semantics {
            contentDescription = detailDescription
        },
    ) {
        LabelText(stringResource(label))
        ValueText(value)
    }
}

@Composable
private fun formatDistance(
    value: Double,
    unit: DistanceUnit,
): String =
    stringResource(
        R.string.distance_value,
        formatUnitNumber(convertDistance(value, unit)) ?: "—",
        stringResource(unit.labels.symbol),
    )

@Composable
private fun formatDistanceSpoken(
    value: Double,
    unit: DistanceUnit,
): String =
    stringResource(
        R.string.distance_spoken_value,
        formatUnitNumber(convertDistance(value, unit)) ?: "—",
        stringResource(unit.labels.accessibility),
    )

/**
 * "<arrival> (<duration>)": the arrival as a short date-time of the current locale in the
 * destination's time zone, and the remaining flight time as hours:minutes; null while unknown.
 */
@Composable
private fun arrivalText(details: RouteDetails): String? {
    val locale = LocalConfiguration.current.locales[0]
    val arrival = details.arrival
    val duration = details.duration
    val zone = details.destinationZone
    return remember(arrival, duration, zone, locale) {
        if (arrival == null || duration == null) {
            null
        } else {
            val dateTime =
                DateTimeFormatter
                    .ofLocalizedDateTime(FormatStyle.SHORT)
                    .withLocale(locale)
                    .format(arrival.atZone(zone))
            val hoursMinutes = "%02d:%02d".format(Locale.ROOT, duration.toHours(), duration.toMinutesPart())
            "$dateTime ($hoursMinutes)"
        }
    }
}
