package kniezrec.com.flightinfo.testutil

import android.os.Looper
import kniezrec.com.flightinfo.flight.FlightLocationFix
import org.robolectric.Shadows.shadowOf

/** Builds a [FlightLocationFix] with readable defaults; timestamps are in seconds for clarity. */
fun flightFix(
    speedMetresPerSecond: Double? = 10.0,
    altitudeMetres: Double? = 100.0,
    elapsedSeconds: Long = 1L,
    bearingDegrees: Double? = null,
    latitude: Double? = null,
    longitude: Double? = null,
): FlightLocationFix =
    FlightLocationFix(
        speedMetresPerSecond = speedMetresPerSecond,
        altitudeMetres = altitudeMetres,
        elapsedRealtimeNanos = elapsedSeconds * 1_000_000_000L,
        bearingDegrees = bearingDegrees,
        latitude = latitude,
        longitude = longitude,
    )

/** Runs every task currently queued on the Robolectric main looper. Call from the test thread. */
fun idleMainLooper() {
    shadowOf(Looper.getMainLooper()).idle()
}
