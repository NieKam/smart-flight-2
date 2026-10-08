package kniezrec.com.flightinfo.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kniezrec.com.flightinfo.R

/**
 * The header of a dashboard card (TASK-040): the card's [icon] in a round
 * [SmartFlightColors.accentContainer] badge, the [title] in [SmartFlightColors.valueText]
 * (`titleLarge`) and an optional [trailing] element such as a [StatusPill] at the end. The badge is
 * decorative. [titleModifier] carries the title's semantics (for example a live region).
 *
 * Above font scale 1.3 the trailing element goes below the title, so neither is clipped.
 */
@Composable
fun CardHeader(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val stacked = LocalDensity.current.fontScale > STACKED_FONT_SCALE
        val trailingMaxWidth = maxWidth * TRAILING_MAX_WIDTH_FRACTION
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CardIconBadge(icon)
                Spacer(Modifier.width(12.dp))
                ValueText(title, titleModifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                if (trailing != null && !stacked) {
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.widthIn(max = trailingMaxWidth)) { trailing() }
                }
            }
            if (trailing != null && stacked) {
                Box(Modifier.padding(top = 8.dp)) { trailing() }
            }
        }
    }
}

/** The card's icon, [SmartFlightColors.accent] on a round [SmartFlightColors.accentContainer] badge. */
@Composable
private fun CardIconBadge(
    @DrawableRes icon: Int,
) {
    val colors = SmartFlightTheme.colors
    Box(
        Modifier
            .size(BADGE_SIZE)
            .clip(CircleShape)
            .background(colors.accentContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ICON_SIZE), tint = colors.accent)
    }
}

/**
 * A status pill (TASK-040): an accent dot and [text] in [SmartFlightColors.accent] on a
 * [SmartFlightColors.accentContainer] capsule, such as "Searching…" in a card header.
 */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = SmartFlightTheme.colors
    Row(
        modifier
            .clip(CircleShape)
            .background(colors.accentContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(colors.accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = colors.accent,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val BADGE_SIZE = 40.dp
private val ICON_SIZE = 22.dp

/** Above this font scale [CardHeader] puts its trailing element below the title. */
private const val STACKED_FONT_SCALE = 1.3f

/** Share of the header the trailing element may take next to the title. */
private const val TRAILING_MAX_WIDTH_FRACTION = 0.5f

@Preview(name = "Card header, light", widthDp = 360)
@Composable
private fun CardHeaderLightPreview() {
    SmartFlightTheme(darkTheme = false) {
        SmartFlightCard(Modifier.padding(12.dp)) {
            CardHeader(R.drawable.ic_card_satellite, "GNSS status", trailing = { StatusPill("Searching…") })
        }
    }
}

@Preview(name = "Card header, dark", widthDp = 360)
@Composable
private fun CardHeaderDarkPreview() {
    SmartFlightTheme(darkTheme = true) {
        SmartFlightCard(Modifier.padding(12.dp)) {
            CardHeader(R.drawable.ic_card_flight, "Flight parameters", trailing = { StatusPill("Waiting for GPS position…") })
        }
    }
}
