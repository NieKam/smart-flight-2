package kniezrec.com.flightinfo.gnss

/**
 * Turns the satellite reports of the shared GNSS registration into the card state. Reports are
 * accepted between [start] and [stop].
 */
internal class GnssStatusController(
    private val onStateChanged: (GnssStatusState) -> Unit,
) {
    private var active = false

    /** Clears the previous report (waiting) and accepts reports until [stop]. */
    fun start() {
        active = true
        onStateChanged(GnssStatusState.Waiting)
    }

    /** Ignores later reports; the card keeps its current state. */
    fun stop() {
        active = false
    }

    fun acceptStatus(satellites: List<GnssSatellite>) {
        if (active) onSatelliteStatus(satellites)
    }

    private fun onSatelliteStatus(satellites: List<GnssSatellite>) {
        onStateChanged(
            if (satellites.isEmpty()) GnssStatusState.Waiting else GnssStatusState.Available(satellites),
        )
    }
}
