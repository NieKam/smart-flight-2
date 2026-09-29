package kniezrec.com.flightinfo.testutil

import kniezrec.com.flightinfo.flight.data.PressureDataSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** In-memory [PressureDataSource]: every collection registers a listener, cancelling it unregisters. */
class FakePressureDataSource(
    private val hasSensor: Boolean = true,
) : PressureDataSource {
    private val active = mutableListOf<(Double) -> Unit>()

    var registerCount = 0
        private set

    val activeCount: Int get() = active.size

    override fun hasPressureSensor(): Boolean = hasSensor

    override fun pressureMillibars(): Flow<Double> =
        callbackFlow {
            registerCount++
            val sender: (Double) -> Unit = { trySend(it) }
            active += sender
            awaitClose { active -= sender }
        }

    fun emit(millibars: Double) = active.toList().forEach { it(millibars) }
}
