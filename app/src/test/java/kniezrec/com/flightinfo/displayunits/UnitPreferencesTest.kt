package kniezrec.com.flightinfo.displayunits

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitPreferencesTest {
    @Test fun missingAndMalformedValuesUseIndependentMetricDefaults() {
        val preferences =
            MemoryPreferences().apply {
                edit().putString("display_units_speed", "bad").putString("display_units_altitude", "ft").commit()
            }
        val result = UnitPreferencesStore(preferences).read()
        assertEquals(SpeedUnit.KILOMETRES_PER_HOUR, result.speed)
        assertEquals(AltitudeUnit.FEET, result.altitude)
        assertEquals(DistanceUnit.KILOMETRES, result.distance)
        assertEquals(VerticalSpeedUnit.METRES_PER_SECOND, result.verticalSpeed)
        assertEquals(PressureUnit.MILLIBAR, result.pressure)
    }

    @Test fun writesAllSelectionsAndASecondReadRestoresThem() {
        val preferences = MemoryPreferences()
        val expected =
            UnitPreferences(
                SpeedUnit.KNOTS,
                AltitudeUnit.FEET,
                DistanceUnit.MILES,
                VerticalSpeedUnit.FEET_PER_MINUTE,
                PressureUnit.INCHES_OF_MERCURY,
            )
        UnitPreferencesStore(preferences).write(expected)
        assertEquals(expected, UnitPreferencesStore(preferences).read())
    }

    private class MemoryPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()

        override fun getString(
            key: String,
            defValue: String?,
        ): String? = values[key] as? String ?: defValue

        override fun getStringSet(
            key: String,
            defValues: MutableSet<String>?,
        ): MutableSet<String>? = (values[key] as? Set<String>)?.toMutableSet() ?: defValues

        override fun getInt(
            key: String,
            defValue: Int,
        ): Int = values[key] as? Int ?: defValue

        override fun getLong(
            key: String,
            defValue: Long,
        ): Long = values[key] as? Long ?: defValue

        override fun getFloat(
            key: String,
            defValue: Float,
        ): Float = values[key] as? Float ?: defValue

        override fun getBoolean(
            key: String,
            defValue: Boolean,
        ): Boolean = values[key] as? Boolean ?: defValue

        override fun contains(key: String): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

        private inner class Editor : SharedPreferences.Editor {
            override fun putString(
                key: String,
                value: String?,
            ): SharedPreferences.Editor = put(key, value)

            override fun putStringSet(
                key: String,
                value: MutableSet<String>?,
            ): SharedPreferences.Editor = put(key, value)

            override fun putInt(
                key: String,
                value: Int,
            ): SharedPreferences.Editor = put(key, value)

            override fun putLong(
                key: String,
                value: Long,
            ): SharedPreferences.Editor = put(key, value)

            override fun putFloat(
                key: String,
                value: Float,
            ): SharedPreferences.Editor = put(key, value)

            override fun putBoolean(
                key: String,
                value: Boolean,
            ): SharedPreferences.Editor = put(key, value)

            override fun remove(key: String): SharedPreferences.Editor {
                values.remove(key)
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                values.clear()
                return this
            }

            override fun commit(): Boolean = true

            override fun apply() = Unit

            private fun put(
                key: String,
                value: Any?,
            ): SharedPreferences.Editor {
                if (value ==
                    null
                ) {
                    values.remove(key)
                } else {
                    values[key] = value
                }
                return this
            }
        }
    }
}
