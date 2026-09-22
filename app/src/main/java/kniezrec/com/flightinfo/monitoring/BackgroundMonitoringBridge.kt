package kniezrec.com.flightinfo.monitoring

import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.gnss.GnssSatellite

internal interface BackgroundMonitoringService {
    fun reconcile(
        activityVisible: Boolean,
        hasUsableFix: Boolean,
    )

    fun onUsableFix()

    fun stopForPreferenceDisabled()
}

/** Process-local handoff; the service owns platform callbacks and the activity consumes events. */
internal object BackgroundMonitoringBridge {
    private var service: BackgroundMonitoringService? = null
    private var serviceGeneration = 0L
    private var activityVisible = false
    private var hasUsableFix = false
    private var onEligibilityLost: (() -> Unit)? = null
    private var onLocation: ((FlightLocationFix) -> Unit)? = null
    private var onGnssStatus: ((List<GnssSatellite>) -> Unit)? = null

    fun beginSession() {
        if (service == null) hasUsableFix = false
    }

    /** Attaches the current service and returns a token for its callback closures. */
    fun attach(service: BackgroundMonitoringService): Long {
        serviceGeneration++
        this.service = service
        return serviceGeneration
    }

    fun reconcile() {
        service?.reconcile(activityVisible, hasUsableFix)
    }

    fun detach(
        service: BackgroundMonitoringService,
        generation: Long,
    ) {
        if (this.service === service && generation == serviceGeneration) {
            this.service = null
            serviceGeneration++
        }
    }

    fun forwardLocation(
        generation: Long,
        fix: FlightLocationFix,
    ) {
        if (generation == serviceGeneration) forwardLocation(fix)
    }

    fun forwardGnssStatus(
        generation: Long,
        satellites: List<GnssSatellite>,
    ) {
        if (generation == serviceGeneration) forwardGnssStatus(satellites)
    }

    fun setActivityVisible(visible: Boolean) {
        activityVisible = visible
        if (!visible && hasUsableFix) {
            service?.onUsableFix()
        } else {
            service?.reconcile(activityVisible, hasUsableFix)
        }
    }

    fun setNotificationEnabled(enabled: Boolean) {
        if (enabled) {
            service?.reconcile(activityVisible, hasUsableFix)
        } else if (!activityVisible) {
            service?.stopForPreferenceDisabled()
        }
    }

    fun setEligibilityLostHandler(handler: () -> Unit) {
        onEligibilityLost = handler
    }

    fun notifyEligibilityLost() {
        onEligibilityLost?.invoke()
    }

    fun onUsableLocationFix(_fix: FlightLocationFix) {
        if (!hasUsableFix) {
            hasUsableFix = true
            if (!activityVisible) service?.onUsableFix()
        }
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
        serviceGeneration++
        onEligibilityLost = null
    }

    fun clearEventHandlers() {
        onLocation = null
        onGnssStatus = null
    }
}
