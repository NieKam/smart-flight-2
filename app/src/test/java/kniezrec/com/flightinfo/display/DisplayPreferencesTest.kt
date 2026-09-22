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
}
