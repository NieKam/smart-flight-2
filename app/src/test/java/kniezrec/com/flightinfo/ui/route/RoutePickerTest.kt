package kniezrec.com.flightinfo.ui.route

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.NearbyCoordinate
import kniezrec.com.flightinfo.nearby.data.CityDataSource
import kniezrec.com.flightinfo.nearby.data.CityRepository
import kniezrec.com.flightinfo.route.RouteEndpoint
import kniezrec.com.flightinfo.route.RoutePickerError
import kniezrec.com.flightinfo.route.RoutePickerState
import kniezrec.com.flightinfo.route.data.RouteRepository
import kniezrec.com.flightinfo.route.ui.RoutePickerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Error mapping and selection of the stateless picker; tall window so the whole picker is on screen. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w411dp-h1000dp")
class RoutePickerTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val paris = NearbyCityRecord(8L, "Paris", "France", 48.8, 2.3, "Europe/Paris")
    private val badZone = NearbyCityRecord(4L, "Bad zone", "D", 0.0, 2.0, "Not/AZone")

    @After fun tearDown() = repositoryScope.cancel()

    @Test fun searchFailureShowsTheLoadErrorWithRetry() {
        var retried = false
        show(open().copy(error = RoutePickerError.SearchFailed), onRetry = { retried = true })

        composeRule.onNodeWithText("City data could not be loaded.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.runOnIdle { assertTrue(retried) }
    }

    @Test fun noCityAtLocationShowsItsMessageWithoutRetry() {
        show(open().copy(error = RoutePickerError.NoCityAtLocation))

        composeRule.onNodeWithText("No city found at this location.").assertIsDisplayed()
        composeRule.onNodeWithText("City data could not be loaded.").assertDoesNotExist()
        composeRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test fun invalidNearestCityShowsItsMessageWithoutRetry() {
        show(open().copy(error = RoutePickerError.InvalidCity))

        composeRule.onNodeWithText("This city record is invalid. Choose another city.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertDoesNotExist()
        composeRule.onNodeWithText("Confirm").assertIsNotEnabled()
    }

    @Test fun noErrorShowsNoErrorMessage() {
        show(open())

        composeRule.onNodeWithText("City data could not be loaded.").assertDoesNotExist()
        composeRule.onNodeWithText("No city found at this location.").assertDoesNotExist()
        composeRule.onNodeWithText("This city record is invalid. Choose another city.").assertDoesNotExist()
    }

    @Test fun invalidSelectionIsShownAndCannotBeConfirmed() {
        show(open().copy(selected = badZone))

        composeRule.onNodeWithText("Selected: Bad zone (D)").assertIsDisplayed()
        composeRule.onNodeWithText("This city record is invalid. Choose another city.").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm").assertIsNotEnabled()
    }

    @Test fun validSelectionIsConfirmableUnlessLoading() {
        show(open().copy(selected = paris, loading = true))

        composeRule.onNodeWithText("Selected: Paris (France)").assertIsDisplayed()
        composeRule.onNodeWithText("Searching offline city data…").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm").assertIsNotEnabled()
        composeRule.onNodeWithText("Search").assertIsNotEnabled()
    }

    @Test fun closedPickerShowsNothing() {
        show(RoutePickerState())

        composeRule.onNodeWithText("Confirm").assertDoesNotExist()
    }

    // TASK-013 bug fix: the nearest city of a map long-press (wired through the real ViewModel)
    // becomes the selection, and Confirm saves it.
    @Test fun nearestCityFromTheViewModelIsSelectedAndConfirmable() {
        val preferences = composeRule.activity.getSharedPreferences("route", Context.MODE_PRIVATE)
        val viewModel =
            RoutePickerViewModel(
                SavedStateHandle(),
                CityRepository(FakeCityDataSource(listOf(paris)), Dispatchers.Unconfined),
                RouteRepository(preferences, repositoryScope),
            )
        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            RoutePicker(
                state = state,
                mapArchive = null,
                onQueryChange = viewModel::updateQuery,
                onSearch = viewModel::search,
                onNearest = viewModel::nearest,
                onSelect = viewModel::select,
                onConfirm = { viewModel.confirm() },
                onCancel = viewModel::close,
                onRetry = viewModel::retry,
            )
        }
        composeRule.runOnIdle {
            viewModel.open(RouteEndpoint.DESTINATION)
            viewModel.nearest(NearbyCoordinate(48.0, 2.0))
        }

        composeRule.onNodeWithText("Selected: Paris (France)").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm").assertIsEnabled().performClick()

        composeRule.onNodeWithText("Confirm").assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(paris.id, preferences.getLong("route_destination_id", Long.MIN_VALUE)) }
    }

    // TASK-013 CI fix: the map's marker update used to write Compose state it had read, so every
    // selection re-ran the update without end (out of memory under Robolectric).
    @Test fun selectingCitiesOnTheOfflineMapSettles() {
        val archive = File(composeRule.activity.cacheDir, "picker-map-test.zip")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("tile.jpg"))
            zip.write(byteArrayOf(0))
            zip.closeEntry()
        }
        val berlin = NearbyCityRecord(7L, "Berlin", "Germany", 52.5, 13.4, "Europe/Berlin")
        var state by mutableStateOf(open().copy(results = listOf(paris, berlin)))
        try {
            composeRule.setContent {
                RoutePicker(
                    state = state,
                    mapArchive = archive,
                    onQueryChange = {},
                    onSearch = {},
                    onNearest = {},
                    onSelect = { state = state.copy(selected = it) },
                    onConfirm = {},
                    onCancel = {},
                    onRetry = {},
                )
            }
            composeRule.onNodeWithText("Paris (France)").performClick()
            composeRule.onNodeWithText("Selected: Paris (France)").assertIsDisplayed()
            composeRule.onNodeWithText("Berlin (Germany)").performClick()
            composeRule.onNodeWithText("Selected: Berlin (Germany)").assertIsDisplayed()
        } finally {
            archive.delete()
        }
    }

    private fun open() = RoutePickerState(endpoint = RouteEndpoint.DEPARTURE)

    private fun show(
        state: RoutePickerState,
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            RoutePicker(
                state = state,
                mapArchive = null,
                onQueryChange = {},
                onSearch = {},
                onNearest = {},
                onSelect = {},
                onConfirm = {},
                onCancel = {},
                onRetry = onRetry,
            )
        }
    }

    private class FakeCityDataSource(
        private val cities: List<NearbyCityRecord>,
    ) : CityDataSource {
        override fun readAll(reextract: Boolean): List<NearbyCityRecord> = cities
    }
}
