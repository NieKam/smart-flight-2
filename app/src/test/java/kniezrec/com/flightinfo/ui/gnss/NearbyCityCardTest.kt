package kniezrec.com.flightinfo.ui.gnss

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.nearby.NearbyCityState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class NearbyCityCardTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun unavailableRetryKeepsVisibleActionLabelAndHint() {
        composeRule.setContent { NearbyCityCard(NearbyCityState.Unavailable, {}) }
        composeRule.onNodeWithText("Try again").assertIsDisplayed().assertHasClickAction()
        composeRule.onNode(hasStateDescription("Reloads nearby city data and tries the latest GPS position.")).assertExists()
    }

    @Config(qualifiers = "en-rGB")
    @Test
    fun availableRowsMergeLocalizedDistanceAndTimeOffsetSemantics() {
        composeRule.setContent { NearbyCityCard(gdansk(), {}) }
        composeRule.onNodeWithText("12.3 km").assertIsDisplayed()
        composeRule.onNode(hasContentDescription("Distance to the city, 12.3 kilometres")).assertExists()
        // 08:42 UTC is 10:42 in Gdańsk in summer; British English uses the 24-hour short style.
        composeRule.onNodeWithText("10:42 (UTC+02:00)").assertIsDisplayed()
        composeRule.onNode(hasContentDescription("Time, 10:42, UTC plus 2 hours 0 minutes")).assertExists()
    }

    @Config(qualifiers = "en-rUS")
    @Test
    fun localTimeFollowsTheCurrentLocale() {
        composeRule.setContent { NearbyCityCard(gdansk(), {}) }
        val expected =
            DateTimeFormatter
                .ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(Locale.US)
                .format(SUMMER_MORNING.atZone(ZoneId.of("Europe/Warsaw")))
        composeRule.onNodeWithText("$expected (UTC+02:00)").assertIsDisplayed()
    }

    @Config(qualifiers = "en-rGB")
    @Test
    fun negativeOffsetAndZoneTimeAreShown() {
        val newYork =
            NearbyCityState.Available(
                cityName = "New York",
                country = "United States",
                distanceKilometres = 1.0,
                zoneId = ZoneId.of("America/New_York"),
                instant = Instant.parse("2020-01-01T00:00:00Z"),
                utcOffsetSeconds = -18_000,
            )
        composeRule.setContent { NearbyCityCard(newYork, {}) }
        composeRule.onNodeWithText("19:00 (UTC−05:00)").assertIsDisplayed()
        composeRule.onNode(hasContentDescription("Time, 19:00, UTC minus 5 hours 0 minutes")).assertExists()
    }

    private fun gdansk() =
        NearbyCityState.Available(
            cityName = "Gdańsk",
            country = "Poland",
            distanceKilometres = 12.34,
            zoneId = ZoneId.of("Europe/Warsaw"),
            instant = SUMMER_MORNING,
            utcOffsetSeconds = 7_200,
        )

    private companion object {
        val SUMMER_MORNING: Instant = Instant.parse("2024-07-01T08:42:00Z")
    }
}
