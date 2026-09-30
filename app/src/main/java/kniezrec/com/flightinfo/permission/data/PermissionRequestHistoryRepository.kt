package kniezrec.com.flightinfo.permission.data

import android.content.SharedPreferences
import kniezrec.com.flightinfo.permission.LocationPermissionRequestHistory
import kniezrec.com.flightinfo.permission.data.di.LocationPermissionPreferences
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LocationPermissionRequestHistory] over the `location_permission` file; it also records the
 * POST_NOTIFICATIONS request (the same no-rationale ambiguity, one permission-history store).
 */
@Singleton
class PermissionRequestHistoryRepository
    @Inject
    constructor(
        @LocationPermissionPreferences private val preferences: SharedPreferences,
    ) : LocationPermissionRequestHistory {
        override var hasRequestedFineLocation: Boolean
            get() = preferences.getBoolean(KEY_HAS_REQUESTED, false)
            set(value) {
                preferences.edit().putBoolean(KEY_HAS_REQUESTED, value).apply()
            }

        /** Whether POST_NOTIFICATIONS has been requested (tells a first request from a permanent denial). */
        var hasRequestedNotifications: Boolean
            get() = preferences.getBoolean(KEY_HAS_REQUESTED_NOTIFICATIONS, false)
            set(value) {
                preferences.edit().putBoolean(KEY_HAS_REQUESTED_NOTIFICATIONS, value).apply()
            }

        private companion object {
            const val KEY_HAS_REQUESTED = "has_requested_location_permission"
            const val KEY_HAS_REQUESTED_NOTIFICATIONS = "has_requested_notification_permission"
        }
    }
