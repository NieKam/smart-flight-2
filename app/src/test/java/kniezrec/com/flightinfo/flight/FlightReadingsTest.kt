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

    @Test fun `pressure is attached to readings only`() {
        assertEquals(FlightParametersState.Waiting, FlightParametersState.Waiting.withPressure(1013.25))
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
