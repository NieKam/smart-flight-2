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
        assertEquals(false, MapSessionRules.shouldShowMaximumZoomWarning(target.zoomLevel, true))

        assertEquals(true, applyMapZoomPolicy(target, largerMapZoom = false))
        assertEquals(true, MapSessionRules.shouldShowMaximumZoomWarning(target.zoomLevel, false))
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
