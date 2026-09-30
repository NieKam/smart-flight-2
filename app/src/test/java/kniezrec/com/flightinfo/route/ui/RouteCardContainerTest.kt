package kniezrec.com.flightinfo.route.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.displayunits.DistanceUnit
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.data.CityRepository
import kniezrec.com.flightinfo.route.RouteEndpoint
import kniezrec.com.flightinfo.route.data.RouteRepository
import kniezrec.com.flightinfo.testutil.FakeLocationDataSource
import kniezrec.com.flightinfo.testutil.ListCityDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock

/** The container renders its [RouteViewModel]'s route and wires choose and clear actions. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w411dp-h1000dp")
class RouteCardContainerTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val warsaw = NearbyCityRecord(1L, "Warsaw", "Poland", 52.2, 21.0, "Europe/Warsaw")
    private val berlin = NearbyCityRecord(2L, "Berlin", "Germany", 52.5, 13.4, "Europe/Berlin")

    @After fun tearDown() = repositoryScope.cancel()

    @Test fun showsTheSavedRouteAndWiresChooseAndClearAll() {
        val preferences = routePreferences()
        preferences
            .edit()
            .putLong("route_departure_id", warsaw.id)
            .putLong("route_destination_id", berlin.id)
            .commit()
        val viewModel =
            RouteViewModel(
                LocationRepository(FakeLocationDataSource(), repositoryScope),
                CityRepository(ListCityDataSource(listOf(warsaw, berlin)), Dispatchers.Unconfined),
                RouteRepository(preferences, repositoryScope),
                Clock.systemUTC(),
            )
        var chosen: RouteEndpoint? = null
        composeRule.setContent {
            RouteCardContainer(DistanceUnit.KILOMETRES, onChoose = { chosen = it }, viewModel = viewModel)
        }
        waitForText("Departure: Warsaw")
        waitForText("Destination: Berlin")

        composeRule.onNodeWithContentDescription("Departure, Warsaw").performClick()
        composeRule.runOnIdle { assertEquals(RouteEndpoint.DEPARTURE, chosen) }

        composeRule.onNodeWithText("Clear route").performClick()
        waitForText("Departure: Choose departure city")
        composeRule.runOnIdle {
            assertFalse(preferences.contains("route_departure_id"))
            assertFalse(preferences.contains("route_destination_id"))
        }
    }

    private fun routePreferences(): SharedPreferences =
        composeRule.activity.getSharedPreferences("route_container_test", Context.MODE_PRIVATE).also {
            it.edit().clear().commit()
        }

    private fun waitForText(text: String) {
        composeRule.waitUntil { composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
