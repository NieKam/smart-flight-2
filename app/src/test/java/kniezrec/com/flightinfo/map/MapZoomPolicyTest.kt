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

    @Test fun theStandardMaximumIsReachedAtZoomSix() {
        assertEquals(true, MapRules.isAtStandardMaximum(6.0))
        assertEquals(true, MapRules.isAtStandardMaximum(7.0))
        assertEquals(false, MapRules.isAtStandardMaximum(5.9))
    }

    @Test fun zoomTipOnlyWithStandardZoomAndFewerThanFourShows() {
        assertEquals(true, MapRules.shouldShowZoomTip(largerMapZoom = false, shownCount = 0))
        assertEquals(true, MapRules.shouldShowZoomTip(largerMapZoom = false, shownCount = 3))
        assertEquals(false, MapRules.shouldShowZoomTip(largerMapZoom = false, shownCount = 4))
        assertEquals(false, MapRules.shouldShowZoomTip(largerMapZoom = false, shownCount = 5))
        assertEquals(false, MapRules.shouldShowZoomTip(largerMapZoom = true, shownCount = 0))
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

    @Test fun enablingInPlaceUpdatePreservesTheViewportAndDisablingRestoresTheStandardMaximum() {
        val target = FakeMapZoomTarget(maxZoomLevel = 6.0, zoom = 6.0)

        assertEquals(true, applyMapZoomPolicy(target, largerMapZoom = true))
        assertEquals(6.0, target.zoomLevel, 0.0)
        assertEquals(9.0, target.maxZoomLevel, 0.0)
        assertEquals(1, target.invalidateCount)

        assertEquals(true, applyMapZoomPolicy(target, largerMapZoom = false))
        assertEquals(6.0, target.zoomLevel, 0.0)
        assertEquals(6.0, target.maxZoomLevel, 0.0)
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
