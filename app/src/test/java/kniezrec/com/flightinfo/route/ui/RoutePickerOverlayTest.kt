package kniezrec.com.flightinfo.route.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.map.data.MapArchiveRepository
import kniezrec.com.flightinfo.map.ui.MapViewModel
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.data.CityRepository
import kniezrec.com.flightinfo.route.RouteEndpoint
import kniezrec.com.flightinfo.route.data.RouteRepository
import kniezrec.com.flightinfo.testutil.FakeDisplaySettingsRepository
import kniezrec.com.flightinfo.testutil.FakeLocationDataSource
import kniezrec.com.flightinfo.testutil.FakeMapTipRepository
import kniezrec.com.flightinfo.testutil.FakeOrientationDataSource
import kniezrec.com.flightinfo.testutil.ListCityDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/**
 * The overlay shows the picker of its [RoutePickerViewModel] only while it is open; typing,
 * searching, selecting and confirming go to the ViewModel. The map ViewModel has no archive here,
 * so the picker shows its map-less layout.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w411dp-h1000dp")
class RoutePickerOverlayTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val berlin = NearbyCityRecord(2L, "Berlin", "Germany", 52.5, 13.4, "Europe/Berlin")

    @After fun tearDown() = repositoryScope.cancel()

    @Test fun opensForAnEndpointAndConfirmSavesTheSelectedCity() {
        val preferences =
            composeRule.activity.getSharedPreferences("picker_overlay_test_route", Context.MODE_PRIVATE).also {
                it.edit().clear().commit()
            }
        val cities = CityRepository(ListCityDataSource(listOf(berlin)), Dispatchers.Unconfined)
        val viewModel = RoutePickerViewModel(SavedStateHandle(), cities, RouteRepository(preferences, repositoryScope))
        val mapViewModel =
            MapViewModel(
                MapArchiveRepository(
                    { throw IOException("no archive") },
                    File(composeRule.activity.cacheDir, "picker-overlay-test"),
                    Dispatchers.Unconfined,
                ),
                LocationRepository(FakeLocationDataSource(), repositoryScope),
                FakeDisplaySettingsRepository(),
                FakeOrientationDataSource(),
                FakeMapTipRepository(),
            )
        composeRule.setContent { RoutePickerOverlay(viewModel = viewModel, mapViewModel = mapViewModel) }
        composeRule.onAllNodesWithText("Choose destination").assertCountEquals(0)

        composeRule.runOnIdle { viewModel.open(RouteEndpoint.DESTINATION) }
        composeRule.onNodeWithText("Choose destination").assertIsDisplayed()
        composeRule.onNodeWithText("City name").performTextInput("Berl")
        composeRule.onNodeWithText("City name").performImeAction()
        // The single result is selected at once (TASK-032).
        composeRule.waitUntil { composeRule.onAllNodesWithText("Selected: Berlin (Germany)").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("Confirm").assertIsEnabled().performClick()

        composeRule.waitUntil { composeRule.onAllNodesWithText("Choose destination").fetchSemanticsNodes().isEmpty() }
        composeRule.runOnIdle { assertEquals(berlin.id, preferences.getLong("route_destination_id", Long.MIN_VALUE)) }
    }
}
