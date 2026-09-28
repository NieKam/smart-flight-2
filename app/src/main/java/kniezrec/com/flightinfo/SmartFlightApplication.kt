package kniezrec.com.flightinfo

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Hilt root. Must stay free of heavy work: Robolectric creates it for every JVM test. */
@HiltAndroidApp
class SmartFlightApplication : Application()
