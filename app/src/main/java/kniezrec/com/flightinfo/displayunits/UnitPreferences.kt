package kniezrec.com.flightinfo.displayunits

import android.content.SharedPreferences

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

class UnitPreferencesStore(
    private val preferences: SharedPreferences,
) {
    fun read(): UnitPreferences =
        UnitPreferences(
            speed = preferences.enumOrDefault(KEY_SPEED, SpeedUnit.entries),
            altitude = preferences.enumOrDefault(KEY_ALTITUDE, AltitudeUnit.entries),
            distance = preferences.enumOrDefault(KEY_DISTANCE, DistanceUnit.entries),
            verticalSpeed = preferences.enumOrDefault(KEY_VERTICAL_SPEED, VerticalSpeedUnit.entries),
            pressure = preferences.enumOrDefault(KEY_PRESSURE, PressureUnit.entries),
        )

    fun write(value: UnitPreferences) {
        preferences
            .edit()
            .putString(KEY_SPEED, value.speed.key)
            .putString(KEY_ALTITUDE, value.altitude.key)
            .putString(KEY_DISTANCE, value.distance.key)
            .putString(KEY_VERTICAL_SPEED, value.verticalSpeed.key)
            .putString(KEY_PRESSURE, value.pressure.key)
            .commit()
    }

    private inline fun <reified T : Enum<T>> SharedPreferences.enumOrDefault(
        key: String,
        values: List<T>,
    ): T {
        val stored = getString(key, null)
        return values.firstOrNull { (it as UnitKey).key == stored } ?: values.first()
    }

    private companion object {
        const val KEY_PREFIX = "display_units_"
        const val KEY_SPEED = KEY_PREFIX + "speed"
        const val KEY_ALTITUDE = KEY_PREFIX + "altitude"
        const val KEY_DISTANCE = KEY_PREFIX + "distance"
        const val KEY_VERTICAL_SPEED = KEY_PREFIX + "vertical_speed"
        const val KEY_PRESSURE = KEY_PREFIX + "pressure"
    }
}

interface UnitKey {
    val key: String
}
