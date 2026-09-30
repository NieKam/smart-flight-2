package kniezrec.com.flightinfo.flight.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.displayunits.SpeedUnit
import kniezrec.com.flightinfo.displayunits.UnitPreferences
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.testutil.FakeLocationDataSource
import kniezrec.com.flightinfo.testutil.FakePressureDataSource
import kniezrec.com.flightinfo.testutil.flightFix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The container renders its [FlightParametersViewModel]'s readings in the units it is given. */
@RunWith(AndroidJUnit4::class)
class FlightParametersCardContainerTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val location = FakeLocationDataSource()

    @After fun tearDown() = repositoryScope.cancel()

    @Test fun showsTheFixReadingsInTheGivenUnits() {
        val viewModel = FlightParametersViewModel(LocationRepository(location, repositoryScope), FakePressureDataSource(hasSensor = false))
        var units by mutableStateOf(UnitPreferences())
        composeRule.setContent { FlightParametersCardContainer(units, viewModel = viewModel) }
        composeRule.onNodeWithText("Waiting for GPS position…").assertIsDisplayed()

        // Re-sent until the ViewModel's registration (made when the card starts collecting) receives it.
        composeRule.waitUntil {
            location.emitFix(flightFix(speedMetresPerSecond = 10.0))
            composeRule.onAllNodesWithText("36.0 km/h").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.runOnIdle { units = UnitPreferences(speed = SpeedUnit.MILES_PER_HOUR) }
        composeRule.onNodeWithText("22.4 mph").assertIsDisplayed()
    }
}
