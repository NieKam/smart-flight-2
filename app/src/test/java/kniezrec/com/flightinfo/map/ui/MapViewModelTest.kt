package kniezrec.com.flightinfo.map.ui

import kniezrec.com.flightinfo.display.DisplayPreferences
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.map.MapCoordinate
import kniezrec.com.flightinfo.map.data.MapArchiveRepository
import kniezrec.com.flightinfo.testutil.FakeDisplaySettingsRepository
import kniezrec.com.flightinfo.testutil.FakeLocationDataSource
import kniezrec.com.flightinfo.testutil.flightFix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val location = FakeLocationDataSource()
    private val display = FakeDisplaySettingsRepository()
    private val directory: File = Files.createTempDirectory("map-view-model").toFile()
    private val archive = File(directory, "osmdroid.zip")

    /** Number of asset copies that fail before one succeeds. */
    private var assetFailuresLeft = 0
    private var assetOpens = 0

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() {
        Dispatchers.resetMain()
        directory.deleteRecursively()
    }

    @Test fun `loading is followed by the ready archive without a position`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val states = subscribeRecording(viewModel)

            assertEquals(listOf(MapUiState.Loading, MapUiState.Ready(archive)), states)
        }

    @Test fun `a failed preparation is unavailable and retry prepares again`() =
        runTest(dispatcher) {
            assetFailuresLeft = 1
            val viewModel = viewModel()
            subscribe(viewModel)
            assertEquals(MapUiState.Unavailable, viewModel.state.value)

            viewModel.retry()
            runCurrent()

            assertEquals(MapUiState.Ready(archive), viewModel.state.value)
            assertEquals(2, assetOpens)
        }

    @Test fun `a map that cannot open the archive is unavailable until retry`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)
            fix(latitude = 1.0, longitude = 2.0)

            viewModel.onMapOpenFailed()
            runCurrent()
            assertEquals(MapUiState.Unavailable, viewModel.state.value)
            assertEquals("fixes are released", 0, location.fixRegistrations.activeCount)

            viewModel.retry()
            runCurrent()
            assertEquals(MapUiState.Ready(archive), viewModel.state.value)
        }

    @Test fun `the first fix is centered once per observation`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)

            fix(latitude = 1.0, longitude = 2.0)
            assertEquals(MapCoordinate(1.0, 2.0), ready(viewModel).centerRequest)
            assertEquals(MapCoordinate(1.0, 2.0), ready(viewModel).position)

            viewModel.onCentered()
            runCurrent()
            assertNull(ready(viewModel).centerRequest)

            fix(latitude = 3.0, longitude = 4.0)
            assertEquals(MapCoordinate(3.0, 4.0), ready(viewModel).position)
            assertNull(ready(viewModel).centerRequest)

            viewModel.retry()
            runCurrent()
            assertEquals(MapUiState.Ready(archive), viewModel.state.value)
            fix(latitude = 5.0, longitude = 6.0)
            assertEquals(MapCoordinate(5.0, 6.0), ready(viewModel).centerRequest)
        }

    @Test fun `an unacknowledged center request stays on the first fix`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)

            fix(latitude = 1.0, longitude = 2.0)
            fix(latitude = 3.0, longitude = 4.0)

            assertEquals(MapCoordinate(1.0, 2.0), ready(viewModel).centerRequest)
            assertEquals(MapCoordinate(3.0, 4.0), ready(viewModel).position)
        }

    @Test fun `the marker course follows the bearing and is 0 without one`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)

            fix(latitude = 1.0, longitude = 2.0, bearing = -90.0)
            assertEquals(270f, ready(viewModel).markerCourseDegrees)

            fix(latitude = 1.0, longitude = 2.1)
            assertEquals(0f, ready(viewModel).markerCourseDegrees)
        }

    @Test fun `fixes without a valid position change nothing`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)
            fix(latitude = 1.0, longitude = 2.0, bearing = 45.0)

            fix(latitude = Double.NaN, longitude = 2.0, bearing = 90.0)
            fix(latitude = null, longitude = null)

            assertEquals(MapCoordinate(1.0, 2.0), ready(viewModel).position)
            assertEquals(45f, ready(viewModel).markerCourseDegrees)
        }

    @Test fun `the larger map zoom setting is part of the ready state`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)
            assertEquals(false, ready(viewModel).largerMapZoom)

            display.set(DisplayPreferences(largerMapZoom = true))
            runCurrent()

            assertEquals(true, ready(viewModel).largerMapZoom)
        }

    @Test fun `nothing is prepared or observed before the state is collected`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.retry()
            runCurrent()

            assertEquals(MapUiState.Loading, viewModel.state.value)
            assertEquals(0, assetOpens)
            assertEquals(0, location.fixRegistrations.registerCount)
        }

    @Test fun `collecting again within the stop timeout keeps the position`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val first = subscribe(viewModel)
            fix(latitude = 1.0, longitude = 2.0)

            first.cancel()
            advanceTimeBy(MapViewModel.STOP_TIMEOUT_MILLIS - 1_000)
            subscribe(viewModel)

            assertEquals(MapCoordinate(1.0, 2.0), ready(viewModel).position)
            assertEquals(1, location.fixRegistrations.registerCount)
        }

    @Test fun `observation restarted after the stop timeout loads again and centers the next first fix`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val first = subscribe(viewModel)
            fix(latitude = 1.0, longitude = 2.0)
            viewModel.onCentered()
            runCurrent()

            first.cancel()
            advanceTimeBy(MapViewModel.STOP_TIMEOUT_MILLIS + 1)
            runCurrent()
            assertEquals(0, location.fixRegistrations.activeCount)
            val states = subscribeRecording(viewModel)

            assertEquals(listOf(MapUiState.Loading, MapUiState.Ready(archive)), states.drop(1))
            fix(latitude = 3.0, longitude = 4.0)
            assertEquals(MapCoordinate(3.0, 4.0), ready(viewModel).centerRequest)
        }

    @Test fun `location switched off keeps the position and fixes count again once it is back on`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)
            fix(latitude = 1.0, longitude = 2.0)

            location.switchLocation(false)
            runCurrent()
            assertEquals(MapCoordinate(1.0, 2.0), ready(viewModel).position)
            assertEquals(0, location.fixRegistrations.activeCount)

            location.switchLocation(true)
            runCurrent()
            fix(latitude = 3.0, longitude = 4.0)
            assertEquals(MapCoordinate(3.0, 4.0), ready(viewModel).position)
        }

    @Test fun `a failed GPS registration keeps the map ready without a position`() =
        runTest(dispatcher) {
            location.failFixRegistration = true
            val viewModel = viewModel()
            subscribe(viewModel)

            assertEquals(MapUiState.Ready(archive), viewModel.state.value)
        }

    private fun TestScope.viewModel() =
        MapViewModel(
            MapArchiveRepository(::openAsset, directory, dispatcher),
            LocationRepository(location, backgroundScope),
            display,
        )

    private fun openAsset(): InputStream {
        assetOpens++
        if (assetFailuresLeft > 0) {
            assetFailuresLeft--
            return ByteArrayInputStream("not a zip archive".toByteArray())
        }
        return ByteArrayInputStream(validArchive())
    }

    private fun TestScope.subscribe(viewModel: MapViewModel): Job =
        backgroundScope.launch { viewModel.state.collect {} }.also { runCurrent() }

    private fun TestScope.subscribeRecording(viewModel: MapViewModel): List<MapUiState> {
        val states = mutableListOf<MapUiState>()
        backgroundScope.launch { viewModel.state.collect { states += it } }
        runCurrent()
        return states
    }

    private fun TestScope.fix(
        latitude: Double?,
        longitude: Double?,
        bearing: Double? = null,
    ) {
        location.emitFix(flightFix(latitude = latitude, longitude = longitude, bearingDegrees = bearing))
        runCurrent()
    }

    private fun ready(viewModel: MapViewModel): MapUiState.Ready =
        viewModel.state.value as? MapUiState.Ready ?: throw AssertionError("Not ready: ${viewModel.state.value}")

    private fun validArchive(): ByteArray =
        ByteArrayOutputStream()
            .also { bytes ->
                ZipOutputStream(bytes).use { zip ->
                    zip.putNextEntry(ZipEntry("tile.jpg"))
                    zip.write(byteArrayOf(0))
                    zip.closeEntry()
                }
            }.toByteArray()
}
