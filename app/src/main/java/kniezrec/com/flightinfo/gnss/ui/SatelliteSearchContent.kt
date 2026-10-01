package kniezrec.com.flightinfo.gnss.ui

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.ui.theme.LabelText
import kotlinx.coroutines.delay

/** Test tag of the searching animation. */
internal const val SATELLITE_SEARCH_ANIMATION_TAG = "satelliteSearchAnimation"

/** How long the waiting text and the window tip are each shown, as in the original app. */
internal const val SEARCH_TEXT_SWITCH_MILLIS = 10_000L

/**
 * The waiting state of the GNSS card as in the original `NoSatellitesFoundView`: the original
 * `loading.json` Lottie animation, looping, above a text that alternates every
 * [SEARCH_TEXT_SWITCH_MILLIS] between "Waiting for GPS signal…" and the window tip. With system
 * animations removed (animator duration scale 0) the animation stays on its first frame. The
 * animation is decorative; only the title is a live region, so the alternating text is not
 * announced every 10 seconds.
 */
@Composable
internal fun SearchingContent() {
    var showTip by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(SEARCH_TEXT_SWITCH_MILLIS)
            showTip = !showTip
        }
    }
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.loading))
    Column(
        Modifier.fillMaxWidth(),
        Arrangement.Center,
        Alignment.CenterHorizontally,
    ) {
        LabelText(
            stringResource(R.string.gnss_status_title),
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style =
                MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
        )
        LottieAnimation(
            composition = composition,
            modifier =
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .testTag(SATELLITE_SEARCH_ANIMATION_TAG),
            isPlaying = animate,
            iterations = LottieConstants.IterateForever,
        )
        LabelText(
            stringResource(if (showTip) R.string.gps_tip else R.string.gnss_waiting),
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 25.sp, textAlign = TextAlign.Center),
        )
    }
}
