package kniezrec.com.flightinfo.monitoring

import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.flight.FlightLocationPlatform
import kniezrec.com.flightinfo.gnss.GnssSatellite
import kniezrec.com.flightinfo.gnss.GnssStatusPlatform

internal class LocationGnssMonitoringSession(
    private val locationPlatform: FlightLocationPlatform,
    private val gnssPlatform: GnssStatusPlatform,
    private val onLocation: (FlightLocationFix) -> Unit,
    private val onGnssStatus: (List<GnssSatellite>) -> Unit,
) {
    private var generation = 0L
    private var locationRegistered = false
    private var gnssRegistered = false

    fun start(): Boolean {
        stop()
        if (!locationPlatform.areLocationServicesEnabled() || !locationPlatform.hasGnssHardware()) return false
        val currentGeneration = ++generation
        locationRegistered =
            runCatching {
                locationPlatform.registerLocationListener { fix ->
                    if (locationRegistered && generation == currentGeneration) onLocation(fix)
                }
            }.getOrDefault(false)
        if (!locationRegistered) {
            stop()
            return false
        }
        gnssRegistered =
            runCatching {
                gnssPlatform.registerGnssStatusCallback { satellites ->
                    if (gnssRegistered && generation == currentGeneration) onGnssStatus(satellites)
                }
            }.getOrDefault(false)
        if (!gnssRegistered) {
            stop()
            return false
        }
        return true
    }

    fun stop() {
        generation++
        if (locationRegistered) locationPlatform.unregisterLocationListener()
        if (gnssRegistered) gnssPlatform.unregisterGnssStatusCallback()
        locationRegistered = false
        gnssRegistered = false
    }

    val isActive: Boolean get() = locationRegistered || gnssRegistered
}
