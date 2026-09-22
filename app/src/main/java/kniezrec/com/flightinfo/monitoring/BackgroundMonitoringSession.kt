package kniezrec.com.flightinfo.monitoring

enum class BackgroundMonitoringState { Inactive, ForegroundObserving, BackgroundWaiting, BackgroundFixedThenStopped }

class BackgroundMonitoringSession {
    var state: BackgroundMonitoringState = BackgroundMonitoringState.Inactive
        private set

    fun startForeground(): Boolean {
        if (state == BackgroundMonitoringState.ForegroundObserving) return false
        state = BackgroundMonitoringState.ForegroundObserving
        return true
    }

    fun background(showNotification: Boolean): Boolean {
        if (state != BackgroundMonitoringState.ForegroundObserving) return false
        state = if (showNotification) BackgroundMonitoringState.BackgroundWaiting else BackgroundMonitoringState.Inactive
        return showNotification
    }

    fun usableFix(): Boolean {
        if (state != BackgroundMonitoringState.BackgroundWaiting) return false
        state = BackgroundMonitoringState.BackgroundFixedThenStopped
        return true
    }

    fun stop() {
        state = BackgroundMonitoringState.Inactive
    }
}
