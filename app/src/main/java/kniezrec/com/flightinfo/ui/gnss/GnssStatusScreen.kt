package kniezrec.com.flightinfo.ui.gnss

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
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
import kniezrec.com.flightinfo.course.CourseState
import kniezrec.com.flightinfo.flight.FlightParametersState
import kniezrec.com.flightinfo.gnss.GnssSatellite
import kniezrec.com.flightinfo.gnss.GnssStatusState
import kniezrec.com.flightinfo.horizon.HorizonState
import kniezrec.com.flightinfo.map.MapSessionRules
import kniezrec.com.flightinfo.nearby.NearbyCityState
import kniezrec.com.flightinfo.ui.gnss.MapCardState
import kniezrec.com.flightinfo.ui.permission.actionCyan
import kniezrec.com.flightinfo.ui.permission.cardPurple

private val textColor = Color(0xFFD9D9ED)

@Composable
fun GnssStatusScreen(
    state: GnssStatusState,
    flightParametersState: FlightParametersState,
    onOpenLocationSettings: () -> Unit,
    onRetry: () -> Unit,
    courseState: CourseState = CourseState.Waiting,
    onCourseRetry: () -> Unit = {},
    horizonState: HorizonState = HorizonState.Waiting,
    onHorizonCalibrate: () -> Unit = {},
    onHorizonRetry: () -> Unit = {},
    nearbyCityState: NearbyCityState = NearbyCityState.WaitingForPosition,
    onNearbyCityRetry: () -> Unit = {},
    mapState: MapCardState = MapCardState.Inactive,
    mapRules: MapSessionRules = MapSessionRules(),
    mapPositionVersion: Int = 0,
    onMapRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    mapPositionVersion
    Column(modifier = modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.app_name), color = textColor, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Crossfade(state, animationSpec = tween(180), label = "GNSS state") { GnssStatusCard(it, onOpenLocationSettings, onRetry) }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                FlightParametersCard(flightParametersState, Modifier.padding(bottom = 12.dp).widthIn(max = 600.dp))
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                CourseCard(courseState, onCourseRetry, Modifier.padding(bottom = 12.dp).widthIn(max = 600.dp))
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                HorizonCard(horizonState, onHorizonCalibrate, onHorizonRetry, Modifier.padding(bottom = 12.dp).widthIn(max = 600.dp))
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                NearbyCityCard(nearbyCityState, onNearbyCityRetry, Modifier.padding(bottom = 12.dp).widthIn(max = 600.dp))
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                MapCard(mapState, mapRules, onMapRetry, Modifier.padding(bottom = 12.dp).widthIn(max = 600.dp))
            }
        }
    }
}

@Composable
private fun GnssStatusCard(
    state: GnssStatusState,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        Modifier
            .padding(vertical = 12.dp)
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .heightIn(min = 160.dp),
        shape =
            androidx.compose.foundation.shape
                .RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cardPurple),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        when (state) {
            is GnssStatusState.Available -> AvailableContent(state.satellites)
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
            GnssStatusState.Waiting -> StaticState(R.string.gnss_status_title, R.string.gnss_waiting)
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
            is GnssStatusState.Available -> error("handled above")
        }
    Column(
        Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(horizontal = 24.dp, vertical = 20.dp),
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
    Text(
        stringResource(title),
        Modifier.semantics {
            liveRegion = LiveRegionMode.Polite
        },
        color = textColor,
        style =
            MaterialTheme.typography.titleLarge.copy(
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
    )
    Text(
        stringResource(body),
        Modifier.padding(top = 12.dp),
        color = textColor,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 25.sp, textAlign = TextAlign.Center),
    )
}

@Composable private fun AvailableContent(satellites: List<GnssSatellite>) {
    val used = satellites.count { it.usedInFix }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp)) {
        Text(
            stringResource(R.string.gnss_status_title),
            Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
            },
            color = textColor,
            style =
                MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Medium,
                ),
        )
        Text(
            pluralStringResource(R.plurals.gnss_satellites_used, used, used),
            Modifier.padding(top = 12.dp),
            color = textColor,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 25.sp),
        )
        satellites.forEachIndexed { index, satellite -> SatelliteRow(index + 1, satellite) }
    }
}

@Composable private fun SatelliteRow(
    index: Int,
    satellite: GnssSatellite,
) {
    val status = if (satellite.usedInFix) R.string.satellite_used else R.string.satellite_not_used
    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 12.dp)) {
        Text(
            stringResource(R.string.satellite_number, index),
            color = textColor,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
        )
        Text(stringResource(status), color = textColor, style = MaterialTheme.typography.bodyMedium)
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
                            actionCyan,
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
        colors = ButtonDefaults.textButtonColors(contentColor = actionCyan),
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
