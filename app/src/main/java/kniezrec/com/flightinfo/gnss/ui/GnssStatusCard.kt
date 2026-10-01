package kniezrec.com.flightinfo.gnss.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.gnss.GnssSatellite
import kniezrec.com.flightinfo.gnss.GnssStatusState
import kniezrec.com.flightinfo.gnss.satelliteChartModel
import kniezrec.com.flightinfo.ui.theme.LabelText
import kniezrec.com.flightinfo.ui.theme.SmartFlightCard
import kniezrec.com.flightinfo.ui.theme.SmartFlightCardDefaults
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme
import kniezrec.com.flightinfo.ui.theme.ValueText

/**
 * The stateless GNSS status card; a new [state] cross-fades in.
 *
 * @param onOpenLocationSettings action of [GnssStatusState.LocationServicesDisabled].
 * @param onRetry action of [GnssStatusState.Error].
 */
@Composable
internal fun GnssStatusCard(
    state: GnssStatusState,
    onOpenLocationSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Crossfade(
        state,
        modifier = modifier,
        animationSpec = tween(180),
        label = "GNSS state",
    ) { GnssStatusCardContent(it, onOpenLocationSettings, onRetry) }
}

@Composable
private fun GnssStatusCardContent(
    state: GnssStatusState,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    SmartFlightCard(
        Modifier
            .padding(vertical = 12.dp)
            .widthIn(max = 600.dp),
        minHeight = SmartFlightCardDefaults.MinHeight,
    ) {
        when (state) {
            is GnssStatusState.Available -> AvailableContent(state.satellites)
            GnssStatusState.Waiting -> SearchingContent()
            else -> StaticContent(state, onOpenSettings, onRetry)
        }
    }
}

@Composable
private fun StaticContent(
    state: GnssStatusState,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    val (title, body, action, actionHint, callback) =
        when (state) {
            GnssStatusState.Waiting, is GnssStatusState.Available -> error("handled above")
            GnssStatusState.LocationServicesDisabled ->
                StaticState(
                    R.string.location_services_off_title,
                    R.string.location_services_off_body,
                    R.string.open_location_settings,
                    R.string.open_location_settings_hint,
                    onOpenSettings,
                )
            GnssStatusState.Unavailable -> StaticState(R.string.gnss_unavailable_title, R.string.gnss_unavailable_body)
            GnssStatusState.Error ->
                StaticState(
                    R.string.gnss_error_title,
                    R.string.gnss_error_body,
                    R.string.gnss_try_again,
                    R.string.gnss_try_again_hint,
                    onRetry,
                )
        }
    Column(
        Modifier.fillMaxWidth(),
        Arrangement.Center,
        Alignment.CenterHorizontally,
    ) {
        StateText(title, body)
        if (action != null && callback != null) GnssAction(action, actionHint!!, callback)
    }
}

private data class StaticState(
    val title: Int,
    val body: Int,
    val action: Int? = null,
    val hint: Int? = null,
    val callback: (() -> Unit)? = null,
)

@Composable private fun StateText(
    title: Int,
    body: Int,
) {
    LabelText(
        stringResource(title),
        Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
        },
        style =
            MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
    )
    ValueText(
        stringResource(body),
        Modifier.padding(top = 12.dp),
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 25.sp, textAlign = TextAlign.Center),
    )
}

@Composable private fun AvailableContent(satellites: List<GnssSatellite>) {
    val chart = remember(satellites) { satelliteChartModel(satellites) }
    Column(Modifier.fillMaxWidth()) {
        LabelText(
            stringResource(R.string.gnss_status_title),
            Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
            },
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Medium,
                ),
        )
        ValueText(
            pluralStringResource(R.plurals.gnss_satellites_used, chart.usedCount, chart.usedCount),
            Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 25.sp),
        )
        SatelliteChart(chart, Modifier.padding(top = 16.dp).fillMaxWidth().height(SATELLITE_CHART_HEIGHT))
    }
}

@Composable
private fun GnssAction(
    action: Int,
    hint: Int,
    callback: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val hintText = stringResource(hint)

    TextButton(
        onClick = callback,
        modifier =
            Modifier
                .padding(top = 12.dp)
                .sizeIn(
                    minWidth = 48.dp,
                    minHeight = 48.dp,
                ).then(
                    if (focused) {
                        Modifier.border(
                            2.dp,
                            SmartFlightTheme.colors.accent,
                            androidx.compose.foundation.shape
                                .RoundedCornerShape(4.dp),
                        )
                    } else {
                        Modifier
                    },
                ).onFocusChanged {
                    focused = it.isFocused
                }.semantics {
                    role = Role.Button
                    stateDescription = hintText
                },
        contentPadding = PaddingValues(horizontal = 12.dp),
        colors = ButtonDefaults.textButtonColors(contentColor = SmartFlightTheme.colors.accent),
    ) {
        Text(
            stringResource(action),
            Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
            },
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                ),
        )
    }
}
