package kniezrec.com.flightinfo.flight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.displayunits.UnitKey
import kniezrec.com.flightinfo.displayunits.UnitPreferences
import kniezrec.com.flightinfo.displayunits.convertAltitude
import kniezrec.com.flightinfo.displayunits.convertPressure
import kniezrec.com.flightinfo.displayunits.convertSpeed
import kniezrec.com.flightinfo.displayunits.convertVerticalSpeed
import kniezrec.com.flightinfo.displayunits.formatUnitNumber
import kniezrec.com.flightinfo.displayunits.ui.labels
import kniezrec.com.flightinfo.flight.FlightParametersState
import kniezrec.com.flightinfo.ui.theme.CardHeader
import kniezrec.com.flightinfo.ui.theme.LabelText
import kniezrec.com.flightinfo.ui.theme.SmartFlightCard
import kniezrec.com.flightinfo.ui.theme.SmartFlightCardDefaults
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme
import kniezrec.com.flightinfo.ui.theme.StatusPill
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
            FlightParametersContent(state as? FlightParametersState.Readings, preferences)

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

/**
 * The header (with the waiting pill until the first reading) and the value tiles of the redesign
 * (TASK-043) in the design's order: speed, altitude, vertical speed, then pressure. Without
 * [readings] every tile shows "—".
 */
@Composable
private fun FlightParametersContent(
    readings: FlightParametersState.Readings?,
    preferences: UnitPreferences,
) {
    Column(Modifier.fillMaxWidth()) {
        CardHeader(
            R.drawable.ic_card_flight,
            stringResource(R.string.flight_parameters_title),
            trailing = if (readings == null) ({ StatusPill(stringResource(R.string.flight_parameters_waiting)) }) else null,
        )
        val speed = readings?.speedKilometresPerHour?.let { convertSpeed(it, preferences.speed) }
        val altitude = readings?.altitudeMetres?.let { convertAltitude(it, preferences.altitude) }
        val verticalSpeed = readings?.verticalSpeedMetresPerSecond?.let { convertVerticalSpeed(it, preferences.verticalSpeed) }
        val pressure = readings?.pressureMillibars?.let { convertPressure(it, preferences.pressure) }
        val tiles: List<@Composable (Modifier) -> Unit> =
            listOf(
                { ParameterTile(R.drawable.ic_tile_gauge, R.string.flight_speed, preferences.speed, speed, signed = false, it) },
                {
                    ParameterTile(
                        R.drawable.ic_tile_altitude,
                        R.string.flight_altitude,
                        preferences.altitude,
                        altitude,
                        signed = false,
                        it,
                    )
                },
                {
                    ParameterTile(
                        R.drawable.ic_tile_vertical_speed,
                        R.string.flight_vertical_speed,
                        preferences.verticalSpeed,
                        verticalSpeed,
                        signed = true,
                        it,
                    )
                },
                { ParameterTile(R.drawable.ic_tile_gauge, R.string.flight_pressure, preferences.pressure, pressure, signed = false, it) },
            )
        ParameterTiles(tiles, Modifier.padding(top = 16.dp))
    }
}

/**
 * [tiles] in rows of equal columns with [SmartFlightColors.cardOutline] dividers between them: four
 * columns on a wide card, two on a phone, one above font scale 1.3 so no value is clipped.
 */
@Composable
private fun ParameterTiles(
    tiles: List<@Composable (Modifier) -> Unit>,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns =
            when {
                LocalDensity.current.fontScale > STACKED_FONT_SCALE -> 1
                maxWidth >= FOUR_COLUMNS_MIN_WIDTH -> 4
                else -> 2
            }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    row.forEachIndexed { index, tile ->
                        if (index > 0) VerticalDivider(color = SmartFlightTheme.colors.cardOutline)
                        tile(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * One value tile: its decorative [icon] (TASK-044), the number (Material `headlineMedium`, tabular figures; "—" while unknown), the
 * [label] and the [unit]'s symbol below it; read as one "label, value" description.
 */
@Composable
private fun ParameterTile(
    icon: Int,
    label: Int,
    unit: UnitKey,
    value: Double?,
    signed: Boolean,
    modifier: Modifier,
) {
    val labelText = stringResource(label)
    val number = formatUnitNumber(value, signed)
    val spokenValue =
        number?.let { stringResource(R.string.flight_value_accessibility, it, stringResource(unit.labels.accessibility)) }
            ?: stringResource(R.string.flight_unavailable_accessibility)
    val tileDescription = stringResource(R.string.flight_row_accessibility, labelText, spokenValue)
    Column(
        modifier
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) { contentDescription = tileDescription }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.padding(bottom = 4.dp).size(24.dp),
            tint = SmartFlightTheme.colors.labelText,
        )
        // Full-width, centered texts: a wrapped line never extends past its text's bounds.
        ValueText(
            number ?: stringResource(R.string.flight_unavailable),
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        LabelText(
            labelText,
            Modifier.fillMaxWidth().padding(top = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
        LabelText(
            stringResource(unit.labels.symbol),
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/** Above this font scale the tiles stack in one column. */
private const val STACKED_FONT_SCALE = 1.3f

/** From this card content width the four tiles share one row (tablets, landscape). */
private val FOUR_COLUMNS_MIN_WIDTH = 480.dp
