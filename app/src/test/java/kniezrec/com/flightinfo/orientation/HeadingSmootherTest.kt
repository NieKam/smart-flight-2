package kniezrec.com.flightinfo.orientation

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class HeadingSmootherTest {
    @Test fun `the first heading is returned as it is`() {
        assertEquals(123.0, HeadingSmoother().add(123.0), 1e-9)
    }

    @Test fun `headings across north average to north, not south`() {
        val smoother = HeadingSmoother()
        smoother.add(359.0)
        val average = smoother.add(1.0)

        assertEquals(0.0, if (average > 180) average - 360 else average, 1e-9)
    }

    @Test fun `the average is in 0 to 360`() {
        val smoother = HeadingSmoother()
        smoother.add(350.0)
        assertEquals(345.0, smoother.add(340.0), 1e-9)
    }

    @Test fun `only the last window headings count`() {
        val smoother = HeadingSmoother(window = 3)
        smoother.add(10.0)
        smoother.add(90.0)
        smoother.add(90.0)
        assertEquals(90.0, smoother.add(90.0), 1e-9)
    }

    @Test fun `opposite headings have no average and return the newest heading`() {
        val smoother = HeadingSmoother()
        smoother.add(0.0)
        assertEquals(180.0, smoother.add(180.0), 1e-9)
    }

    @Test fun `low pass starts from the first vector and then keeps 97 percent of the previous one`() {
        val first = lowPass(null, floatArrayOf(1f, 2f, 3f))
        assertArrayEquals(floatArrayOf(1f, 2f, 3f), first, 0f)

        val next = lowPass(first, floatArrayOf(11f, 2f, -97f))
        assertArrayEquals(floatArrayOf(1.3f, 2f, 0f), next, 1e-5f)
    }
}
