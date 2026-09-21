package kniezrec.com.flightinfo.displayunits

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

fun convertSpeed(value: Double, unit: SpeedUnit): Double? = value.convert { when (unit) {
    SpeedUnit.KILOMETRES_PER_HOUR -> it
    SpeedUnit.MILES_PER_HOUR -> it * 0.621371
    SpeedUnit.KNOTS -> it / 1.852
} }

fun convertAltitude(value: Double, unit: AltitudeUnit): Double? = value.convert { if (unit == AltitudeUnit.FEET) it * 3.28084 else it }
fun convertDistance(value: Double, unit: DistanceUnit): Double? = value.convert { if (unit == DistanceUnit.MILES) it * 0.621371 else it }
fun convertVerticalSpeed(value: Double, unit: VerticalSpeedUnit): Double? = value.convert { when (unit) {
    VerticalSpeedUnit.METRES_PER_SECOND -> it
    VerticalSpeedUnit.METRES_PER_MINUTE -> it * 60.0
    VerticalSpeedUnit.FEET_PER_MINUTE -> it * 196.850394
} }
fun convertPressure(value: Double, unit: PressureUnit): Double? = value.convert { if (unit == PressureUnit.INCHES_OF_MERCURY) it * 0.02953 else it }

fun formatUnitNumber(value: Double?, signed: Boolean = false, locale: Locale = Locale.getDefault()): String? {
    if (value == null || !value.isFinite()) return null
    val number = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }.format(abs(value))
    return if (signed) {
        if (value < 0) "−$number" else "+$number"
    } else number
}

private inline fun Double.convert(converter: (Double) -> Double): Double? =
    takeIf { it.isFinite() }?.let(converter)?.takeIf { it.isFinite() }
