package kniezrec.com.flightinfo.testutil

import kniezrec.com.flightinfo.map.data.MapTipRepository

/** In-memory [MapTipRepository]. */
class FakeMapTipRepository(
    var shownCount: Int = 0,
) : MapTipRepository {
    override suspend fun zoomTipShownCount(): Int = shownCount

    override suspend fun recordZoomTipShown() {
        shownCount++
    }
}
