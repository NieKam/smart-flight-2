package kniezrec.com.flightinfo.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one dashboard card (TASK-039): a filled Material 3 [Card] in [SmartFlightColors.card]
 * (`surfaceContainer`) with the large shape of the shape scale (16dp), a hairline
 * [SmartFlightColors.cardOutline] and a soft shadow, as in the redesign: the light card is barely
 * lighter than the page, so the outline separates it.
 *
 * The content is a column with [contentPadding] (16dp by default; 0 for edge-to-edge content such
 * as the map or the missing-sensor overlay). With a [minHeight], content shorter than it is
 * centered vertically, as the cards' waiting and error states.
 */
@Composable
fun SmartFlightCard(
    modifier: Modifier = Modifier,
    minHeight: Dp = Dp.Unspecified,
    contentPadding: PaddingValues = SmartFlightCardDefaults.ContentPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SmartFlightTheme.colors
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.card, contentColor = colors.valueText),
        elevation = CardDefaults.cardElevation(defaultElevation = SmartFlightCardDefaults.Elevation),
        border = BorderStroke(1.dp, colors.cardOutline),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .padding(contentPadding),
            verticalArrangement = Arrangement.Center,
            content = content,
        )
    }
}

/** Defaults of [SmartFlightCard]. */
object SmartFlightCardDefaults {
    /** Padding of every card's content. */
    val ContentPadding = PaddingValues(16.dp)

    /** No padding: content drawn edge to edge (map, missing-sensor overlay). */
    val NoPadding = PaddingValues(0.dp)

    /** Minimum height of the instrument and data cards, so their waiting states do not jump. */
    val MinHeight = 160.dp

    /** Shadow of every card: just enough to lift the white light card off the page. */
    val Elevation = 1.dp
}

@Preview(name = "Card, dark", widthDp = 360)
@Composable
private fun SmartFlightCardDarkPreview() {
    SmartFlightTheme(darkTheme = true) { SmartFlightCardPreviewContent() }
}

@Preview(name = "Card, light", widthDp = 360)
@Composable
private fun SmartFlightCardLightPreview() {
    SmartFlightTheme(darkTheme = false) { SmartFlightCardPreviewContent() }
}

@Composable
private fun SmartFlightCardPreviewContent() {
    SmartFlightCard(Modifier.padding(12.dp), minHeight = SmartFlightCardDefaults.MinHeight) {
        LabelText("Flight parameters", style = MaterialTheme.typography.titleLarge)
        LabelValueRow("Speed", withSmallerUnit("812.0 km/h", "km/h"), MaterialTheme.typography.headlineMedium)
        LabelValueRow("Altitude", withSmallerUnit("10668.0 m", "m"), MaterialTheme.typography.headlineMedium)
    }
}
