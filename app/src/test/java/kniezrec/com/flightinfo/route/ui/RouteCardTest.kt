package kniezrec.com.flightinfo.route.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.route.RouteDetails
import kniezrec.com.flightinfo.route.RouteState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The card formats the raw arrival and duration values of [RouteDetails]; tall window so the whole card is on screen. */
@RunWith(AndroidJUnit4::class)
class RouteCardTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Config(qualifiers = "en-rGB-w411dp-h1000dp")
    @Test
    fun arrivalIsAShortDateTimeInTheDestinationZoneWithHoursAndMinutes() {
        show(details(arrival = ARRIVAL, duration = Duration.ofSeconds(1_112)))

        // 00:18 UTC is 01:18 in Berlin in winter.
        val expected = "${shortDateTime(Locale.UK, BERLIN)} (00:18)"
        assertTrue(expected.contains("01:18"))
        composeRule.onNodeWithText(expected).assertIsDisplayed()
        composeRule.onNode(hasContentDescription("Estimated arrival, $expected")).assertExists()
    }

    @Config(qualifiers = "en-rUS-w411dp-h1000dp")
    @Test
    fun arrivalFollowsTheCurrentLocale() {
        show(details(arrival = ARRIVAL, duration = Duration.ofSeconds(1_112)))

        composeRule.onNodeWithText("${shortDateTime(Locale.US, BERLIN)} (00:18)").assertIsDisplayed()
    }

    @Config(qualifiers = "en-rGB-w411dp-h1000dp")
    @Test
    fun durationOverADayKeepsCountingHours() {
        show(details(arrival = ARRIVAL, duration = Duration.ofHours(26).plusMinutes(5).plusSeconds(59)))

        composeRule.onNodeWithText("${shortDateTime(Locale.UK, BERLIN)} (26:05)").assertIsDisplayed()
    }

    @Config(qualifiers = "en-rGB-w411dp-h1000dp")
    @Test
    fun unknownArrivalWaitsForSpeed() {
        show(details(arrival = null, duration = null))

        composeRule.onNodeWithText("Waiting for usable speed").assertIsDisplayed()
    }

    @Config(qualifiers = "en-rGB-w411dp-h1000dp")
    @Test
    fun destinationOnlyShowsRemainingDistanceAndArrivalWithoutDistanceBetweenCities() {
        show(
            details(arrival = ARRIVAL, duration = Duration.ofSeconds(1_112)).copy(fixedDistanceKm = null),
            departure = null,
        )

        composeRule.onNodeWithText("Distance to destination").assertIsDisplayed()
        composeRule.onNodeWithText("111.2 km").assertIsDisplayed()
        composeRule.onNodeWithText("Estimated arrival").assertIsDisplayed()
        composeRule.onNodeWithText("${shortDateTime(Locale.UK, BERLIN)} (00:18)").assertIsDisplayed()
        composeRule.onAllNodesWithText("Distance between cities").assertCountEquals(0)
    }

    private fun show(
        details: RouteDetails,
        departure: NearbyCityRecord? = DEPARTURE,
    ) {
        composeRule.setContent {
            RouteCard(
                state = RouteState(departure = departure, destination = DESTINATION, details = details),
                onChoose = {},
                onClear = {},
                onClearAll = {},
            )
        }
    }

    private fun details(
        arrival: Instant?,
        duration: Duration?,
    ) = RouteDetails(
        fixedDistanceKm = 111.2,
        remainingDistanceKm = 111.2,
        arrival = arrival,
        destinationZone = BERLIN,
        duration = duration,
    )

    private fun shortDateTime(
        locale: Locale,
        zone: ZoneId,
    ): String =
        DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.SHORT)
            .withLocale(locale)
            .format(ARRIVAL.atZone(zone))

    private companion object {
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
        val ARRIVAL: Instant = Instant.parse("2020-01-01T00:18:32Z")
        val DEPARTURE = NearbyCityRecord(1L, "Alpha", "A", 0.0, 0.0, "UTC")
        val DESTINATION = NearbyCityRecord(2L, "Beta", "B", 0.0, 1.0, "Europe/Berlin")
    }
}
