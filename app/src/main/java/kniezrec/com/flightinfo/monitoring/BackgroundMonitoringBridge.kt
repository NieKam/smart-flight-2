package kniezrec.com.flightinfo.monitoring

import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.gnss.GnssSatellite

/** Process-local handoff; the service owns platform callbacks and the activity consumes events. */
internal object BackgroundMonitoringBridge {
    private var service: LocationForegroundService? = null
    private var activityVisible = false
    private var hasUsableFix = false
    private var onEligibilityLost: (() -> Unit)? = null
    private var onLocation: ((FlightLocationFix) -> Unit)? = null
    private var onGnssStatus: ((List<GnssSatellite>) -> Unit)? = null

    fun beginSession() {
        hasUsableFix = false
    }

    fun attach(service: LocationForegroundService) {
        this.service = service
        service.reconcile(activityVisible, hasUsableFix)
    }

    fun detach(service: LocationForegroundService) {
        if (this.service === service) this.service = null
    }

    fun setActivityVisible(visible: Boolean) {
        activityVisible = visible
        if (!visible && hasUsableFix) {
            service?.onUsableFix()
        } else {
            service?.reconcile(activityVisible, hasUsableFix)
        }
    }

    fun setEligibilityLostHandler(handler: () -> Unit) {
        onEligibilityLost = handler
    }

    fun notifyEligibilityLost() {
        onEligibilityLost?.invoke()
    }

    fun onUsableLocationFix(_fix: FlightLocationFix) {
        hasUsableFix = true
        if (!activityVisible) service?.onUsableFix()
    }

    fun setEventHandlers(
        onLocation: (FlightLocationFix) -> Unit,
        onGnssStatus: (List<GnssSatellite>) -> Unit,
    ) {
        this.onLocation = onLocation
        this.onGnssStatus = onGnssStatus
    }

    fun forwardLocation(fix: FlightLocationFix) {
        onLocation?.invoke(fix)
        onUsableLocationFix(fix)
    }

    fun forwardGnssStatus(satellites: List<GnssSatellite>) {
        onGnssStatus?.invoke(satellites)
    }

    fun clear() {
        activityVisible = false
        hasUsableFix = false
        service = null
        onEligibilityLost = null
        onLocation = null
        onGnssStatus = null
    }
}
