package kniezrec.com.flightinfo.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapZoomPolicyTest {
    @Test fun largerZoomChangesOnlyTheMaximum() {
        assertEquals(6.0, MapSessionRules.maxZoom(false), 0.0)
        assertEquals(9.0, MapSessionRules.maxZoom(true), 0.0)
    }

    @Test fun defaultAndFollowZoomRemainUnchanged() {
        val rules = MapSessionRules()
        assertEquals(3.0, rules.recenter().zoom, 0.0)
        assertEquals(6.0, MapSessionRules.FOLLOW_ZOOM, 0.0)
    }

    @Test fun standardRangeWarningIsSuppressedForLargerZoom() {
        assertEquals(true, MapSessionRules.shouldShowMaximumZoomWarning(6.0, false))
        assertEquals(false, MapSessionRules.shouldShowMaximumZoomWarning(6.0, true))
        assertEquals(false, MapSessionRules.shouldShowMaximumZoomWarning(5.9, false))
    }

    @Test fun disablingLargerZoomClampsAnOutOfRangeViewport() {
        assertEquals(6.0, MapSessionRules.reconcileZoom(8.0, false), 0.0)
        assertEquals(8.0, MapSessionRules.reconcileZoom(8.0, true), 0.0)
    }
}
