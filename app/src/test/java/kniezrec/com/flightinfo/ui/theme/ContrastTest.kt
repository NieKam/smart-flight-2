package kniezrec.com.flightinfo.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** WCAG contrast of the palette pairs the app draws (TASK-018 contrast section). */
class ContrastTest {
    private val colors = DefaultSmartFlightColors

    @Test
    fun valuesAndBodyTextPassAaOnCardAndPage() {
        assertAtLeast(4.5f, colors.valueText, colors.card, "valueText on card")
        assertAtLeast(4.5f, colors.valueText, colors.page, "valueText on page")
        assertAtLeast(4.5f, colors.toolbarTitle, colors.card, "toolbarTitle on card")
        assertAtLeast(4.5f, colors.valueText, colors.toastBackground, "valueText on toastBackground")
    }

    @Test
    fun accentPassesAsUiComponentOnCardAndAsTextOnPage() {
        assertAtLeast(3f, colors.accent, colors.card, "accent on card")
        assertAtLeast(4.5f, colors.accent, colors.page, "accent on page")
        assertAtLeast(4.5f, colors.accent, colors.toastBackground, "accent on toastBackground")
    }

    @Test
    fun mutedLabelsAreTheDocumentedException() {
        // The original design's muted labels are below AA; kept by the palette decision (README
        // open question 1). The ratios are pinned so a change of either color is noticed.
        val onCard = contrastRatio(colors.labelText, colors.card)
        val onPage = contrastRatio(colors.labelText, colors.page)
        println("labelText contrast: on card %.2f:1, on page %.2f:1".format(onCard, onPage))
        println(
            "error contrast: on card %.2f:1, on page %.2f:1".format(
                contrastRatio(colors.error, colors.card),
                contrastRatio(colors.error, colors.page),
            ),
        )
        assertEquals(2.50f, onCard, 0.01f)
        assertEquals(3.35f, onPage, 0.01f)
    }

    private fun assertAtLeast(
        minimum: Float,
        foreground: Color,
        background: Color,
        pair: String,
    ) {
        val ratio = contrastRatio(foreground, background)
        println("$pair: %.2f:1".format(ratio))
        assertTrue("$pair is %.2f:1, needs %.1f:1".format(ratio, minimum), ratio >= minimum)
    }
}
