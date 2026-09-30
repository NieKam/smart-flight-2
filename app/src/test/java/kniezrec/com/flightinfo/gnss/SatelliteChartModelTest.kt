package kniezrec.com.flightinfo.gnss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SatelliteChartModelTest {
    @Test fun `bars keep the report order and are colored by use in the fix`() {
        val chart = satelliteChartModel(listOf(GnssSatellite(true, 30f), GnssSatellite(false, 20f), GnssSatellite(true, 10f)))

        assertEquals(listOf(SatelliteBar(30f, true), SatelliteBar(20f, false), SatelliteBar(10f, true)), chart.bars)
        assertEquals(2, chart.usedCount)
        assertEquals(30f, chart.strongestDbHz)
    }

    @Test fun `eight integer y labels from 1 with headroom above the strongest signal, as the original screenshot`() {
        val chart = satelliteChartModel(listOf(GnssSatellite(true, 19.2f), GnssSatellite(false, 13f)))

        assertEquals(listOf(1, 4, 7, 10, 13, 16, 19, 22), chart.yLabels)
        assertEquals(22, chart.yAxisMaximum)
    }

    @Test fun `the y axis always reaches the strongest signal`() {
        listOf(0.5f, 2f, 7.9f, 12.3f, 20f, 23f, 35.5f, 48f, 60f).forEach { strongest ->
            val chart = satelliteChartModel(listOf(GnssSatellite(true, strongest)))
            assertEquals(Y_LABEL_COUNT, chart.yLabels.size)
            assertEquals(Y_AXIS_MINIMUM, chart.yLabels.first())
            val step = chart.yLabels[1] - chart.yLabels[0]
            assert(step >= 1)
            assert(chart.yLabels.zipWithNext().all { (a, b) -> b - a == step })
            assert(chart.yAxisMaximum >= strongest) { "axis ${chart.yAxisMaximum} below $strongest" }
        }
    }

    @Test fun `bar heights scale from the axis minimum to the axis maximum`() {
        val chart = satelliteChartModel(listOf(GnssSatellite(true, 22f), GnssSatellite(false, 1f), GnssSatellite(false, 11.5f)))
        val max = chart.yAxisMaximum.toFloat()

        assertEquals((22f - 1f) / (max - 1f), chart.heightFraction(chart.bars[0]), 1e-6f)
        assertEquals(0f, chart.heightFraction(chart.bars[1]), 0f)
        assertEquals((11.5f - 1f) / (max - 1f), chart.heightFraction(chart.bars[2]), 1e-6f)
    }

    @Test fun `missing, negative and non-finite signals are bars without height`() {
        val chart =
            satelliteChartModel(
                listOf(
                    GnssSatellite(true, null),
                    GnssSatellite(false, Float.NaN),
                    GnssSatellite(false, Float.POSITIVE_INFINITY),
                    GnssSatellite(true, -5f),
                ),
            )

        assertEquals(listOf(0f, 0f, 0f, 0f), chart.bars.map { it.signalDbHz })
        assertEquals(listOf(0f, 0f, 0f, 0f), chart.bars.map(chart::heightFraction))
        assertNull(chart.strongestDbHz)
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), chart.yLabels)
    }

    @Test fun `empty input gives no bars and a default axis`() {
        val chart = satelliteChartModel(emptyList())

        assertEquals(emptyList<SatelliteBar>(), chart.bars)
        assertEquals(emptyList<Int>(), chart.xLabels)
        assertEquals(0, chart.usedCount)
        assertNull(chart.strongestDbHz)
        assertEquals(8, chart.yAxisMaximum)
    }

    @Test fun `x labels every third index for twenty satellites and never more than seven`() {
        assertEquals(listOf(0, 3, 6, 9, 12, 15, 18), satelliteChartModel(satellites(20)).xLabels)
        assertEquals(listOf(0, 1, 2, 3, 4), satelliteChartModel(satellites(5)).xLabels)
        listOf(1, 7, 8, 40, 64, 100).forEach { count ->
            val labels = satelliteChartModel(satellites(count)).xLabels
            assert(labels.size <= MAX_X_LABELS) { "$count satellites: ${labels.size} labels" }
            assertEquals(0, labels.first())
        }
    }

    private fun satellites(count: Int) = List(count) { GnssSatellite(it % 2 == 0, 20f) }
}
