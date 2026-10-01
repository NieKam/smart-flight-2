package kniezrec.com.flightinfo.course

import org.junit.Assert.assertEquals
import org.junit.Test

class CompassRotationTest {
    @Test fun `turns the short way across north`() {
        assertEquals(-10f, shortestRotationTarget(0f, 350f), 1e-4f)
        assertEquals(370f, shortestRotationTarget(350f, 10f), 1e-4f)
    }

    @Test fun `ordinary turns go straight to the heading`() {
        assertEquals(90f, shortestRotationTarget(0f, 90f), 1e-4f)
        assertEquals(10f, shortestRotationTarget(100f, 10f), 1e-4f)
    }

    @Test fun `repeated wraps accumulate from an unwrapped angle`() {
        var angle = 0f
        listOf(90f, 180f, 270f, 0f, 90f).forEach { angle = shortestRotationTarget(angle, it) }
        assertEquals(450f, angle, 1e-4f)

        listOf(0f, 270f, 180f, 90f, 0f, 270f).forEach { angle = shortestRotationTarget(angle, it) }
        assertEquals(-90f, angle, 1e-4f)
    }

    @Test fun `the result always points at the heading`() {
        listOf(-725f, -10f, 0f, 359f, 1_000f).forEach { current ->
            listOf(0f, 1f, 179f, 181f, 359f).forEach { heading ->
                val target = shortestRotationTarget(current, heading)
                assertEquals(heading, ((target % 360f) + 360f) % 360f, 1e-3f)
                assert(kotlin.math.abs(target - current) <= 180f)
            }
        }
    }
}
