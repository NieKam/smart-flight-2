package kniezrec.com.flightinfo.nearby.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.displayunits.DistanceUnit
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.data.CityRepository
import kniezrec.com.flightinfo.testutil.FakeLocationDataSource
import kniezrec.com.flightinfo.testutil.ListCityDataSource
import kniezrec.com.flightinfo.testutil.flightFix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

/** The container renders its [NearbyCityViewModel]'s city with distances in the unit it is given. */
@RunWith(AndroidJUnit4::class)
class NearbyCityCardContainerTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val location = FakeLocationDataSource()
    private val warsaw = NearbyCityRecord(1L, "Warsaw", "Poland", 52.2, 21.0, "Europe/Warsaw")

    @After fun tearDown() = repositoryScope.cancel()

    @Test fun showsTheNearestCityAndFollowsTheDistanceUnit() {
        val viewModel =
            NearbyCityViewModel(
                LocationRepository(location, repositoryScope),
                CityRepository(ListCityDataSource(listOf(warsaw)), Dispatchers.Unconfined),
                Clock.systemUTC(),
            )
        var distanceUnit by mutableStateOf(DistanceUnit.KILOMETRES)
        composeRule.setContent { NearbyCityCardContainer(distanceUnit, viewModel = viewModel) }

        // Re-sent until the ViewModel's registration (made when the card starts collecting) receives it.
        composeRule.waitUntil {
            location.emitFix(flightFix(latitude = 52.2, longitude = 21.0))
            composeRule.onAllNodesWithContentDescription("Closest city, Warsaw").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("0.0 km").assertIsDisplayed()

        composeRule.runOnIdle { distanceUnit = DistanceUnit.MILES }
        composeRule.onNodeWithText("0.0 mi").assertIsDisplayed()
        composeRule.onAllNodesWithText("0.0 km").assertCountEquals(0)
    }
}
