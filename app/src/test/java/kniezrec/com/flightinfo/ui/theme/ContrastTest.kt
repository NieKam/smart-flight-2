package kniezrec.com.flightinfo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WCAG 2.1 contrast of every foreground/background pair the app draws (TASK-037), in every scheme:
 * text at least 4.5:1 (1.4.3), icons, switch thumbs, borders, chart bars, the horizon marks and the
 * map buttons at least 3:1 (1.4.11). The table is printed for the PR.
 */
class ContrastTest {
    @Test
    fun everyTextPairPassesAa() {
        val failures =
            SCHEMES.flatMap { (scheme, colors) ->
                textPairs(colors).mapNotNull { pair -> pair.failure(scheme, TEXT_MINIMUM) }
            }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun everyComponentPairPassesThreeToOne() {
        val failures =
            SCHEMES.flatMap { (scheme, colors) ->
                componentPairs(colors).mapNotNull { pair -> pair.failure(scheme, COMPONENT_MINIMUM) }
            }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun printsTheContrastTable() {
        println("| Scheme | Foreground | Background | Ratio | Minimum |")
        println("|---|---|---|---|---|")
        SCHEMES.forEach { (scheme, colors) ->
            textPairs(colors).forEach { println(it.row(scheme, TEXT_MINIMUM)) }
            componentPairs(colors).forEach { println(it.row(scheme, COMPONENT_MINIMUM)) }
        }
    }

    /** Text: labels, values, titles, actions and errors on every surface they sit on. */
    private fun textPairs(colors: SmartFlightColors): List<ColorPair> {
        val surfaces =
            listOf(
                "card" to colors.card,
                "page" to colors.page,
                "raised (dialog, menu)" to colors.raised,
            )
        val onSurfaces =
            surfaces.flatMap { (surface, background) ->
                listOf(
                    ColorPair("labelText", colors.labelText, surface, background),
                    ColorPair("valueText", colors.valueText, surface, background),
                    ColorPair("accent (text button)", colors.accent, surface, background),
                    ColorPair("error", colors.error, surface, background),
                )
            }
        // Missing-sensor overlay: the card-colored veil over the blurred preview. The message is
        // checked over the lightest/darkest preview pixel (a value-colored stroke); the "Hide" action
        // sits over the preview's card background.
        val veilOverValue = colors.card.copy(alpha = MISSING_SENSOR_VEIL_ALPHA).compositeOver(colors.valueText)
        val veilOverCard = colors.card.copy(alpha = MISSING_SENSOR_VEIL_ALPHA).compositeOver(colors.card)
        return onSurfaces +
            listOf(
                ColorPair("toolbarTitle", colors.toolbarTitle, "topBar", colors.topBar),
                ColorPair("toolbarTitle", colors.toolbarTitle, "topBarScrolled", colors.topBarScrolled),
                ColorPair("inverseOnSurface (snackbar text)", colors.inverseOnSurface, "inverseSurface", colors.inverseSurface),
                ColorPair("inversePrimary (snackbar action)", colors.inversePrimary, "inverseSurface", colors.inverseSurface),
                ColorPair("valueText (placeholder message)", colors.valueText, "veil over a value stroke", veilOverValue),
                ColorPair("accent (placeholder Hide)", colors.accent, "veil over card", veilOverCard),
            )
    }

    /** Non-text: icons, controls, chart bars, the horizon instrument and the map buttons. */
    private fun componentPairs(colors: SmartFlightColors): List<ColorPair> =
        listOf(
            ColorPair("valueText (card icons)", colors.valueText, "card", colors.card),
            ColorPair("toolbarTitle (top bar icons)", colors.toolbarTitle, "topBar", colors.topBar),
            ColorPair("accent (checked switch thumb, focus border)", colors.accent, "page", colors.page),
            ColorPair("valueText (unchecked switch thumb)", colors.valueText, "page", colors.page),
            ColorPair("labelText (switch and field border)", colors.labelText, "page", colors.page),
            ColorPair("accent (selected radio)", colors.accent, "raised", colors.raised),
            ColorPair("labelText (unselected radio)", colors.labelText, "raised", colors.raised),
            ColorPair("satelliteUsed (chart bar)", colors.satelliteUsed, "card", colors.card),
            ColorPair("satelliteUnused (chart bar)", colors.satelliteUnused, "card", colors.card),
            ColorPair("horizonLine", colors.horizonLine, "horizonSky", colors.horizonSky),
            ColorPair("horizonLine", colors.horizonLine, "horizonGround", colors.horizonGround),
            ColorPair("mapInk (map button icon)", colors.mapInk, "map button over a white tile", mapButtonOver(colors, WHITE_TILE)),
            ColorPair("mapInk (map button icon)", colors.mapInk, "map button over a black tile", mapButtonOver(colors, BLACK_TILE)),
        )

    private fun mapButtonOver(
        colors: SmartFlightColors,
        tile: Color,
    ) = colors.mapHalo.copy(alpha = MAP_BUTTON_CONTAINER_ALPHA).compositeOver(tile)

    private class ColorPair(
        val foregroundName: String,
        val foreground: Color,
        val backgroundName: String,
        val background: Color,
    ) {
        val ratio: Float get() = contrastRatio(foreground, background)

        fun failure(
            scheme: String,
            minimum: Float,
        ): String? =
            if (ratio >= minimum) {
                null
            } else {
                "$scheme: $foregroundName on $backgroundName is %.2f:1, needs %.1f:1".format(ratio, minimum)
            }

        fun row(
            scheme: String,
            minimum: Float,
        ) = "| $scheme | $foregroundName ${hex(foreground)} | $backgroundName ${hex(background)} | %.2f | %.1f |".format(ratio, minimum)

        private fun hex(color: Color) =
            "#%02X%02X%02X".format(
                Math.round(color.red * 255),
                Math.round(color.green * 255),
                Math.round(color.blue * 255),
            )
    }

    private companion object {
        const val TEXT_MINIMUM = 4.5f
        const val COMPONENT_MINIMUM = 3f
        val WHITE_TILE = Color(0xFFFFFFFF)
        val BLACK_TILE = Color(0xFF000000)
        val SCHEMES = listOf("dark" to DarkSmartFlightColors, "light" to LightSmartFlightColors)
    }
}
