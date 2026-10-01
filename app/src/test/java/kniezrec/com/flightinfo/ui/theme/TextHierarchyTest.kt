package kniezrec.com.flightinfo.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.about.AppVersion
import kniezrec.com.flightinfo.about.ui.AboutDialog
import kniezrec.com.flightinfo.course.CourseState
import kniezrec.com.flightinfo.course.ui.CourseCard
import kniezrec.com.flightinfo.flight.FlightParametersState
import kniezrec.com.flightinfo.flight.ui.FlightParametersCard
import kniezrec.com.flightinfo.nearby.NearbyCityState
import kniezrec.com.flightinfo.nearby.ui.NearbyCityCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

/**
 * Muted labels and light values (the original `TextLabel` / `TextValue` roles) and the type scale of
 * TASK-037: labels in `labelLarge`, key values in `headlineMedium`/`displayMedium` and secondary
 * values in `titleMedium`, with tabular figures and a smaller unit.
 */
@RunWith(AndroidJUnit4::class)
class TextHierarchyTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val colors = DarkSmartFlightColors
    private val type = appTypography

    @Test
    fun flightParametersLabelsAreMutedAndValuesLight() {
        show { FlightParametersCard(FlightParametersState.Readings(36.0, null, 100.0)) }

        assertEquals(colors.labelText, layout("Flight parameters").color())
        assertEquals(colors.labelText, layout("Speed").color())
        assertEquals(colors.labelText, layout("Altitude").color())
        assertEquals(colors.valueText, layout("36.0 km/h").color())
        assertEquals(colors.valueText, layout("100.0 m").color())
    }

    @Test
    fun keyFlightValuesAreTabularHeadlinesWithASmallerUnit() {
        show { FlightParametersCard(FlightParametersState.Readings(36.0, null, 12500.0)) }

        val speed = layout("36.0 km/h")
        val style = speed.layoutInput.style
        assertEquals(type.headlineMedium.fontSize, style.fontSize)
        assertEquals(TABULAR_FIGURES, style.fontFeatureSettings)
        assertUnitSmaller(speed, unit = "km/h")
        assertUnitSmaller(layout("12500.0 m"), unit = "m")

        // Labels: the smaller, medium-weight label style, clearly below the value size.
        val label = layout("Speed").layoutInput.style
        assertEquals(type.labelLarge.fontSize, label.fontSize)
        assertEquals(FontWeight.Medium, label.fontWeight)
        assertTrue(label.fontSize.value < style.fontSize.value)
        // Card title.
        assertEquals(type.titleLarge.fontSize, layout("Flight parameters").layoutInput.style.fontSize)
    }

    @Test
    fun compassHeadingIsATabularDisplayValue() {
        show { CourseCard(CourseState.Available(271, null), onRetry = {}, onHide = {}) }

        val heading = layout("271°").layoutInput.style
        assertEquals(colors.valueText, heading.color)
        assertEquals(type.displayMedium.fontSize, heading.fontSize)
        assertEquals(TABULAR_FIGURES, heading.fontFeatureSettings)
    }

    @Test
    fun nearbyCityLabelsAreMutedAndValuesLight() {
        show { NearbyCityCard(nearby(), onRetry = {}) }

        assertEquals(colors.labelText, layout("Closest city").color())
        assertEquals(colors.labelText, layout("Country").color())
        assertEquals(colors.valueText, layout("Gdańsk").color())
        assertEquals(colors.valueText, layout("Poland").color())
    }

    @Test
    fun nearbyDistanceIsATabularSecondaryValueWithASmallerUnit() {
        show { NearbyCityCard(nearby(), onRetry = {}) }

        val distance = layout("12.3 km")
        assertEquals(type.titleMedium.fontSize, distance.layoutInput.style.fontSize)
        assertEquals(TABULAR_FIGURES, distance.layoutInput.style.fontFeatureSettings)
        assertUnitSmaller(distance, unit = "km")
    }

    @Test
    fun flightValuesAreNotClippedAtFontScaleTwoOnANarrowScreen() {
        composeRule.setContent {
            SmartFlightTheme(content = {
                CompositionLocalProvider(LocalDensity provides Density(composeRule.density.density, 2f)) {
                    Box(Modifier.requiredWidth(320.dp).padding(horizontal = 12.dp)) {
                        FlightParametersCard(FlightParametersState.Readings(1234.5, -12.3, 12500.0, 1013.25))
                    }
                }
            })
        }
        composeRule.waitForIdle()

        for (text in listOf("Vertical speed", "1234.5 km/h", "−12.3 m/s", "12500.0 m", "1013.3 mbar")) {
            composeRule.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
            assertFalse("$text is clipped", layout(text).hasVisualOverflow)
        }
    }

    @Test
    fun dialogTitleIsMutedAndContentLight() {
        show { AboutDialog(AppVersion("1.0", 1), { true }, { true }) {} }

        assertEquals(colors.labelText, layout("Smart Flight").color())
        assertEquals(colors.labelText, layout("Version").color())
        assertEquals(colors.valueText, layout("1.0 (1)").color())
    }

    private fun nearby() =
        NearbyCityState.Available(
            cityName = "Gdańsk",
            country = "Poland",
            distanceKilometres = 12.34,
            zoneId = ZoneId.of("Europe/Warsaw"),
            instant = Instant.parse("2020-07-01T06:00:00Z"),
            utcOffsetSeconds = 7_200,
        )

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { SmartFlightTheme(content = content) }
        composeRule.waitForIdle()
    }

    /** The unit span covers [unit] at the end of the text and is smaller than the number. */
    private fun assertUnitSmaller(
        layout: TextLayoutResult,
        unit: String,
    ) {
        val text = layout.layoutInput.text
        val span = text.spanStyles.single()
        assertEquals(unit, text.text.substring(span.start, span.end))
        val unitSize: TextUnit = span.item.fontSize
        assertTrue("unit size $unitSize is not relative", unitSize.isEm)
        assertTrue("unit size $unitSize is not smaller", unitSize.value < 1f)
    }

    private fun TextLayoutResult.color(): Color = layoutInput.style.color

    /** The layout of the text node showing [text]. */
    private fun layout(text: String): TextLayoutResult {
        val node = composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }
}
