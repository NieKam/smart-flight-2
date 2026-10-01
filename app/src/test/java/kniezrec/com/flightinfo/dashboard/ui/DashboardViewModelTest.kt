package kniezrec.com.flightinfo.dashboard.ui

import kniezrec.com.flightinfo.dashboard.HideableCard
import kniezrec.com.flightinfo.displayunits.DistanceUnit
import kniezrec.com.flightinfo.displayunits.SpeedUnit
import kniezrec.com.flightinfo.displayunits.UnitPreferences
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.testutil.FakeCardVisibilityRepository
import kniezrec.com.flightinfo.testutil.FakeLocationDataSource
import kniezrec.com.flightinfo.testutil.FakeUnitSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    @Test fun `units follow the unit settings at once`() =
        runTest {
            val repository = FakeUnitSettingsRepository(UnitPreferences(distance = DistanceUnit.MILES))
            val viewModel =
                DashboardViewModel(
                    repository,
                    LocationRepository(FakeLocationDataSource(), backgroundScope),
                    FakeCardVisibilityRepository(),
                )
            assertEquals(UnitPreferences(distance = DistanceUnit.MILES), viewModel.units.value)

            repository.setUnits(UnitPreferences(speed = SpeedUnit.MILES_PER_HOUR))

            assertEquals(UnitPreferences(speed = SpeedUnit.MILES_PER_HOUR), viewModel.units.value)
        }

    @Test fun `location check reads the switch now`() =
        runTest {
            val location = FakeLocationDataSource(locationEnabled = false)
            val viewModel =
                DashboardViewModel(
                    FakeUnitSettingsRepository(UnitPreferences()),
                    LocationRepository(location, backgroundScope),
                    FakeCardVisibilityRepository(),
                )
            assertFalse(viewModel.isLocationEnabled())

            location.locationEnabled = true

            assertTrue(viewModel.isLocationEnabled())
        }

    @Test fun `hide persists the card and lists it as hidden`() =
        runTest {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            try {
                val cards = FakeCardVisibilityRepository()
                val viewModel =
                    DashboardViewModel(
                        FakeUnitSettingsRepository(UnitPreferences()),
                        LocationRepository(FakeLocationDataSource(), backgroundScope),
                        cards,
                    )
                assertEquals(emptySet<HideableCard>(), viewModel.hiddenCards.value)

                viewModel.hide(HideableCard.Horizon)

                assertEquals(setOf(HideableCard.Horizon), cards.hiddenCards.value)
                assertEquals(setOf(HideableCard.Horizon), viewModel.hiddenCards.value)
            } finally {
                Dispatchers.resetMain()
            }
        }
}
