package kniezrec.com.flightinfo.testutil

import kniezrec.com.flightinfo.orientation.OrientationSample
import kniezrec.com.flightinfo.orientation.data.OrientationDataSource
import kniezrec.com.flightinfo.orientation.data.OrientationRegistrationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * In-memory [OrientationDataSource]: every collection registers a listener, cancelling it
 * unregisters. [all] keeps released listeners so tests can fire late callbacks.
 */
class FakeOrientationDataSource(
    var available: Boolean = true,
) : OrientationDataSource {
    var failRegistration = false

    val all = mutableListOf<(OrientationSample) -> Unit>()
    private val active = mutableListOf<(OrientationSample) -> Unit>()

    var registerCount = 0
        private set

    val activeCount: Int get() = active.size

    override fun isAvailable(): Boolean = available

    override val samples: Flow<OrientationSample> =
        callbackFlow {
            registerCount++
            if (failRegistration) throw OrientationRegistrationException("fake registration refused")
            val sender: (OrientationSample) -> Unit = { trySend(it) }
            all += sender
            active += sender
            awaitClose { active -= sender }
        }

    fun emit(
        headingDegrees: Double = 0.0,
        pitchDegrees: Double = 0.0,
        rollDegrees: Double = 0.0,
    ) {
        val sample = OrientationSample(headingDegrees, pitchDegrees, rollDegrees)
        active.toList().forEach { it(sample) }
    }
}
