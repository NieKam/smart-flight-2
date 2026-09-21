package kniezrec.com.flightinfo.ui.gnss

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.*
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.nearby.NearbyCityState
import kniezrec.com.flightinfo.ui.permission.cardPurple
import java.text.NumberFormat
import java.util.Locale
private val nearbyTextColor = androidx.compose.ui.graphics.Color(0xFFD9D9ED)

@Composable
internal fun NearbyCityCard(state: NearbyCityState, onRetry: () -> Unit, modifier: Modifier = Modifier) = Card(
    modifier.fillMaxWidth().heightIn(min = 160.dp),
    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(containerColor = cardPurple),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
) { when (state) {
    NearbyCityState.WaitingForPosition -> Static(R.string.nearby_city_title, R.string.nearby_city_waiting)
    NearbyCityState.LookingUp -> Static(R.string.nearby_city_title, R.string.nearby_city_looking_up)
    NearbyCityState.Unavailable -> Static(R.string.nearby_city_unavailable, R.string.nearby_city_unavailable_body, onRetry)
    is NearbyCityState.Available -> Available(state)
} }

@Composable private fun Static(title: Int, body: Int, retry: (() -> Unit)? = null) = Column(
    Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(24.dp, 20.dp), Arrangement.Center, Alignment.CenterHorizontally,
) {
    Title(title, TextAlign.Center)
    Text(stringResource(body), Modifier.padding(top = 12.dp), color = nearbyTextColor, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 25.sp))
    retry?.let { RetryButton(it) }
}

@Composable private fun Available(state: NearbyCityState.Available) = Column(Modifier.fillMaxWidth().padding(24.dp, 20.dp)) {
    Title(R.string.nearby_city_title); Spacer(Modifier.height(16.dp))
    val number = NumberFormat.getNumberInstance(Locale.getDefault()).apply { minimumFractionDigits = 1; maximumFractionDigits = 1 }.format(state.distanceKilometres)
    Row(R.string.nearby_city_closest, state.cityName); Row(R.string.nearby_city_country, state.country)
    Row(R.string.nearby_city_distance, stringResource(R.string.nearby_city_distance_value, number), stringResource(R.string.nearby_city_distance_spoken, number))
    val offset = utcOffsetPresentation(state.utcOffsetSeconds)
    Row(R.string.nearby_city_time, stringResource(R.string.nearby_city_time_value, state.localTime, offset.visible), stringResource(R.string.nearby_city_time_spoken, state.localTime, offset.spoken))
}


@Composable private fun RetryButton(onRetry: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val hint = stringResource(R.string.nearby_city_retry_hint)
    TextButton(
        onClick = onRetry,
        modifier = Modifier.padding(top = 12.dp).sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .border(if (focused) 2.dp else 0.dp, if (focused) androidx.compose.ui.graphics.Color(0xFF6CF0FF) else androidx.compose.ui.graphics.Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .semantics { stateDescription = hint },
        interactionSource = interactionSource,
    ) { Text(stringResource(R.string.nearby_city_retry)) }
}

private data class UtcOffsetText(val visible: String, val spoken: String)

@Composable private fun utcOffsetPresentation(totalSeconds: Int): UtcOffsetText {
    val absoluteMinutes = kotlin.math.abs(totalSeconds / 60)
    val hours = absoluteMinutes / 60
    val minutes = absoluteMinutes % 60
    val visibleSign = stringResource(if (totalSeconds < 0) R.string.nearby_city_utc_offset_minus else R.string.nearby_city_utc_offset_plus)
    val spokenSign = stringResource(if (totalSeconds < 0) R.string.nearby_city_utc_offset_spoken_minus else R.string.nearby_city_utc_offset_spoken_plus)
    val visible = stringResource(R.string.nearby_city_utc_offset_visible, visibleSign, hours, minutes)
    val hourText = pluralStringResource(R.plurals.nearby_city_utc_offset_hours, hours, hours)
    val minuteText = pluralStringResource(R.plurals.nearby_city_utc_offset_minutes, minutes, minutes)
    val spoken = stringResource(R.string.nearby_city_utc_offset_spoken, spokenSign, hourText, minuteText)
    return UtcOffsetText(visible, spoken)
}

@Composable private fun Title(text: Int, align: TextAlign = TextAlign.Start) = Text(stringResource(text), color = nearbyTextColor, textAlign = align, style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium))
@Composable private fun Row(label: Int, value: String, spoken: String = value) { val name = stringResource(label); androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics(mergeDescendants = true) { contentDescription = "$name, $spoken" }, verticalAlignment = Alignment.CenterVertically) { Text(name, Modifier.weight(1f), color = nearbyTextColor); Text(value, color = nearbyTextColor, fontWeight = FontWeight.Medium, textAlign = TextAlign.End) } }
