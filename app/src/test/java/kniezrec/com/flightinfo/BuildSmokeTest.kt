package kniezrec.com.flightinfo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Proves that kotlinx-coroutines-test is on the unit-test classpath and virtual time works. */
@OptIn(ExperimentalCoroutinesApi::class)
class BuildSmokeTest {
    @Test
    fun runTest_withStandardTestDispatcher_advancesVirtualTime() =
        runTest(StandardTestDispatcher()) {
            var completed = false

            launch {
                delay(60_000)
                completed = true
            }
            advanceUntilIdle()

            assertEquals(true, completed)
            assertEquals(60_000L, testScheduler.currentTime)
        }
}
