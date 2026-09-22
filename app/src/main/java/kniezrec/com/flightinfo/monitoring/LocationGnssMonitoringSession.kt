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
) : MonitoringSession {
    private var generation = 0L
    private var locationRegistered = false
    private var gnssRegistered = false

    override fun start(): Boolean {
        stop()
        if (!locationPlatform.areLocationServicesEnabled() || !locationPlatform.hasGnssHardware()) return false
        val currentGeneration = ++generation
        // Mark ownership before calling into the platform. Android registration may install
        // the callback and then throw (or return false), so stop() must still unregister it.
        locationRegistered = true
        val locationRegistrationSucceeded =
            runCatching {
                locationPlatform.registerLocationListener { fix ->
                    if (locationRegistered && generation == currentGeneration) onLocation(fix)
                }
            }.getOrDefault(false)
        if (!locationRegistrationSucceeded) {
            stop()
            return false
        }
        gnssRegistered = true
        val gnssRegistrationSucceeded =
            runCatching {
                gnssPlatform.registerGnssStatusCallback { satellites ->
                    if (gnssRegistered && generation == currentGeneration) onGnssStatus(satellites)
                }
            }.getOrDefault(false)
        if (!gnssRegistrationSucceeded) {
            stop()
            return false
        }
        return true
    }

    override fun stop() {
        generation++
        if (locationRegistered) runCatching { locationPlatform.unregisterLocationListener() }
        if (gnssRegistered) runCatching { gnssPlatform.unregisterGnssStatusCallback() }
        locationRegistered = false
        gnssRegistered = false
    }

    val isActive: Boolean get() = locationRegistered || gnssRegistered
}
