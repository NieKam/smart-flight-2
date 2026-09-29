package kniezrec.com.flightinfo.flight

import org.junit.Assert.assertEquals
import org.junit.Test

class FlightParametersControllerTest {
    private val states = mutableListOf<FlightParametersState>()
    private val forwardedFixes = mutableListOf<FlightLocationFix>()
    private val controller = FlightParametersController(states::add, onLocationFix = forwardedFixes::add)

    @Test fun `first altitude sample has no vertical speed and second computes rate`() {
        controller.start()

        controller.acceptLocationFix(FlightLocationFix(10.0, 100.0, 1_000_000_000L))
        assertEquals(36.0, (states.last() as FlightParametersState.Readings).speedKilometresPerHour)
        assertEquals(null, (states.last() as FlightParametersState.Readings).verticalSpeedMetresPerSecond)

        controller.acceptLocationFix(FlightLocationFix(10.0, 104.0, 3_000_000_000L))
        assertEquals(2.0, (states.last() as FlightParametersState.Readings).verticalSpeedMetresPerSecond)
    }

    @Test fun `missing fields and invalid intervals do not invent readings`() {
        controller.start()

        controller.acceptLocationFix(FlightLocationFix(null, 100.0, 2_000_000_000L))
        controller.acceptLocationFix(FlightLocationFix(null, 102.0, 2_000_000_000L))
        val invalidInterval = states.last() as FlightParametersState.Readings
        assertEquals(null, invalidInterval.speedKilometresPerHour)
        assertEquals(null, invalidInterval.verticalSpeedMetresPerSecond)

        controller.acceptLocationFix(FlightLocationFix(null, 104.0, 3_000_000_000L))
        assertEquals(null, (states.last() as FlightParametersState.Readings).verticalSpeedMetresPerSecond)
    }

    @Test fun `initial fix without a displayable field remains waiting`() {
        controller.start()

        controller.acceptLocationFix(FlightLocationFix(null, null, 1_000_000_000L))
        controller.acceptLocationFix(FlightLocationFix(Double.NaN, Double.POSITIVE_INFINITY, 2_000_000_000L))

        assertEquals(listOf(FlightParametersState.Waiting), states)
    }

    @Test fun `fixes before start are ignored`() {
        controller.acceptLocationFix(FlightLocationFix(10.0, 100.0, 1_000_000_000L))

        assertEquals(emptyList<FlightParametersState>(), states)
        assertEquals(emptyList<FlightLocationFix>(), forwardedFixes)
    }

    @Test fun `stop resets to waiting and ignores later fixes`() {
        controller.start()
        controller.acceptLocationFix(FlightLocationFix(10.0, 100.0, 1_000_000_000L))

        controller.stop()
        controller.acceptLocationFix(FlightLocationFix(20.0, 110.0, 2_000_000_000L))

        assertEquals(FlightParametersState.Waiting, states.last())
        assertEquals(1, forwardedFixes.size)
    }

    @Test fun `restart resets to waiting and clears the altitude history`() {
        controller.start()
        controller.acceptLocationFix(FlightLocationFix(10.0, 100.0, 1_000_000_000L))

        controller.start()
        assertEquals(FlightParametersState.Waiting, states.last())
        controller.acceptLocationFix(FlightLocationFix(10.0, 104.0, 3_000_000_000L))

        assertEquals(null, (states.last() as FlightParametersState.Readings).verticalSpeedMetresPerSecond)
    }

    @Test fun `accepted fixes are forwarded before readings are derived`() {
        controller.start()
        val fix = FlightLocationFix(null, null, 1_000_000_000L, 45.0, 52.0, 21.0)

        controller.acceptLocationFix(fix)

        // Forwarded even though it has no displayable reading (course, nearby city, route, map use it).
        assertEquals(listOf(fix), forwardedFixes)
    }
}
