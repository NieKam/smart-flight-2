package kniezrec.com.flightinfo.map

import org.junit.Assert.assertEquals
import org.junit.Test

class MapZoomPolicyTest {
    @Test fun largerZoomChangesOnlyTheMaximum() {
        assertEquals(6.0, MapRules.maxZoom(false), 0.0)
        assertEquals(9.0, MapRules.maxZoom(true), 0.0)
    }

    @Test fun defaultAndFollowZoomRemainUnchanged() {
        assertEquals(3.0, MapRules.recenter(null).zoom, 0.0)
        assertEquals(6.0, MapRules.FOLLOW_ZOOM, 0.0)
    }

    @Test fun standardRangeWarningIsSuppressedForLargerZoom() {
        assertEquals(true, MapRules.shouldShowMaximumZoomWarning(6.0, false))
        assertEquals(false, MapRules.shouldShowMaximumZoomWarning(6.0, true))
        assertEquals(false, MapRules.shouldShowMaximumZoomWarning(5.9, false))
    }

    @Test fun disablingLargerZoomClampsAnOutOfRangeViewport() {
        assertEquals(6.0, MapRules.reconcileZoom(8.0, false), 0.0)
        assertEquals(8.0, MapRules.reconcileZoom(8.0, true), 0.0)
    }

    @Test fun inPlaceUpdateClampsBeforeRestoringStandardMaximum() {
        val target = FakeMapZoomTarget(maxZoomLevel = 9.0, zoom = 8.0)

        assertEquals(true, applyMapZoomPolicy(target, largerMapZoom = false))
        assertEquals(6.0, target.zoomLevel, 0.0)
        assertEquals(6.0, target.maxZoomLevel, 0.0)
        assertEquals(1, target.invalidateCount)
    }

    @Test fun enablingInPlaceUpdatePreservesViewportAndWarningRestoresWhenDisabled() {
        val target = FakeMapZoomTarget(maxZoomLevel = 6.0, zoom = 6.0)

        assertEquals(true, applyMapZoomPolicy(target, largerMapZoom = true))
        assertEquals(6.0, target.zoomLevel, 0.0)
        assertEquals(9.0, target.maxZoomLevel, 0.0)
        assertEquals(1, target.invalidateCount)
        assertEquals(false, MapRules.shouldShowMaximumZoomWarning(target.zoomLevel, true))

        assertEquals(true, applyMapZoomPolicy(target, largerMapZoom = false))
        assertEquals(true, MapRules.shouldShowMaximumZoomWarning(target.zoomLevel, false))
    }

    private class FakeMapZoomTarget(
        override var maxZoomLevel: Double,
        private var zoom: Double,
    ) : MapZoomTarget {
        var invalidateCount = 0
        override val zoomLevel: Double get() = zoom

        override fun setZoom(zoom: Double) {
            this.zoom = zoom
        }

        override fun invalidate() {
            invalidateCount++
        }
    }
}
