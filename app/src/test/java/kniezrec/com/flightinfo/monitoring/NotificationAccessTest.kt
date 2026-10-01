package kniezrec.com.flightinfo.monitoring

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationAccessTest {
    @Test fun `below Android 13 only the notification switches matter`() {
        assertEquals(NotificationAccess.Allowed, access(sdk = 32, granted = false, enabled = true))
        assertEquals(NotificationAccess.Blocked, access(sdk = 32, granted = false, enabled = false))
    }

    @Test fun `granted permission is allowed unless notifications are switched off`() {
        assertEquals(NotificationAccess.Allowed, access(granted = true, enabled = true))
        assertEquals(NotificationAccess.Blocked, access(granted = true, enabled = false))
    }

    @Test fun `not granted is requestable before the first request and while a rationale is shown`() {
        assertEquals(NotificationAccess.Requestable, access(requested = false, rationale = false))
        assertEquals(NotificationAccess.Requestable, access(requested = true, rationale = true))
    }

    @Test fun `requested without rationale is a permanent denial`() {
        assertEquals(NotificationAccess.Blocked, access(requested = true, rationale = false))
    }

    private fun access(
        sdk: Int = 33,
        granted: Boolean = false,
        enabled: Boolean = true,
        requested: Boolean = false,
        rationale: Boolean = false,
    ) = notificationAccess(sdk, granted, enabled, requested, rationale)
}
