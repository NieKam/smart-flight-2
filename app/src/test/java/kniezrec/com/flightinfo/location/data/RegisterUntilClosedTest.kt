package kniezrec.com.flightinfo.location.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterUntilClosedTest {
    private var registerCount = 0
    private var unregisterCount = 0

    @Test
    fun `registration lasts until the collection is cancelled`() =
        runTest {
            val job = backgroundScope.launch { registration { true }.collect() }
            runCurrent()

            assertEquals(1, registerCount)
            assertEquals(0, unregisterCount)

            job.cancel()
            runCurrent()
            assertEquals(1, unregisterCount)
        }

    @Test
    fun `refused registration is released and ends the flow with a typed exception`() =
        runTest {
            val failure = runCatching { registration { false }.collect() }.exceptionOrNull()

            assertTrue(failure is LocationRegistrationException)
            assertEquals(1, unregisterCount)
        }

    @Test
    fun `throwing registration is released and ends the flow with a typed exception`() =
        runTest {
            val cause = SecurityException("permission revoked")

            val failure = runCatching { registration { throw cause }.collect() }.exceptionOrNull()

            assertTrue(failure is LocationRegistrationException)
            assertEquals("test registration failed", failure?.message)
            // Coroutine stack-trace recovery may rethrow a copy whose cause is the original
            // LocationRegistrationException, so the SecurityException can sit one level deeper.
            assertTrue(generateSequence(failure) { it.cause }.any { it === cause })
            assertEquals(1, unregisterCount)
        }

    @Test
    fun `failing unregistration does not replace the registration failure`() =
        runTest {
            val failure =
                runCatching {
                    callbackFlow<Unit> {
                        registerUntilClosed(
                            description = "test",
                            register = { false },
                            unregister = { throw IllegalStateException("not registered") },
                        )
                    }.collect()
                }.exceptionOrNull()

            assertTrue(failure is LocationRegistrationException)
        }

    private fun registration(register: () -> Boolean): Flow<Unit> =
        callbackFlow {
            registerUntilClosed(
                description = "test",
                register = {
                    registerCount++
                    register()
                },
                unregister = { unregisterCount++ },
            )
        }
}
