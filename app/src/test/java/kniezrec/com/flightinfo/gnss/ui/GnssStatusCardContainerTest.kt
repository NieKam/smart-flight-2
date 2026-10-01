package kniezrec.com.flightinfo.gnss.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.gnss.GnssSatellite
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.testutil.FakeLocationDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The container renders the state of its [GnssStatusViewModel] (built over a fake location source). */
@RunWith(AndroidJUnit4::class)
class GnssStatusCardContainerTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val location = FakeLocationDataSource()

    @After fun tearDown() = repositoryScope.cancel()

    @Test fun showsWaitingThenTheReportedSatellites() {
        val viewModel = GnssStatusViewModel(LocationRepository(location, repositoryScope))
        composeRule.setContent {
            GnssStatusCardContainer(onOpenLocationSettings = {}, viewModel = viewModel)
        }
        composeRule.onNodeWithText("Waiting for GPS signal…").assertIsDisplayed()

        // Re-sent until the ViewModel's registration (made when the card starts collecting) receives it.
        composeRule.waitUntil {
            location.emitSatellites(listOf(GnssSatellite(true), GnssSatellite(false)))
            composeRule.onAllNodesWithText("Using 1 satellite").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Using 1 satellite").assertIsDisplayed()
    }
}
