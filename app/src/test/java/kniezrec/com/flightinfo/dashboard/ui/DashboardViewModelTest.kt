package kniezrec.com.flightinfo.dashboard.ui

import kniezrec.com.flightinfo.displayunits.DistanceUnit
import kniezrec.com.flightinfo.displayunits.SpeedUnit
import kniezrec.com.flightinfo.displayunits.UnitPreferences
import kniezrec.com.flightinfo.location.data.LocationRepository
import kniezrec.com.flightinfo.testutil.FakeLocationDataSource
import kniezrec.com.flightinfo.testutil.FakeUnitSettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardViewModelTest {
    @Test fun `units follow the unit settings at once`() =
        runTest {
            val repository = FakeUnitSettingsRepository(UnitPreferences(distance = DistanceUnit.MILES))
            val viewModel = DashboardViewModel(repository, LocationRepository(FakeLocationDataSource(), backgroundScope))
            assertEquals(UnitPreferences(distance = DistanceUnit.MILES), viewModel.units.value)

            repository.setUnits(UnitPreferences(speed = SpeedUnit.MILES_PER_HOUR))

            assertEquals(UnitPreferences(speed = SpeedUnit.MILES_PER_HOUR), viewModel.units.value)
        }

    @Test fun `location check reads the switch now`() =
        runTest {
            val location = FakeLocationDataSource(locationEnabled = false)
            val viewModel = DashboardViewModel(FakeUnitSettingsRepository(UnitPreferences()), LocationRepository(location, backgroundScope))
            assertFalse(viewModel.isLocationEnabled())

            location.locationEnabled = true

            assertTrue(viewModel.isLocationEnabled())
        }
}
