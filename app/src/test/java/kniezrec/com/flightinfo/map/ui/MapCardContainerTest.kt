package kniezrec.com.flightinfo.map.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.map.data.MapArchiveRepository
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.data.CityRepository
import kniezrec.com.flightinfo.route.data.RouteRepository
import kniezrec.com.flightinfo.route.ui.RouteViewModel
import kniezrec.com.flightinfo.testutil.FakeDisplaySettingsRepository
import kniezrec.com.flightinfo.testutil.FakeLocationDataSource
import kniezrec.com.flightinfo.testutil.ListCityDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Clock
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The container renders its [MapViewModel]'s map with the route of its [RouteViewModel] over it. */
@RunWith(AndroidJUnit4::class)
class MapCardContainerTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val location = FakeLocationDataSource()
    private val warsaw = NearbyCityRecord(1L, "Warsaw", "Poland", 52.2, 21.0, "Europe/Warsaw")
    private val berlin = NearbyCityRecord(2L, "Berlin", "Germany", 52.5, 13.4, "Europe/Berlin")
    private lateinit var directory: File

    @After fun tearDown() {
        repositoryScope.cancel()
        directory.deleteRecursively()
    }

    @Test fun readyMapShowsTheSavedRouteOverlay() {
        directory = File(composeRule.activity.cacheDir, "map-container-test").apply { mkdirs() }
        ZipOutputStream(FileOutputStream(File(directory, "osmdroid.zip"))).use { zip ->
            zip.putNextEntry(ZipEntry("tile.jpg"))
            zip.write(byteArrayOf(0))
            zip.closeEntry()
        }
        val routePreferences =
            composeRule.activity.getSharedPreferences("map_container_test_route", Context.MODE_PRIVATE).also {
                it
                    .edit()
                    .clear()
                    .putLong("route_departure_id", warsaw.id)
                    .putLong("route_destination_id", berlin.id)
                    .commit()
            }
        val locationRepository = LocationRepository(location, repositoryScope)
        val mapViewModel =
            MapViewModel(
                MapArchiveRepository({ throw IOException("the archive is already in place") }, directory, Dispatchers.Unconfined),
                locationRepository,
                FakeDisplaySettingsRepository(),
            )
        val routeViewModel =
            RouteViewModel(
                locationRepository,
                CityRepository(ListCityDataSource(listOf(warsaw, berlin)), Dispatchers.Unconfined),
                RouteRepository(routePreferences, repositoryScope),
                Clock.systemUTC(),
            )
        composeRule.setContent { MapCardContainer(viewModel = mapViewModel, routeViewModel = routeViewModel) }

        composeRule.waitUntil {
            composeRule.onAllNodesWithContentDescription("Route overlay from Warsaw to Berlin").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("map-content").assertExists()
    }

    @Test fun unavailableArchiveOffersARetryWiredToTheViewModel() {
        directory = File(composeRule.activity.cacheDir, "map-container-missing").apply { mkdirs() }
        var copies = 0
        val mapViewModel =
            MapViewModel(
                MapArchiveRepository(
                    {
                        copies++
                        throw IOException("no archive")
                    },
                    directory,
                    Dispatchers.Unconfined,
                ),
                LocationRepository(location, repositoryScope),
                FakeDisplaySettingsRepository(),
            )
        val routeViewModel =
            RouteViewModel(
                LocationRepository(location, repositoryScope),
                CityRepository(ListCityDataSource(emptyList()), Dispatchers.Unconfined),
                RouteRepository(
                    composeRule.activity.getSharedPreferences("map_container_test_empty", Context.MODE_PRIVATE),
                    repositoryScope,
                ),
                Clock.systemUTC(),
            )
        composeRule.setContent { MapCardContainer(viewModel = mapViewModel, routeViewModel = routeViewModel) }
        composeRule.waitUntil { composeRule.onAllNodesWithText("Map unavailable").fetchSemanticsNodes().isNotEmpty() }
        val before = composeRule.runOnIdle { copies }

        composeRule.onNodeWithText("Try again").assertHasClickAction().performClick()

        composeRule.waitUntil { copies > before }
    }
}
