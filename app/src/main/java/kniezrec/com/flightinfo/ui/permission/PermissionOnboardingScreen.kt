package kniezrec.com.flightinfo.ui.permission

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
import androidx.compose.ui.platform.testTag
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
import kniezrec.com.flightinfo.permission.LocationPermissionState

private val LightLavender = Color(0xFFD9D9ED)

// The legacy muted lavender does not meet contrast at this size; use the accessible light token.
private val BodyLavender = Color(0xFFD9D9ED)
internal val smartFlightPageColor = Color(0xFF484685)
internal const val PERMISSION_STATE_CARD_TEST_TAG = "permission_state_card"

@Composable
fun PermissionOnboardingScreen(
    state: LocationPermissionState,
    onGrantPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    announceStateChange: Boolean = false,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                color = LightLavender,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Crossfade(targetState = state, animationSpec = tween(180), label = "permission state") { currentState ->
                    PermissionStateCard(currentState, onGrantPermission, onOpenSettings, announceStateChange)
                }
            }
        }
    }
}

@Composable
private fun PermissionStateCard(
    state: LocationPermissionState,
    onGrantPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    announceStateChange: Boolean,
) {
    val content =
        when (state) {
            LocationPermissionState.Requestable ->
                PermissionCardContent(
                    R.string.permission_title,
                    R.string.permission_body,
                    R.string.permission_grant,
                    R.string.permission_request_hint,
                    onGrantPermission,
                )
            LocationPermissionState.SettingsRequired ->
                PermissionCardContent(
                    R.string.permission_needed_title,
                    R.string.permission_needed_body,
                    R.string.permission_open_settings,
                    R.string.permission_settings_hint,
                    onOpenSettings,
                )
            LocationPermissionState.Granted -> error("Fine location is rendered by the GNSS status screen")
        }
    val actionHint = content.actionHint?.let { stringResource(it) }
    Card(
        modifier =
            Modifier
                .padding(top = 12.dp, bottom = 12.dp)
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .testTag(PERMISSION_STATE_CARD_TEST_TAG),
        shape =
            androidx.compose.foundation.shape
                .RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cardPurple),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(content.title),
                modifier = Modifier.semantics { if (announceStateChange) liveRegion = LiveRegionMode.Polite },
                color = LightLavender,
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
            )
            Text(
                text = stringResource(content.body),
                modifier = Modifier.padding(top = 12.dp),
                color = BodyLavender,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 25.sp, textAlign = TextAlign.Center),
            )
            if (content.action != null && actionHint != null && content.onAction != null) {
                var actionFocused by remember { mutableStateOf(false) }
                TextButton(
                    onClick = content.onAction,
                    modifier =
                        Modifier
                            .padding(top = 12.dp)
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .then(
                                if (actionFocused) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = actionCyan,
                                        shape =
                                            androidx.compose.foundation.shape
                                                .RoundedCornerShape(4.dp),
                                    )
                                } else {
                                    Modifier
                                },
                            ).onFocusChanged { actionFocused = it.isFocused }
                            .semantics {
                                role = Role.Button
                                stateDescription = actionHint
                            },
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = actionCyan),
                ) {
                    Text(
                        text = stringResource(content.action),
                        style =
                            MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 18.sp,
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                            ),
                    )
                }
            }
        }
    }
}

private data class PermissionCardContent(
    val title: Int,
    val body: Int,
    val action: Int?,
    val actionHint: Int?,
    val onAction: (() -> Unit)?,
)
