package kniezrec.com.flightinfo.nearby.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.displayunits.DistanceUnit
import kniezrec.com.flightinfo.displayunits.convertDistance
import kniezrec.com.flightinfo.displayunits.formatUnitNumber
import kniezrec.com.flightinfo.displayunits.ui.labels
import kniezrec.com.flightinfo.nearby.NearbyCityState
import kniezrec.com.flightinfo.ui.theme.CardHeader
import kniezrec.com.flightinfo.ui.theme.LabelValueRow
import kniezrec.com.flightinfo.ui.theme.SmartFlightCard
import kniezrec.com.flightinfo.ui.theme.SmartFlightCardDefaults
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme
import kniezrec.com.flightinfo.ui.theme.ValueText
import kniezrec.com.flightinfo.ui.theme.drawSkyline
import kniezrec.com.flightinfo.ui.theme.withSmallerUnit
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun NearbyCityCard(
    state: NearbyCityState,
    onRetry: () -> Unit,
    distanceUnit: DistanceUnit = DistanceUnit.KILOMETRES,
    modifier: Modifier = Modifier,
) = SmartFlightCard(modifier, minHeight = SmartFlightCardDefaults.MinHeight) {
    when (state) {
        NearbyCityState.WaitingForPosition -> Static(R.string.nearby_city_title, R.string.nearby_city_waiting, illustrated = true)
        NearbyCityState.LookingUp -> Static(R.string.nearby_city_title, R.string.nearby_city_looking_up, illustrated = true)
        NearbyCityState.Unavailable -> Static(R.string.nearby_city_unavailable, R.string.nearby_city_unavailable_body, onRetry)
        is NearbyCityState.Available -> Available(state, distanceUnit)
    }
}

@Composable private fun Static(
    title: Int,
    body: Int,
    retry: (() -> Unit)? = null,
    illustrated: Boolean = false,
) = Column(
    Modifier.fillMaxWidth(),
    Arrangement.Center,
    Alignment.CenterHorizontally,
) {
    Title(title)
    ValueText(
        stringResource(body),
        Modifier.padding(top = 12.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 25.sp),
    )
    if (illustrated) WaitingSkyline(Modifier.padding(top = 12.dp).fillMaxWidth().height(SKYLINE_HEIGHT))
    retry?.let { RetryButton(it) }
}

/**
 * The waiting illustration of the redesign (TASK-045): a city skyline (`accentLight`) behind soft
 * hills (`horizonGround`, faint) with the location pin in the accent. Decorative.
 */
@Composable private fun WaitingSkyline(modifier: Modifier) {
    val colors = SmartFlightTheme.colors
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).semantics { hideFromAccessibility() },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, colors.accentContainer)))
            val base = height * SKYLINE_BASE
            drawSkyline(colors.accentLight, width * 0.02f, width * 0.36f, base, height * 0.62f)
            drawSkyline(colors.accentLight, width * 0.62f, width * 0.98f, base, height * 0.62f)
            val hills =
                Path().apply {
                    moveTo(0f, height * 0.8f)
                    quadraticTo(width * 0.25f, height * 0.62f, width * 0.5f, height * 0.8f)
                    quadraticTo(width * 0.75f, height * 0.96f, width, height * 0.74f)
                    lineTo(width, height)
                    lineTo(0f, height)
                    close()
                }
            drawPath(hills, colors.horizonGround.copy(alpha = HILLS_ALPHA))
        }
        Icon(
            painterResource(R.drawable.ic_card_place),
            contentDescription = null,
            modifier = Modifier.padding(bottom = 16.dp).size(40.dp),
            tint = colors.accent,
        )
    }
}

private val SKYLINE_HEIGHT = 120.dp

/** Bottom of the buildings, as a fraction of the illustration's height. */
private const val SKYLINE_BASE = 0.86f
private const val HILLS_ALPHA = 0.3f

@Composable private fun Available(
    state: NearbyCityState.Available,
    distanceUnit: DistanceUnit,
) = Column(Modifier.fillMaxWidth()) {
    Title(R.string.nearby_city_title)
    Spacer(Modifier.height(16.dp))
    val number = formatUnitNumber(convertDistance(state.distanceKilometres, distanceUnit)) ?: "—"
    val unit = stringResource(distanceUnit.labels.symbol)
    Row(R.string.nearby_city_closest, AnnotatedString(state.cityName), state.cityName)
    Row(R.string.nearby_city_country, AnnotatedString(state.country), state.country)
    Row(
        R.string.nearby_city_distance,
        withSmallerUnit(stringResource(R.string.distance_value, number, unit), unit),
        stringResource(
            R.string.distance_spoken_value,
            number,
            stringResource(distanceUnit.labels.accessibility),
        ),
    )
    val offset = utcOffsetPresentation(state.utcOffsetSeconds)
    val localTime = localTimeText(state.instant, state.zoneId)
    Row(
        R.string.nearby_city_time,
        AnnotatedString(stringResource(R.string.nearby_city_time_value, localTime, offset.visible)),
        stringResource(R.string.nearby_city_time_spoken, localTime, offset.spoken),
    )
}

@Composable private fun RetryButton(onRetry: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val hint = stringResource(R.string.nearby_city_retry_hint)
    TextButton(
        onClick = onRetry,
        modifier =
            Modifier
                .padding(top = 12.dp)
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .border(
                    if (focused) 2.dp else 0.dp,
                    if (focused) {
                        SmartFlightTheme.colors.accent
                    } else {
                        Color.Transparent
                    },
                    androidx.compose.foundation.shape
                        .RoundedCornerShape(4.dp),
                ).semantics { stateDescription = hint },
        interactionSource = interactionSource,
    ) { Text(stringResource(R.string.nearby_city_retry)) }
}

/** The city's local time at [instant], in the short time style of the current locale. */
@Composable private fun localTimeText(
    instant: Instant,
    zoneId: ZoneId,
): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(instant, zoneId, locale) {
        DateTimeFormatter
            .ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(locale)
            .format(instant.atZone(zoneId))
    }
}

private data class UtcOffsetText(
    val visible: String,
    val spoken: String,
)

@Composable private fun utcOffsetPresentation(totalSeconds: Int): UtcOffsetText {
    val absoluteMinutes = kotlin.math.abs(totalSeconds / 60)
    val hours = absoluteMinutes / 60
    val minutes = absoluteMinutes % 60
    val visibleSign = stringResource(if (totalSeconds < 0) R.string.nearby_city_utc_offset_minus else R.string.nearby_city_utc_offset_plus)
    val spokenSign =
        stringResource(
            if (totalSeconds <
                0
            ) {
                R.string.nearby_city_utc_offset_spoken_minus
            } else {
                R.string.nearby_city_utc_offset_spoken_plus
            },
        )
    val visible = stringResource(R.string.nearby_city_utc_offset_visible, visibleSign, hours, minutes)
    val hourText = pluralStringResource(R.plurals.nearby_city_utc_offset_hours, hours, hours)
    val minuteText = pluralStringResource(R.plurals.nearby_city_utc_offset_minutes, minutes, minutes)
    val spoken = stringResource(R.string.nearby_city_utc_offset_spoken, spokenSign, hourText, minuteText)
    return UtcOffsetText(visible, spoken)
}

@Composable private fun Title(text: Int) = CardHeader(R.drawable.ic_card_place, stringResource(text))

/** A label and its secondary value (Material `titleMedium`, tabular figures), read as one description. */
@Composable private fun Row(
    label: Int,
    value: AnnotatedString,
    spoken: String,
) {
    val name = stringResource(label)
    val rowDescription = stringResource(R.string.card_row_description, name, spoken)
    LabelValueRow(
        label = name,
        value = value,
        valueStyle = MaterialTheme.typography.titleMedium,
        modifier =
            Modifier.heightIn(min = 48.dp).semantics(mergeDescendants = true) {
                contentDescription = rowDescription
            },
    )
}
