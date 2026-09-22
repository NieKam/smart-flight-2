package kniezrec.com.flightinfo.monitoring

import kniezrec.com.flightinfo.flight.FlightLocationFix

/** Process-local handoff; the activity owns location/GNSS callbacks. */
internal object BackgroundMonitoringBridge {
    private var service: LocationForegroundService? = null
    private var activityVisible = false
    private var hasUsableFix = false
    private var onEligibilityLost: (() -> Unit)? = null

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
        service?.reconcile(activityVisible, hasUsableFix)
    }

    fun setEligibilityLostHandler(handler: () -> Unit) {
        onEligibilityLost = handler
    }

    fun notifyEligibilityLost() {
        onEligibilityLost?.invoke()
    }

    fun onUsableLocationFix(_fix: FlightLocationFix) {
        hasUsableFix = true
        service?.onUsableFix()
    }

    fun clear() {
        activityVisible = false
        hasUsableFix = false
        service = null
        onEligibilityLost = null
    }
}
