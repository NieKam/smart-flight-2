package kniezrec.com.flightinfo.gnss

/** Narrow, testable boundary around Android GNSS registration. */
internal interface GnssStatusPlatform {
    fun areLocationServicesEnabled(): Boolean

    fun hasGnssHardware(): Boolean

    /** Returns false when the platform declines callback registration. */
    fun registerGnssStatusCallback(onStatus: (List<GnssSatellite>) -> Unit): Boolean

    fun unregisterGnssStatusCallback()
}

/** Owns one active GNSS callback and clears old reports before every registration attempt. */
internal class GnssStatusController(
    private val platform: GnssStatusPlatform,
    private val onStateChanged: (GnssStatusState) -> Unit,
) {
    private var registered = false

    fun start() {
        stop()
        when {
            !platform.areLocationServicesEnabled() -> onStateChanged(GnssStatusState.LocationServicesDisabled)
            !platform.hasGnssHardware() -> onStateChanged(GnssStatusState.Unavailable)
            else -> {
                onStateChanged(GnssStatusState.Waiting)
                registered =
                    try {
                        platform.registerGnssStatusCallback(::onSatelliteStatus)
                    } catch (_: SecurityException) {
                        false
                    } catch (_: RuntimeException) {
                        false
                    }
                if (!registered) onStateChanged(GnssStatusState.Error)
            }
        }
    }

    fun stop() {
        if (registered) platform.unregisterGnssStatusCallback()
        registered = false
    }

    fun showError() {
        stop()
        onStateChanged(GnssStatusState.Error)
    }

    fun attachToExternalSession() {
        stop()
        registered = true
        onStateChanged(GnssStatusState.Waiting)
    }

    fun acceptStatus(satellites: List<GnssSatellite>) {
        if (registered) onSatelliteStatus(satellites)
    }

    private fun onSatelliteStatus(satellites: List<GnssSatellite>) {
        onStateChanged(
            if (satellites.isEmpty()) GnssStatusState.Waiting else GnssStatusState.Available(satellites),
        )
    }
}
