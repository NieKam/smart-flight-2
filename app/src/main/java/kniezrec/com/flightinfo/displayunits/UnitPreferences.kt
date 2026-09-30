package kniezrec.com.flightinfo.displayunits

enum class SpeedUnit(
    override val key: String,
) : UnitKey {
    KILOMETRES_PER_HOUR("kmh"),
    MILES_PER_HOUR("mph"),
    KNOTS("kt"),
}

enum class AltitudeUnit(
    override val key: String,
) : UnitKey {
    METRES("m"),
    FEET("ft"),
}

enum class DistanceUnit(
    override val key: String,
) : UnitKey {
    KILOMETRES("km"),
    MILES("mi"),
}

enum class VerticalSpeedUnit(
    override val key: String,
) : UnitKey {
    METRES_PER_SECOND("ms"),
    METRES_PER_MINUTE("mmin"),
    FEET_PER_MINUTE("ftmin"),
}

enum class PressureUnit(
    override val key: String,
) : UnitKey {
    MILLIBAR("mbar"),
    INCHES_OF_MERCURY("inhg"),
}

data class UnitPreferences(
    val speed: SpeedUnit = SpeedUnit.KILOMETRES_PER_HOUR,
    val altitude: AltitudeUnit = AltitudeUnit.METRES,
    val distance: DistanceUnit = DistanceUnit.KILOMETRES,
    val verticalSpeed: VerticalSpeedUnit = VerticalSpeedUnit.METRES_PER_SECOND,
    val pressure: PressureUnit = PressureUnit.MILLIBAR,
)

sealed interface UnitKey {
    val key: String
}
