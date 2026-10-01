package kniezrec.com.flightinfo.route.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.displayunits.DistanceUnit
import kniezrec.com.flightinfo.displayunits.convertDistance
import kniezrec.com.flightinfo.displayunits.formatUnitNumber
import kniezrec.com.flightinfo.displayunits.ui.labels
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.route.RouteDetails
import kniezrec.com.flightinfo.route.RouteEndpoint
import kniezrec.com.flightinfo.route.RouteError
import kniezrec.com.flightinfo.route.RouteState
import kniezrec.com.flightinfo.ui.theme.LabelText
import kniezrec.com.flightinfo.ui.theme.SmartFlightCard
import kniezrec.com.flightinfo.ui.theme.SmartFlightCardDefaults
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme
import kniezrec.com.flightinfo.ui.theme.ValueText
import kniezrec.com.flightinfo.ui.theme.withSmallerUnit
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The route card as in the original app: two side-by-side slots (departure with the take-off icon,
 * destination with the landing icon) above label/value detail rows, and a trash icon that clears the
 * whole route. Tapping a slot picks its city; a long press (or the accessibility action) clears it.
 */
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
    SmartFlightCard(modifier, minHeight = SmartFlightCardDefaults.MinHeight) {
        Column(Modifier.fillMaxWidth()) {
            // As in the original, the hint stays until the destination (and with it the details) is set.
            if (state.destination == null) {
                LabelText(stringResource(R.string.route_hint), style = routeBody())
                Spacer(Modifier.height(8.dp))
            }
            // Empty slots are large until the details appear, then the remaining one shrinks.
            val iconSize = if (state.destination == null) LARGE_ICON else SMALL_ICON
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                EndpointSlot(RouteEndpoint.DEPARTURE, state.departure, iconSize, onChoose, onClear, Modifier.weight(1f))
                EndpointSlot(RouteEndpoint.DESTINATION, state.destination, iconSize, onChoose, onClear, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    state.details?.let { details -> Details(details, distanceUnit) }
                }
                if (state.departure != null || state.destination != null) {
                    IconButton(onClick = onClearAll) {
                        Icon(
                            painterResource(R.drawable.ic_route_delete),
                            contentDescription = stringResource(R.string.route_clear_all),
                            tint = SmartFlightTheme.colors.valueText,
                        )
                    }
                }
            }
            state.error?.let {
                Text(
                    stringResource(
                        when (it) {
                            RouteError.RESTORE -> R.string.route_restore_error
                        },
                    ),
                    color = SmartFlightTheme.colors.error,
                )
                TextButton(onClick = onRestoreRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.route_retry), color = SmartFlightTheme.colors.accent)
                }
            }
        }
    }
}

/**
 * One endpoint: its icon and "Pick …" while empty, the city name (22sp, one line) and country once
 * chosen. Departure is aligned to the start, destination to the end.
 */
@Composable private fun EndpointSlot(
    endpoint: RouteEndpoint,
    city: NearbyCityRecord?,
    iconSize: Dp,
    onChoose: (RouteEndpoint) -> Unit,
    onClear: (RouteEndpoint) -> Unit,
    modifier: Modifier = Modifier,
) {
    val departure = endpoint == RouteEndpoint.DEPARTURE
    val roleText = stringResource(if (departure) R.string.route_departure else R.string.route_destination)
    val pickText = stringResource(if (departure) R.string.route_choose_departure else R.string.route_choose_destination)
    val clearText = stringResource(if (departure) R.string.route_clear_departure else R.string.route_clear_destination)
    val description =
        if (city == null) {
            stringResource(R.string.route_endpoint_description, roleText, pickText)
        } else {
            stringResource(R.string.route_endpoint_city_description, roleText, city.name, city.country)
        }
    val alignment = if (departure) Alignment.Start else Alignment.End
    val textAlign = if (departure) TextAlign.Start else TextAlign.End
    Column(
        modifier
            .heightIn(min = 48.dp)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = pickText,
                onLongClickLabel = if (city != null) clearText else null,
                onLongClick = if (city != null) ({ onClear(endpoint) }) else null,
                onClick = { onChoose(endpoint) },
            ).semantics {
                contentDescription = description
                if (city != null) {
                    customActions =
                        listOf(
                            CustomAccessibilityAction(clearText) {
                                onClear(endpoint)
                                true
                            },
                        )
                }
            },
        horizontalAlignment = alignment,
    ) {
        if (city == null) {
            Icon(
                painterResource(if (departure) R.drawable.ic_route_take_off else R.drawable.ic_route_landing),
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = SmartFlightTheme.colors.valueText,
            )
            LabelText(pickText, textAlign = textAlign, style = routeBody())
        } else {
            Text(
                city.name,
                Modifier.fillMaxWidth(),
                color = SmartFlightTheme.colors.valueText,
                textAlign = textAlign,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
            )
            LabelText(city.country, Modifier.fillMaxWidth(), textAlign = textAlign, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable private fun Details(
    details: RouteDetails,
    distanceUnit: DistanceUnit,
) {
    Spacer(Modifier.height(8.dp))
    val unit = stringResource(distanceUnit.labels.symbol)
    details.fixedDistanceKm?.let { fixed ->
        Detail(
            R.string.route_distance,
            withSmallerUnit(formatDistance(fixed, distanceUnit), unit),
            formatDistanceSpoken(fixed, distanceUnit),
        )
    }
    val remaining = details.remainingDistanceKm
    val waitingPosition = stringResource(R.string.route_waiting_position)
    Detail(
        R.string.route_remaining,
        remaining?.let { withSmallerUnit(formatDistance(it, distanceUnit), unit) } ?: AnnotatedString(waitingPosition),
        remaining?.let { formatDistanceSpoken(it, distanceUnit) } ?: waitingPosition,
    )
    val arrival = arrivalText(details) ?: stringResource(R.string.route_waiting_speed)
    Detail(R.string.route_arrival, AnnotatedString(arrival), arrival)
}

/**
 * A muted label (Material `labelLarge`) followed by its secondary value (Material `titleMedium`,
 * tabular figures), as the original `TextLabel`/`TextValue` rows.
 */
@Composable private fun Detail(
    label: Int,
    value: AnnotatedString,
    spoken: String,
) {
    val labelText = stringResource(label)
    val detailDescription = stringResource(R.string.route_detail_description, labelText, spoken)
    BoxWithConstraints(
        Modifier.fillMaxWidth().padding(top = 8.dp).semantics {
            contentDescription = detailDescription
        },
    ) {
        // The label never takes the whole row, so the value keeps room at large font scales.
        val labelMaxWidth = maxWidth * 0.6f
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LabelText(labelText, Modifier.widthIn(max = labelMaxWidth).alignByBaseline(), style = MaterialTheme.typography.labelLarge)
            ValueText(value, Modifier.weight(1f, fill = false).alignByBaseline(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun routeBody() = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 25.sp)

private val LARGE_ICON = 100.dp
private val SMALL_ICON = 44.dp

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
