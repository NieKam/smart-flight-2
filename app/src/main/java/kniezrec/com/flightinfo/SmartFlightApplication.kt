package kniezrec.com.flightinfo

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import org.osmdroid.config.Configuration

/** Hilt root. Must stay free of heavy work: Robolectric creates it for every JVM test. */
@HiltAndroidApp
class SmartFlightApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // osmdroid's process-wide settings (user agent, cache paths), read once before any map.
        Configuration.getInstance().load(this, getSharedPreferences(OSMDROID_PREFERENCES, MODE_PRIVATE))
    }

    private companion object {
        const val OSMDROID_PREFERENCES = "osmdroid"
    }
}
