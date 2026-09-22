package kniezrec.com.flightinfo.display

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.robolectric.RuntimeEnvironment

class DisplayPreferencesTest {
    @Before
    fun clearPreferences() {
        preferences.edit().clear().commit()
    }

    private val preferences =
        RuntimeEnvironment
            .getApplication()
            .getSharedPreferences("display-test", Context.MODE_PRIVATE)

    @Test fun missingValuesUseIndependentDefaults() {
        assertEquals(DisplayPreferences(), DisplayPreferencesStore(preferences).read())
    }

    @Test fun malformedValuesUseOnlyTheirOwnDefaults() {
        preferences
            .edit()
            .putString("display_behavior_keep_screen_always_on", "invalid")
            .putBoolean("display_behavior_portrait_orientation", false)
            .putString("display_behavior_larger_map_zoom", "invalid")
            .commit()
        assertEquals(DisplayPreferences(portraitOrientation = false), DisplayPreferencesStore(preferences).read())
    }

    @Test fun valuesRoundTrip() {
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
        val store = DisplayPreferencesStore(preferences)
        store.write(DisplayPreferences(portraitOrientation = false))

        val recreatedStore = DisplayPreferencesStore(preferences)
        assertEquals(false, recreatedStore.read().portraitOrientation)

        store.write(recreatedStore.read().copy(portraitOrientation = true))
        assertEquals(true, DisplayPreferencesStore(preferences).read().portraitOrientation)
    }
}
