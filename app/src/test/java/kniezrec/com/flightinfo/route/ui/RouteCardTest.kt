package kniezrec.com.flightinfo.route.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.route.RouteDetails
import kniezrec.com.flightinfo.route.RouteEndpoint
import kniezrec.com.flightinfo.route.RouteError
import kniezrec.com.flightinfo.route.RouteState
import org.junit.Assert.assertEquals
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

/** The card's slots and actions (TASK-033); it formats the raw arrival and duration values of [RouteDetails]; tall window so the whole card is on screen. */
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
        composeRule.onAllNodesWithText("Distance").assertCountEquals(0)
    }

    @Config(qualifiers = "en-rGB-w411dp-h1000dp")
    @Test
    fun emptyRouteShowsTheHintAndBothPickSlotsWithoutClearActions() {
        showActions(RouteState())

        composeRule.onNodeWithText("Tap to select flight route").assertIsDisplayed()
        composeRule.onNodeWithText("Pick departure").assertIsDisplayed()
        composeRule.onNodeWithText("Pick destination").assertIsDisplayed()
        val departure = composeRule.onNodeWithContentDescription("Departure, Pick departure").fetchSemanticsNode()
        assertTrue(SemanticsActions.OnLongClick !in departure.config)
        assertTrue(SemanticsActions.CustomActions !in departure.config)
        composeRule.onAllNodesWithContentDescription("Clear route").assertCountEquals(0)
    }

    @Config(qualifiers = "en-rGB-w411dp-h1000dp")
    @Test
    fun tappingASlotOpensThePickerForThatEndpoint() {
        showActions(RouteState())

        composeRule.onNodeWithText("Pick destination").assertHasClickAction().performClick()
        composeRule.onNodeWithText("Pick departure").performClick()

        composeRule.runOnIdle { assertEquals(listOf(RouteEndpoint.DESTINATION, RouteEndpoint.DEPARTURE), chosen) }
    }

    @Config(qualifiers = "en-rGB-w411dp-h1000dp")
    @Test
    fun filledSlotShowsCityAndCountryAndTapChangesIt() {
        showActions(RouteState(departure = DEPARTURE, destination = DESTINATION))

        composeRule.onNodeWithText("Alpha").assertIsDisplayed()
        composeRule.onNodeWithText("A").assertIsDisplayed()
        composeRule.onNodeWithText("Beta").assertIsDisplayed()
        composeRule.onNodeWithText("B").assertIsDisplayed()
        composeRule.onAllNodesWithText("Tap to select flight route").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("Destination, Beta, B").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf(RouteEndpoint.DESTINATION), chosen)
            assertEquals(emptyList<RouteEndpoint>(), cleared)
        }
    }

    @Config(qualifiers = "en-rGB-w411dp-h1000dp")
    @Test
    fun longPressOnAFilledSlotClearsOnlyThatEndpoint() {
        showActions(RouteState(departure = DEPARTURE, destination = DESTINATION))

        composeRule.onNodeWithText("Alpha").performTouchInput { longClick() }
        composeRule.runOnIdle {
            assertEquals(listOf(RouteEndpoint.DEPARTURE), cleared)
            assertEquals(emptyList<RouteEndpoint>(), chosen)
            assertEquals(0, clearedAll)
        }

        composeRule.onNodeWithText("Beta").performTouchInput { longClick() }
        composeRule.runOnIdle { assertEquals(listOf(RouteEndpoint.DEPARTURE, RouteEndpoint.DESTINATION), cleared) }
    }

    @Config(qualifiers = "en-rGB-w411dp-h1000dp")
    @Test
    fun filledSlotsOfferClearAsAccessibilityActions() {
        showActions(RouteState(departure = DEPARTURE, destination = DESTINATION))

        val departure = composeRule.onNodeWithContentDescription("Departure, Alpha, A").fetchSemanticsNode()
        val clearDeparture = departure.config[SemanticsActions.CustomActions].single()
        assertEquals("Clear departure", clearDeparture.label)
        assertEquals("Clear departure", departure.config[SemanticsActions.OnLongClick].label)
        assertEquals("Pick departure", departure.config[SemanticsActions.OnClick].label)
        val destination = composeRule.onNodeWithContentDescription("Destination, Beta, B").fetchSemanticsNode()
        val clearDestination = destination.config[SemanticsActions.CustomActions].single()
        assertEquals("Clear destination", clearDestination.label)

        composeRule.runOnIdle { clearDestination.action() }
        composeRule.runOnIdle { assertEquals(listOf(RouteEndpoint.DESTINATION), cleared) }
    }

    @Config(qualifiers = "en-rGB-w411dp-h1000dp")
    @Test
    fun trashClearsTheWholeRoute() {
        showActions(RouteState(departure = DEPARTURE, destination = DESTINATION, details = details(ARRIVAL, Duration.ofSeconds(1_112))))

        composeRule.onNodeWithContentDescription("Clear route").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(1, clearedAll)
            assertEquals(emptyList<RouteEndpoint>(), cleared)
        }
    }

    @Config(qualifiers = "en-rGB-w411dp-h1000dp")
    @Test
    fun trashIsOfferedWithOnlyADeparture() {
        showActions(RouteState(departure = DEPARTURE))

        composeRule.onNodeWithText("Tap to select flight route").assertIsDisplayed()
        composeRule.onNodeWithText("Pick destination").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Clear route").performClick()
        composeRule.runOnIdle { assertEquals(1, clearedAll) }
    }

    @Config(qualifiers = "en-rGB-w411dp-h1000dp")
    @Test
    fun restoreErrorOffersRetry() {
        var retried = 0
        composeRule.setContent {
            RouteCard(
                state = RouteState(error = RouteError.RESTORE),
                onChoose = {},
                onClear = {},
                onClearAll = {},
                onRestoreRetry = { retried++ },
            )
        }

        composeRule.onNodeWithText("Unable to restore saved route. Retry to read city data.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.runOnIdle { assertEquals(1, retried) }
    }

    private val chosen = mutableListOf<RouteEndpoint>()
    private val cleared = mutableListOf<RouteEndpoint>()
    private var clearedAll = 0

    private fun showActions(state: RouteState) {
        composeRule.setContent {
            RouteCard(
                state = state,
                onChoose = { chosen += it },
                onClear = { cleared += it },
                onClearAll = { clearedAll++ },
            )
        }
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
