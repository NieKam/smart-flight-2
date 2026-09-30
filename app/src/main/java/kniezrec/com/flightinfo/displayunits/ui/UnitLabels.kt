package kniezrec.com.flightinfo.displayunits.ui

import androidx.annotation.StringRes
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.displayunits.AltitudeUnit
import kniezrec.com.flightinfo.displayunits.DistanceUnit
import kniezrec.com.flightinfo.displayunits.PressureUnit
import kniezrec.com.flightinfo.displayunits.SpeedUnit
import kniezrec.com.flightinfo.displayunits.UnitKey
import kniezrec.com.flightinfo.displayunits.VerticalSpeedUnit

/**
 * The string resources that name a display unit.
 *
 * @property symbol short symbol shown next to values, e.g. "km/h".
 * @property longName full name with symbol, shown in the unit choice dialog, e.g. "Kilometres per hour (km/h)".
 * @property accessibility spoken name used in accessibility descriptions, e.g. "kilometres per hour".
 */
data class UnitLabels(
    @param:StringRes val symbol: Int,
    @param:StringRes val longName: Int,
    @param:StringRes val accessibility: Int,
)

/** The single unit → string-resource mapping used by every card and the settings screen. */
val UnitKey.labels: UnitLabels
    get() =
        when (this) {
            is SpeedUnit -> speedLabels(this)
            is AltitudeUnit -> altitudeLabels(this)
            is DistanceUnit -> distanceLabels(this)
            is VerticalSpeedUnit -> verticalSpeedLabels(this)
            is PressureUnit -> pressureLabels(this)
        }

private fun speedLabels(unit: SpeedUnit): UnitLabels =
    when (unit) {
        SpeedUnit.KILOMETRES_PER_HOUR -> UnitLabels(R.string.unit_kmh, R.string.option_kmh, R.string.unit_kmh_accessibility)
        SpeedUnit.MILES_PER_HOUR -> UnitLabels(R.string.unit_mph, R.string.option_mph, R.string.unit_mph_accessibility)
        SpeedUnit.KNOTS -> UnitLabels(R.string.unit_kt, R.string.option_kt, R.string.unit_kt_accessibility)
    }

private fun altitudeLabels(unit: AltitudeUnit): UnitLabels =
    when (unit) {
        AltitudeUnit.METRES -> UnitLabels(R.string.unit_m, R.string.option_m, R.string.unit_m_accessibility)
        AltitudeUnit.FEET -> UnitLabels(R.string.unit_ft, R.string.option_ft, R.string.unit_ft_accessibility)
    }

private fun distanceLabels(unit: DistanceUnit): UnitLabels =
    when (unit) {
        DistanceUnit.KILOMETRES -> UnitLabels(R.string.unit_km, R.string.option_km, R.string.unit_km_accessibility)
        DistanceUnit.MILES -> UnitLabels(R.string.unit_mi, R.string.option_mi, R.string.unit_mi_accessibility)
    }

private fun verticalSpeedLabels(unit: VerticalSpeedUnit): UnitLabels =
    when (unit) {
        VerticalSpeedUnit.METRES_PER_SECOND -> UnitLabels(R.string.unit_ms, R.string.option_ms, R.string.unit_ms_accessibility)
        VerticalSpeedUnit.METRES_PER_MINUTE -> UnitLabels(R.string.unit_mmin, R.string.option_mmin, R.string.unit_mmin_accessibility)
        VerticalSpeedUnit.FEET_PER_MINUTE -> UnitLabels(R.string.unit_ftmin, R.string.option_ftmin, R.string.unit_ftmin_accessibility)
    }

private fun pressureLabels(unit: PressureUnit): UnitLabels =
    when (unit) {
        PressureUnit.MILLIBAR -> UnitLabels(R.string.unit_mbar, R.string.option_mbar, R.string.unit_mbar_accessibility)
        PressureUnit.INCHES_OF_MERCURY -> UnitLabels(R.string.unit_inhg, R.string.option_inhg, R.string.unit_inhg_accessibility)
    }
