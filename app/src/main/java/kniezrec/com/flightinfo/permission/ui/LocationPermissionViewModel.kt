package kniezrec.com.flightinfo.permission.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kniezrec.com.flightinfo.permission.LocationPermissionRequestHistory
import kniezrec.com.flightinfo.permission.LocationPermissionState
import kniezrec.com.flightinfo.permission.PermissionSnapshot
import kniezrec.com.flightinfo.permission.locationPermissionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Location-permission state of the dashboard.
 *
 * The Activity owns the Android side (permission launcher, rationale check) and passes a fresh
 * [PermissionSnapshot] to [refresh] on creation, on resume and after a permission result. The
 * durable request history distinguishes a first launch from Android's no-rationale denial.
 */
@HiltViewModel
class LocationPermissionViewModel
    @Inject
    constructor(
        private val requestHistory: LocationPermissionRequestHistory,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(LocationPermissionState.Requestable)
        private val mutableAnnounceChange = MutableStateFlow(false)

        val state: StateFlow<LocationPermissionState> = mutableState.asStateFlow()

        /** True once a permission result has been handled; the onboarding card then announces changes. */
        val announceChange: StateFlow<Boolean> = mutableAnnounceChange.asStateFlow()

        /** Recomputes [state] from [snapshot]; [announceChange] is set when this follows a permission result. */
        fun refresh(
            snapshot: PermissionSnapshot,
            announceChange: Boolean = false,
        ): LocationPermissionState {
            val current =
                locationPermissionState(
                    isFineLocationGranted = snapshot.isFineLocationGranted,
                    isCoarseLocationGranted = snapshot.isCoarseLocationGranted,
                    hasRequestedPermission = requestHistory.hasRequestedFineLocation,
                    shouldShowRationale = snapshot.shouldShowRationale,
                )
            mutableState.value = current
            if (announceChange) mutableAnnounceChange.value = true
            return current
        }

        /** Records the request before the platform dialog opens, so it survives a process restart. */
        fun onRequestLaunched() {
            requestHistory.hasRequestedFineLocation = true
        }
    }
