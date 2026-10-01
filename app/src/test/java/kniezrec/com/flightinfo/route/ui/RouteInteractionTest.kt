package kniezrec.com.flightinfo.route.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.route.RouteDetails
import kniezrec.com.flightinfo.route.RouteEndpoint
import kniezrec.com.flightinfo.route.RoutePickerError
import kniezrec.com.flightinfo.route.RoutePickerState
import kniezrec.com.flightinfo.route.RouteState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Interaction and semantics of the stateless route card and city picker (formerly in GnssStatusScreenTest). */
@RunWith(AndroidJUnit4::class)
class RouteInteractionTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun routePickerSupportsDraftSelectionAndExplicitCancel() {
        val city = NearbyCityRecord(7L, "Berlin", "Germany", 52.5, 13.4, "Europe/Berlin")
        val other = NearbyCityRecord(8L, "Paris", "France", 48.8, 2.3, "Europe/Paris")
        var state by mutableStateOf(RoutePickerState(endpoint = RouteEndpoint.DEPARTURE, results = listOf(city, other)))
        var confirmed = false
        var cancelled = false
        composeRule.setContent {
            RoutePicker(
                state = state,
                mapArchive = null,
                onQueryChange = {},
                onSearch = {},
                onNearest = {},
                onSelect = { state = state.copy(selected = it) },
                onConfirm = { confirmed = true },
                onCancel = { cancelled = true },
                onRetry = {},
            )
        }
        composeRule.onNodeWithText("Confirm").assertIsNotEnabled()
        composeRule.onNodeWithText("Berlin (Germany)").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Confirm").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertTrue(confirmed) }
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle { assertTrue(cancelled) }
    }

    @Test fun routePickerSystemBackCancelsDraftWithoutConfirming() {
        val existing = NearbyCityRecord(6L, "Existing", "US", 40.0, -74.0, "UTC")
        var cancelled = false
        var confirmed = false
        composeRule.setContent {
            RoutePicker(
                state = RoutePickerState(endpoint = RouteEndpoint.DEPARTURE, selected = existing),
                mapArchive = null,
                onQueryChange = {},
                onSearch = {},
                onNearest = {},
                onSelect = {},
                onConfirm = { confirmed = true },
                onCancel = { cancelled = true },
                onRetry = {},
            )
        }
        composeRule.onNodeWithText("Selected: Existing (US)").assertIsDisplayed()
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeRule.runOnIdle {
            assertTrue(cancelled)
            assertTrue(!confirmed)
        }
    }

    @Test fun routePickerImeSearchUsesTheTypedQuery() {
        var state by mutableStateOf(RoutePickerState(endpoint = RouteEndpoint.DESTINATION))
        var searched: String? = null
        composeRule.setContent {
            RoutePicker(
                state = state,
                mapArchive = null,
                onQueryChange = { state = state.copy(query = it) },
                onSearch = { searched = it },
                onNearest = {},
                onSelect = {},
                onConfirm = {},
                onCancel = {},
                onRetry = {},
            )
        }
        composeRule.onNodeWithText("City name").performTextInput("  Berlin  ")
        composeRule.onNodeWithText("City name").performImeAction()
        composeRule.runOnIdle { assertTrue(searched == "  Berlin  ") }
    }

    @Test fun routeCardEllipsizesLongNamesInRtlAtLargeFontScale() {
        val departure = NearbyCityRecord(11L, "Departure city with a deliberately long name", "US", 0.0, 0.0, "UTC")
        val destination = NearbyCityRecord(12L, "Destination city with a deliberately long name", "DE", 0.0, 1.0, "Europe/Berlin")
        composeRule.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(composeRule.density.density, 2f),
            ) {
                RouteCard(
                    state = RouteState(departure = departure, destination = destination),
                    onChoose = {},
                    onClear = {},
                    onClearAll = {},
                )
            }
        }
        // One line each (ellipsized as in the original), the full name kept for accessibility.
        composeRule.onNodeWithText("Departure city with a deliberately long name").assertIsDisplayed()
        composeRule.onNodeWithText("Destination city with a deliberately long name").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Departure, Departure city with a deliberately long name, US").assertHasClickAction()
    }

    @Test fun routePickerNearestNoCityIsReportedWithoutSelectingCity() {
        composeRule.setContent {
            RoutePicker(
                state = RoutePickerState(endpoint = RouteEndpoint.DEPARTURE, error = RoutePickerError.NoCityAtLocation),
                mapArchive = null,
                onQueryChange = {},
                onSearch = {},
                onNearest = {},
                onSelect = {},
                onConfirm = {},
                onCancel = {},
                onRetry = {},
            )
        }
        composeRule.onNodeWithText("No city found at this location.").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm").assertIsNotEnabled()
    }

    @Test fun routePickerNearestCityIsImmediatelyConfirmableDraft() {
        val city = NearbyCityRecord(8L, "Paris", "France", 48.8, 2.3, "Europe/Paris")
        var confirmed = false
        composeRule.setContent {
            RoutePicker(
                // The state of a found nearest city (RoutePickerViewModel.nearest).
                state = RoutePickerState(endpoint = RouteEndpoint.DESTINATION, results = listOf(city), selected = city),
                mapArchive = null,
                onQueryChange = {},
                onSearch = {},
                onNearest = {},
                onSelect = {},
                onConfirm = { confirmed = true },
                onCancel = {},
                onRetry = {},
            )
        }
        composeRule.onNodeWithText("Selected: Paris (France)").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertTrue(confirmed) }
    }

    @Test fun routePickerKeepsMultipleResultsUnselectedUntilExplicitChoiceAndSupportsRetry() {
        val first = NearbyCityRecord(9L, "Springfield", "US", 39.8, -89.6, "UTC")
        val second = first.copy(id = 10L, country = "CA")
        var state by mutableStateOf(
            RoutePickerState(endpoint = RouteEndpoint.DEPARTURE, results = listOf(first, second), error = RoutePickerError.SearchFailed),
        )
        var retried = false
        var confirmed: NearbyCityRecord? = null
        composeRule.setContent {
            RoutePicker(
                state = state,
                mapArchive = null,
                onQueryChange = {},
                onSearch = {},
                onNearest = {},
                onSelect = { state = state.copy(selected = it) },
                onConfirm = { confirmed = state.selected },
                onCancel = {},
                onRetry = { retried = true },
            )
        }
        composeRule.onNodeWithText("Selected: Springfield (US)").assertDoesNotExist()
        composeRule.onNodeWithText("Springfield (US)").performClick()
        composeRule.onNodeWithText("Selected: Springfield (US)").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.runOnIdle { assertTrue(retried) }
        composeRule.onNodeWithText("Confirm").performClick()
        composeRule.runOnIdle { assertTrue(confirmed == first) }
    }

    @Test fun routeCardExposesEndpointAndDetailSemanticsWithLargeTextContent() {
        val departure = NearbyCityRecord(11L, "A very long departure city name", "US", 0.0, 0.0, "UTC")
        val destination = NearbyCityRecord(12L, "A very long destination city name", "DE", 0.0, 1.0, "Europe/Berlin")
        var cleared = false
        composeRule.setContent {
            RouteCard(
                state =
                    RouteState(
                        departure = departure,
                        destination = destination,
                        details =
                            RouteDetails(111.2, null, null, java.time.ZoneId.of("Europe/Berlin"), null),
                    ),
                onChoose = {},
                onClear = {},
                onClearAll = { cleared = true },
            )
        }
        composeRule.onNodeWithContentDescription("Departure, A very long departure city name, US").assertHasClickAction()
        val departureAction =
            composeRule
                .onNodeWithContentDescription(
                    "Departure, A very long departure city name, US",
                ).assertHasClickAction()
        assertTrue(departureAction.fetchSemanticsNode().boundsInRoot.height >= 48f * composeRule.density.density)
        composeRule.onNodeWithContentDescription("Destination, A very long destination city name, DE").assertHasClickAction()
        composeRule.onNodeWithText("Distance").assertIsDisplayed()
        composeRule.onNodeWithText("Waiting for current position").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Clear route").performClick()
        composeRule.runOnIdle { assertTrue(cleared) }
    }
}
