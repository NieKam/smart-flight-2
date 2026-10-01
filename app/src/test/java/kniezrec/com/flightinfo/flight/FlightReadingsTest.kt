package kniezrec.com.flightinfo.flight

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FlightReadingsTest {
    @Test fun `first altitude sample has no vertical speed and the next one computes the raw rate`() {
        val first = verticalSpeedStep(null, 100.0, 1_000_000_000L)
        assertEquals(VerticalSpeedStep(AltitudeSample(100.0, 1_000_000_000L), null), first)

        val second = verticalSpeedStep(first.sample, 104.0, 3_000_000_000L)
        assertEquals(VerticalSpeedStep(AltitudeSample(104.0, 3_000_000_000L), 2.0), second)
    }

    @Test fun `missing or non-finite altitude has no rate and keeps the history`() {
        val previous = AltitudeSample(100.0, 1_000_000_000L)

        assertEquals(VerticalSpeedStep(previous, null), verticalSpeedStep(previous, null, 2_000_000_000L))
        assertEquals(VerticalSpeedStep(previous, null), verticalSpeedStep(previous, Double.NaN, 2_000_000_000L))
    }

    @Test fun `a timestamp that does not move forward has no rate and clears the history`() {
        val previous = AltitudeSample(100.0, 2_000_000_000L)

        assertEquals(VerticalSpeedStep(null, null), verticalSpeedStep(previous, 102.0, 2_000_000_000L))
        assertEquals(VerticalSpeedStep(null, null), verticalSpeedStep(previous, 102.0, 1_000_000_000L))
    }

    @Test fun `every collection starts a new session from waiting`() =
        runTest {
            val fixes = flowOf(FlightLocationFix(10.0, 100.0, 1_000_000_000L), FlightLocationFix(10.0, 104.0, 3_000_000_000L))

            val expected =
                listOf(
                    FlightParametersState.Waiting,
                    FlightParametersState.Readings(36.0, null, 100.0),
                    FlightParametersState.Readings(36.0, 2.0, 104.0),
                )
            assertEquals(expected, fixes.flightParameters().toList())
            assertEquals(expected, fixes.flightParameters().toList())
        }

    @Test fun `averaged rates keep the last three and start without a rate`() {
        val sample = AltitudeSample(100.0, 1L)
        assertEquals(emptyList<Double>(), averagedRates(emptyList(), VerticalSpeedStep(sample, null)))
        assertEquals(listOf(1.0), averagedRates(emptyList(), VerticalSpeedStep(sample, 1.0)))
        assertEquals(listOf(1.0, 2.0, 3.0), averagedRates(listOf(1.0, 2.0), VerticalSpeedStep(sample, 3.0)))
        assertEquals(listOf(2.0, 3.0, 4.0), averagedRates(listOf(1.0, 2.0, 3.0), VerticalSpeedStep(sample, 4.0)))
    }

    @Test fun `a step without a rate keeps the window and a cleared history clears it`() {
        val sample = AltitudeSample(100.0, 1L)
        assertEquals(listOf(1.0, 2.0), averagedRates(listOf(1.0, 2.0), VerticalSpeedStep(sample, null)))
        assertEquals(emptyList<Double>(), averagedRates(listOf(1.0, 2.0), VerticalSpeedStep(null, null)))
    }

    @Test fun `session shows the moving average, none for the first sample, and restarts after a timestamp reset`() {
        val states =
            listOf(
                FlightLocationFix(10.0, 100.0, 1_000_000_000L),
                FlightLocationFix(10.0, 102.0, 2_000_000_000L),
                FlightLocationFix(10.0, 106.0, 3_000_000_000L),
                FlightLocationFix(10.0, 112.0, 4_000_000_000L),
                FlightLocationFix(10.0, 120.0, 5_000_000_000L),
                // Non-monotonic: clears the history and the window.
                FlightLocationFix(10.0, 120.0, 5_000_000_000L),
                FlightLocationFix(10.0, 121.0, 6_000_000_000L),
                FlightLocationFix(10.0, 122.0, 7_000_000_000L),
            ).runningFold(FlightReadingsSession()) { session, fix -> session.accept(fix) }
                .drop(1)
                .map { (it.state as FlightParametersState.Readings).verticalSpeedMetresPerSecond }

        assertEquals(listOf(null, 2.0, 3.0, 4.0, 6.0, null, null, 1.0), states)
    }

    @Test fun `pressure is attached with or without GPS readings`() {
        assertEquals(FlightParametersState.Waiting, FlightParametersState.Waiting.withPressure(null))
        assertEquals(FlightParametersState.Readings(null, null, null, 1013.25), FlightParametersState.Waiting.withPressure(1013.25))
        assertEquals(
            FlightParametersState.Readings(36.0, null, 100.0, 1013.25),
            FlightParametersState.Readings(36.0, null, 100.0).withPressure(1013.25),
        )
        assertEquals(
            FlightParametersState.Readings(36.0, null, 100.0, null),
            FlightParametersState.Readings(36.0, null, 100.0, 1013.25).withPressure(null),
        )
    }
}
