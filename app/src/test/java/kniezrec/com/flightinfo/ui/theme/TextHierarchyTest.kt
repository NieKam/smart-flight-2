package kniezrec.com.flightinfo.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.about.AppVersion
import kniezrec.com.flightinfo.about.ui.AboutDialog
import kniezrec.com.flightinfo.flight.FlightParametersState
import kniezrec.com.flightinfo.flight.ui.FlightParametersCard
import kniezrec.com.flightinfo.nearby.NearbyCityState
import kniezrec.com.flightinfo.nearby.ui.NearbyCityCard
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

/** Muted labels and light values, as the original `TextLabel` / `TextValue` styles. */
@RunWith(AndroidJUnit4::class)
class TextHierarchyTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val colors = DarkSmartFlightColors

    @Test
    fun flightParametersLabelsAreMutedAndValuesLight() {
        show { FlightParametersCard(FlightParametersState.Readings(36.0, null, 100.0)) }

        assertEquals(colors.labelText, textColor("Flight parameters"))
        assertEquals(colors.labelText, textColor("Speed"))
        assertEquals(colors.labelText, textColor("Altitude"))
        assertEquals(colors.valueText, textColor("36.0 km/h"))
        assertEquals(colors.valueText, textColor("100.0 m"))
    }

    @Test
    fun nearbyCityLabelsAreMutedAndValuesLight() {
        show {
            NearbyCityCard(
                NearbyCityState.Available(
                    cityName = "Gdańsk",
                    country = "Poland",
                    distanceKilometres = 12.34,
                    zoneId = ZoneId.of("Europe/Warsaw"),
                    instant = Instant.parse("2020-07-01T06:00:00Z"),
                    utcOffsetSeconds = 7_200,
                ),
                onRetry = {},
            )
        }

        assertEquals(colors.labelText, textColor("Closest city"))
        assertEquals(colors.labelText, textColor("Country"))
        assertEquals(colors.valueText, textColor("Gdańsk"))
        assertEquals(colors.valueText, textColor("Poland"))
    }

    @Test
    fun dialogTitleIsMutedAndContentLight() {
        show { AboutDialog(AppVersion("1.0", 1), { true }, { true }) {} }

        assertEquals(colors.labelText, textColor("Smart Flight"))
        assertEquals(colors.labelText, textColor("Version"))
        assertEquals(colors.valueText, textColor("1.0 (1)"))
    }

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { SmartFlightTheme(content) }
        composeRule.waitForIdle()
    }

    /** The color the text node is laid out with. */
    private fun textColor(text: String): Color {
        val node = composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results
            .single()
            .layoutInput.style.color
    }
}
