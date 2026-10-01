package kniezrec.com.flightinfo.displayunits.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.displayunits.AltitudeUnit
import kniezrec.com.flightinfo.displayunits.DistanceUnit
import kniezrec.com.flightinfo.displayunits.PressureUnit
import kniezrec.com.flightinfo.displayunits.SpeedUnit
import kniezrec.com.flightinfo.displayunits.UnitKey
import kniezrec.com.flightinfo.displayunits.VerticalSpeedUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UnitLabelsTest {
    private val allUnits: List<UnitKey> =
        SpeedUnit.entries + AltitudeUnit.entries + DistanceUnit.entries + VerticalSpeedUnit.entries + PressureUnit.entries

    @Test
    fun everyUnitMapsToExistingNonBlankStrings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        allUnits.forEach { unit ->
            val labels = unit.labels
            listOf(labels.symbol, labels.longName, labels.accessibility).forEach { resource ->
                assertTrue("Blank label for $unit", context.getString(resource).isNotBlank())
            }
        }
    }

    @Test
    fun unitsOfOneQuantityHaveDistinctLabels() {
        listOf(SpeedUnit.entries, AltitudeUnit.entries, DistanceUnit.entries, VerticalSpeedUnit.entries, PressureUnit.entries)
            .forEach { units ->
                val labels = units.map { it.labels }
                assertEquals(units.size, labels.map { it.symbol }.distinct().size)
                assertEquals(units.size, labels.map { it.longName }.distinct().size)
                assertEquals(units.size, labels.map { it.accessibility }.distinct().size)
            }
    }

    /** Pins the mapping the cards and settings used before it was shared. */
    @Test
    fun mappingMatchesTheUnitStrings() {
        val expected =
            mapOf<UnitKey, UnitLabels>(
                SpeedUnit.KILOMETRES_PER_HOUR to UnitLabels(R.string.unit_kmh, R.string.option_kmh, R.string.unit_kmh_accessibility),
                SpeedUnit.MILES_PER_HOUR to UnitLabels(R.string.unit_mph, R.string.option_mph, R.string.unit_mph_accessibility),
                SpeedUnit.KNOTS to UnitLabels(R.string.unit_kt, R.string.option_kt, R.string.unit_kt_accessibility),
                AltitudeUnit.METRES to UnitLabels(R.string.unit_m, R.string.option_m, R.string.unit_m_accessibility),
                AltitudeUnit.FEET to UnitLabels(R.string.unit_ft, R.string.option_ft, R.string.unit_ft_accessibility),
                DistanceUnit.KILOMETRES to UnitLabels(R.string.unit_km, R.string.option_km, R.string.unit_km_accessibility),
                DistanceUnit.MILES to UnitLabels(R.string.unit_mi, R.string.option_mi, R.string.unit_mi_accessibility),
                VerticalSpeedUnit.METRES_PER_SECOND to UnitLabels(R.string.unit_ms, R.string.option_ms, R.string.unit_ms_accessibility),
                VerticalSpeedUnit.METRES_PER_MINUTE to
                    UnitLabels(R.string.unit_mmin, R.string.option_mmin, R.string.unit_mmin_accessibility),
                VerticalSpeedUnit.FEET_PER_MINUTE to
                    UnitLabels(R.string.unit_ftmin, R.string.option_ftmin, R.string.unit_ftmin_accessibility),
                PressureUnit.MILLIBAR to UnitLabels(R.string.unit_mbar, R.string.option_mbar, R.string.unit_mbar_accessibility),
                PressureUnit.INCHES_OF_MERCURY to
                    UnitLabels(R.string.unit_inhg, R.string.option_inhg, R.string.unit_inhg_accessibility),
            )

        assertEquals(allUnits.toSet(), expected.keys)
        allUnits.forEach { unit -> assertEquals("Labels of $unit", expected.getValue(unit), unit.labels) }
    }
}
