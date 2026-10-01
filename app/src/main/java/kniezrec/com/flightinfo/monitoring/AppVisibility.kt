package kniezrec.com.flightinfo.monitoring

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the dashboard is in the foreground (between `onResume` and `onPause`). Written by
 * `MainActivity`, observed by [LocationForegroundService] to decide its background behavior.
 */
@Singleton
class AppVisibility
    @Inject
    constructor() {
        private val state = MutableStateFlow(false)

        val visible: StateFlow<Boolean> = state.asStateFlow()

        fun setVisible(visible: Boolean) {
            state.value = visible
        }
    }
