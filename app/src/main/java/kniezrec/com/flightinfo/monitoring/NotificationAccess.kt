package kniezrec.com.flightinfo.monitoring

/** Whether the background notification can be shown, and what the user can do about it. */
enum class NotificationAccess {
    /** Notifications can be posted. */
    Allowed,

    /** Android 13+: POST_NOTIFICATIONS can still be requested with the system dialog. */
    Requestable,

    /** Denied permanently, or notifications switched off for the app or its channel: only the system settings help. */
    Blocked,
}

/**
 * [NotificationAccess] from the platform state read at one moment. Below Android 13 there is no
 * runtime permission. Android returns no rationale both before the first request and after a
 * permanent denial; [hasRequested] tells the two apart (as for location).
 *
 * @param notificationsEnabled notifications are on for the app and the channel is not blocked.
 */
fun notificationAccess(
    sdkInt: Int,
    permissionGranted: Boolean,
    notificationsEnabled: Boolean,
    hasRequested: Boolean,
    shouldShowRationale: Boolean,
): NotificationAccess =
    when {
        sdkInt < POST_NOTIFICATIONS_SDK || permissionGranted ->
            if (notificationsEnabled) NotificationAccess.Allowed else NotificationAccess.Blocked
        hasRequested && !shouldShowRationale -> NotificationAccess.Blocked
        else -> NotificationAccess.Requestable
    }

/** Android 13 (TIRAMISU), where POST_NOTIFICATIONS became a runtime permission. */
const val POST_NOTIFICATIONS_SDK = 33
