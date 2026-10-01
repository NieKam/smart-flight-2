package kniezrec.com.flightinfo.map.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapTipRepositoryTest {
    private val preferences: SharedPreferences =
        ApplicationProvider
            .getApplicationContext<Context>()
            .getSharedPreferences("map_tips", Context.MODE_PRIVATE)

    @Test
    fun theCountStartsAtZero() =
        runTest {
            assertEquals(0, SharedPreferencesMapTipRepository(preferences).zoomTipShownCount())
        }

    @Test
    fun recordingIncrementsAndPersistsTheCount() =
        runTest {
            val repository = SharedPreferencesMapTipRepository(preferences)

            repository.recordZoomTipShown()
            assertEquals(1, repository.zoomTipShownCount())
            repository.recordZoomTipShown()

            assertEquals(2, repository.zoomTipShownCount())
            assertEquals(2, preferences.getInt(KEY, -1))
            // A new instance (e.g. after the next launch) reads the same count.
            assertEquals(2, SharedPreferencesMapTipRepository(preferences).zoomTipShownCount())
        }

    @Test
    fun aNonIntegerOrNegativeStoredValueCountsAsZero() =
        runTest {
            preferences.edit().putString(KEY, "4").commit()
            assertEquals(0, SharedPreferencesMapTipRepository(preferences).zoomTipShownCount())

            preferences.edit().putInt(KEY, -3).commit()
            val repository = SharedPreferencesMapTipRepository(preferences)
            assertEquals(0, repository.zoomTipShownCount())
            repository.recordZoomTipShown()
            assertEquals(1, repository.zoomTipShownCount())
        }

    private companion object {
        const val KEY = "map_zoom_tip_shown_count"
    }
}
