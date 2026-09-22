package kniezrec.com.flightinfo.ui.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.about.AppVersion
import kniezrec.com.flightinfo.about.formatAppVersion

private enum class AboutFailure { Feedback, Rating }

@Composable
fun AboutDialog(
    version: AppVersion,
    onSendFeedback: () -> Boolean,
    onRate: () -> Boolean,
    onDismiss: () -> Unit,
) {
    var failure by remember { mutableStateOf<AboutFailure?>(null) }
    val versionText = formatAppVersion(version).ifEmpty { stringResource(R.string.about_version_unavailable) }
    val feedbackActionDescription = stringResource(R.string.about_feedback_action_description)
    val ratingActionDescription = stringResource(R.string.about_rating_action_description)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.mipmap.ic_launcher), null, Modifier.size(64.dp))
                Text(stringResource(R.string.app_name), Modifier.padding(start = 16.dp), style = MaterialTheme.typography.headlineSmall)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.about_version), style = MaterialTheme.typography.labelLarge)
                Text(versionText, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { failure = if (onSendFeedback()) null else AboutFailure.Feedback },
                    modifier =
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                            role = Role.Button
                            stateDescription = feedbackActionDescription
                        },
                ) { Text(stringResource(R.string.about_send_feedback)) }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { failure = if (onRate()) null else AboutFailure.Rating },
                    modifier =
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                            role = Role.Button
                            stateDescription = ratingActionDescription
                        },
                ) { Text(stringResource(R.string.about_rate)) }
                failure?.let { currentFailure ->
                    Text(
                        stringResource(
                            if (currentFailure == AboutFailure.Feedback) {
                                R.string.about_feedback_unavailable
                            } else {
                                R.string.about_rating_unavailable
                            },
                        ),
                        Modifier.padding(top = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.about_disclaimer_heading), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_disclaimer), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.about_attribution_heading), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.about_attribution), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.about_close))
            }
        },
    )
}
