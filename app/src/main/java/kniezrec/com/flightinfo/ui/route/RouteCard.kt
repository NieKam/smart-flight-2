package kniezrec.com.flightinfo.ui.route

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.route.RouteEndpoint
import kniezrec.com.flightinfo.route.RouteState
import kniezrec.com.flightinfo.route.formatKilometres
import kniezrec.com.flightinfo.ui.permission.actionCyan
import kniezrec.com.flightinfo.ui.permission.cardPurple

@Composable
fun RouteCard(
    state: RouteState,
    onChoose: (RouteEndpoint) -> Unit,
    onClear: (RouteEndpoint) -> Unit,
    onClearAll: () -> Unit,
    onRestoreRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth().heightIn(min = 120.dp), colors = CardDefaults.cardColors(containerColor = cardPurple)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.route_title),
                color =
                    androidx.compose.ui.graphics
                        .Color(0xFFD9D9ED),
            )
            EndpointRow(RouteEndpoint.DEPARTURE, state.departure?.name, onChoose, onClear)
            EndpointRow(RouteEndpoint.DESTINATION, state.destination?.name, onChoose, onClear)
            state.details?.let { details ->
                Detail(R.string.route_distance, formatKilometres(details.fixedDistanceKm))
                Detail(
                    R.string.route_remaining,
                    details.remainingDistanceKm?.let(::formatKilometres) ?: stringResource(R.string.route_waiting_position),
                )
                Detail(
                    R.string.route_arrival,
                    details.arrival?.let { a -> "$a (${details.duration})" } ?: stringResource(R.string.route_waiting_speed),
                )
            }
            if (state.departure != null ||
                state.destination != null
            ) {
                TextButton(onClick = onClearAll, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.route_clear_all), color = actionCyan)
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
                        androidx.compose.ui.graphics
                            .Color(0xFFFFB4AB),
                )
            }
            if (state.error != null) {
                TextButton(onClick = onRestoreRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.route_retry), color = actionCyan)
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
        Icon(painterResource(icon), stringResource(iconDescription), tint = actionCyan)
        TextButton(
            onClick = { onChoose(endpoint) },
            modifier =
                Modifier.weight(1f).semantics {
                    contentDescription =
                        "$roleText, ${city ?: chooseText}"
                },
        ) { Text("$roleText: ${city ?: chooseText}", color = actionCyan) }
        if (city !=
            null
        ) {
            TextButton(onClick = { onChoose(endpoint) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.route_edit), color = actionCyan)
            }
            TextButton(onClick = {
                onClear(endpoint)
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.route_clear), color = actionCyan) }
        }
    }
}

@Composable private fun Detail(
    label: Int,
    value: String,
) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            stringResource(label),
            color =
                androidx.compose.ui.graphics
                    .Color(0xFFD9D9ED),
        )
        Text(
            value,
            color =
                androidx.compose.ui.graphics
                    .Color(0xFFD9D9ED),
        )
    }
}
