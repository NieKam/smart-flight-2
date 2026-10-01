package kniezrec.com.flightinfo.displayunits.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.displayunits.AltitudeUnit
import kniezrec.com.flightinfo.displayunits.DistanceUnit
import kniezrec.com.flightinfo.displayunits.PressureUnit
import kniezrec.com.flightinfo.displayunits.SpeedUnit
import kniezrec.com.flightinfo.displayunits.UnitPreferences
import kniezrec.com.flightinfo.displayunits.VerticalSpeedUnit
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnitSettingsRepositoryTest {
    private val preferences: SharedPreferences =
        ApplicationProvider
            .getApplicationContext<Context>()
            .getSharedPreferences("display_units", Context.MODE_PRIVATE)

    @Test
    fun missingValuesUseMetricDefaults() =
        runTest {
            assertEquals(
                UnitPreferences(
                    SpeedUnit.KILOMETRES_PER_HOUR,
                    AltitudeUnit.METRES,
                    DistanceUnit.KILOMETRES,
                    VerticalSpeedUnit.METRES_PER_SECOND,
                    PressureUnit.MILLIBAR,
                ),
                repository().units.value,
            )
        }

    @Test
    fun missingAndMalformedValuesUseIndependentMetricDefaults() =
        runTest {
            preferences
                .edit()
                .putString(SPEED, "bad")
                .putString(ALTITUDE, "ft")
                .commit()

            assertEquals(UnitPreferences(altitude = AltitudeUnit.FEET), repository().units.value)
        }

    @Test
    fun setterPublishesAtOnceAndPersistsUnderTheSameKeys() =
        runTest {
            val repository = repository()
            val expected =
                UnitPreferences(
                    SpeedUnit.KNOTS,
                    AltitudeUnit.FEET,
                    DistanceUnit.MILES,
                    VerticalSpeedUnit.FEET_PER_MINUTE,
                    PressureUnit.INCHES_OF_MERCURY,
                )

            repository.setUnits(expected)

            assertEquals(expected, repository.units.value)
            assertEquals("kt", preferences.getString(SPEED, null))
            assertEquals("ft", preferences.getString(ALTITUDE, null))
            assertEquals("mi", preferences.getString("display_units_distance", null))
            assertEquals("ftmin", preferences.getString("display_units_vertical_speed", null))
            assertEquals("inhg", preferences.getString("display_units_pressure", null))
            // A new instance (e.g. after process restart) reads the same values back.
            assertEquals(expected, repository().units.value)
        }

    @Test
    fun changesWrittenElsewhereAreEmitted() =
        runTest {
            val repository = repository()
            val emitted = mutableListOf<UnitPreferences>()
            backgroundScope.launch { repository.units.toList(emitted) }
            runCurrent()

            preferences.edit().putString(SPEED, "mph").commit()
            runCurrent()

            assertEquals(listOf(UnitPreferences(), UnitPreferences(speed = SpeedUnit.MILES_PER_HOUR)), emitted)
        }

    private fun TestScope.repository(): SharedPreferencesUnitSettingsRepository =
        SharedPreferencesUnitSettingsRepository(preferences, backgroundScope).also { runCurrent() }

    private companion object {
        const val SPEED = "display_units_speed"
        const val ALTITUDE = "display_units_altitude"
    }
}
