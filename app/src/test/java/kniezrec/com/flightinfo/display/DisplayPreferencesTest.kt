package kniezrec.com.flightinfo.display

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayPreferencesTest {
    @Test fun missingValuesUseIndependentDefaults() {
        assertEquals(DisplayPreferences(), DisplayPreferencesStore(FakeSharedPreferences()).read())
    }

    @Test fun malformedValuesUseOnlyTheirOwnDefaults() {
        val preferences =
            FakeSharedPreferences().apply {
                edit()
                    .putString("display_behavior_keep_screen_always_on", "invalid")
                    .putBoolean("display_behavior_portrait_orientation", false)
                    .putString("display_behavior_larger_map_zoom", "invalid")
                    .apply()
            }
        assertEquals(DisplayPreferences(portraitOrientation = false), DisplayPreferencesStore(preferences).read())
    }

    @Test fun valuesRoundTrip() {
        val preferences = FakeSharedPreferences()
        val store = DisplayPreferencesStore(preferences)
        val expected = DisplayPreferences(true, false, true)
        store.write(expected)
        assertEquals(expected, store.read())
    }

    @Test fun effectsApplyBothToggleDirectionsAndAvoidUnnecessaryOrientationRequests() {
        val calls = mutableListOf<String>()
        val applier =
            DisplayPreferencesApplier(
                sink =
                    object : DisplayEffectSink {
                        override fun setKeepScreenAlwaysOn(enabled: Boolean) {
                            calls += "keep:$enabled"
                        }

                        override fun requestOrientation(orientation: Int) {
                            calls += "orientation:$orientation"
                        }
                    },
                portraitOrientation = 1,
                sensorOrientation = 4,
            )

        applier.apply(DisplayPreferences(keepScreenAlwaysOn = true, portraitOrientation = false), currentOrientation = 1)
        assertEquals(listOf("keep:true", "orientation:4"), calls)

        calls.clear()
        applier.apply(DisplayPreferences(), currentOrientation = 1)
        assertEquals(listOf("keep:false"), calls)

        calls.clear()
        applier.apply(DisplayPreferences(portraitOrientation = true), currentOrientation = 4)
        assertEquals(listOf("keep:false", "orientation:1"), calls)

        calls.clear()
        applier.apply(DisplayPreferences(portraitOrientation = false), currentOrientation = 4)
        assertEquals(listOf("keep:false"), calls)
    }

    @Test fun orientationPreferenceIsRereadAfterRecreation() {
        val preferences = FakeSharedPreferences()
        val store = DisplayPreferencesStore(preferences)
        store.write(DisplayPreferences(portraitOrientation = false))

        val recreatedStore = DisplayPreferencesStore(preferences)
        assertEquals(false, recreatedStore.read().portraitOrientation)

        store.write(recreatedStore.read().copy(portraitOrientation = true))
        assertEquals(true, DisplayPreferencesStore(preferences).read().portraitOrientation)
    }
}

private class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(
        key: String?,
        defValue: String?,
    ): String? = values[key] as? String ?: defValue

    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?,
    ): MutableSet<String>? = values[key] as? MutableSet<String> ?: defValues

    override fun getInt(
        key: String?,
        defValue: Int,
    ): Int = values[key] as? Int ?: defValue

    override fun getLong(
        key: String?,
        defValue: Long,
    ): Long = values[key] as? Long ?: defValue

    override fun getFloat(
        key: String?,
        defValue: Float,
    ): Float = values[key] as? Float ?: defValue

    override fun getBoolean(
        key: String?,
        defValue: Boolean,
    ): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : SharedPreferences.Editor {
        override fun putString(
            key: String?,
            value: String?,
        ): SharedPreferences.Editor = apply { if (key != null) values[key] = value }

        override fun putStringSet(
            key: String?,
            valuesSet: MutableSet<String>?,
        ): SharedPreferences.Editor =
            apply {
                if (key !=
                    null
                ) {
                    values[key] = valuesSet
                }
            }

        override fun putInt(
            key: String?,
            value: Int,
        ): SharedPreferences.Editor = apply { if (key != null) values[key] = value }

        override fun putLong(
            key: String?,
            value: Long,
        ): SharedPreferences.Editor = apply { if (key != null) values[key] = value }

        override fun putFloat(
            key: String?,
            value: Float,
        ): SharedPreferences.Editor = apply { if (key != null) values[key] = value }

        override fun putBoolean(
            key: String?,
            value: Boolean,
        ): SharedPreferences.Editor = apply { if (key != null) values[key] = value }

        override fun remove(key: String?): SharedPreferences.Editor = apply { if (key != null) values.remove(key) }

        override fun clear(): SharedPreferences.Editor = apply { values.clear() }

        override fun commit(): Boolean = true

        override fun apply() = Unit
    }
}
