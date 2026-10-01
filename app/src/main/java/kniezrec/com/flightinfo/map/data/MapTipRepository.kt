package kniezrec.com.flightinfo.map.data

import android.content.SharedPreferences
import kniezrec.com.flightinfo.map.data.di.MapTipPreferences
import javax.inject.Inject
import javax.inject.Singleton

/** How often the map's one-off tips were shown (the max-zoom tip is shown a limited number of times). */
interface MapTipRepository {
    /** Times the max-zoom tip was shown; 0 when nothing (or a non-integer value) is stored. */
    suspend fun zoomTipShownCount(): Int

    /** Counts one more max-zoom tip, persistently; [zoomTipShownCount] includes it when this returns. */
    suspend fun recordZoomTipShown()
}

/**
 * [MapTipRepository] over the `map_tips` file. The count starts at 0 for everyone: the original
 * app's `tip_shown_count` is not imported (human decision, no upgrade path from the original app).
 */
@Singleton
class SharedPreferencesMapTipRepository
    @Inject
    constructor(
        @MapTipPreferences private val preferences: SharedPreferences,
    ) : MapTipRepository {
        override suspend fun zoomTipShownCount(): Int = (preferences.all[KEY_ZOOM_TIP_SHOWN_COUNT] as? Int)?.coerceAtLeast(0) ?: 0

        override suspend fun recordZoomTipShown() {
            // apply() updates the in-memory values at once.
            preferences.edit().putInt(KEY_ZOOM_TIP_SHOWN_COUNT, zoomTipShownCount() + 1).apply()
        }

        private companion object {
            const val KEY_ZOOM_TIP_SHOWN_COUNT = "map_zoom_tip_shown_count"
        }
    }
