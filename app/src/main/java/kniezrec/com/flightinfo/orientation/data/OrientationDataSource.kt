package kniezrec.com.flightinfo.orientation.data

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kniezrec.com.flightinfo.data.shareRethrowingIn
import kniezrec.com.flightinfo.di.ApplicationScope
import kniezrec.com.flightinfo.orientation.DisplayRelativeOrientation
import kniezrec.com.flightinfo.orientation.OrientationSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Display-relative device orientation from the rotation-vector sensor. */
interface OrientationDataSource {
    /** Whether the device has a rotation-vector sensor. */
    fun isAvailable(): Boolean

    /**
     * Orientation samples, shared: one sensor listener however many collectors there are,
     * registered when the first collector starts and unregistered as soon as the last one stops.
     * Nothing is replayed. Collection ends with [OrientationRegistrationException] when there is
     * no sensor or the platform refuses the registration; the next collection after all
     * collectors have stopped registers again.
     */
    val samples: Flow<OrientationSample>
}

/** The platform refused or failed to register the orientation sensor listener. */
class OrientationRegistrationException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * [OrientationDataSource] over `TYPE_ROTATION_VECTOR`. Events arrive on the main thread; the
 * display rotation is read for every event.
 */
@Singleton
internal class AndroidOrientationDataSource
    @Inject
    constructor(
        private val sensorManager: SensorManager,
        private val displayRotationProvider: DisplayRotationProvider,
        @ApplicationScope scope: CoroutineScope,
    ) : OrientationDataSource {
        override fun isAvailable(): Boolean = rotationVectorSensor() != null

        override val samples: Flow<OrientationSample> =
            callbackFlow {
                val sensor =
                    rotationVectorSensor() ?: throw OrientationRegistrationException("No rotation-vector sensor")
                val listener =
                    object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent) {
                            val matrix = FloatArray(9)
                            SensorManager.getRotationMatrixFromVector(matrix, event.values)
                            DisplayRelativeOrientation
                                .calculate(matrix, displayRotationProvider.current())
                                ?.let { trySend(it) }
                        }

                        override fun onAccuracyChanged(
                            sensor: Sensor?,
                            accuracy: Int,
                        ) = Unit
                    }
                val registered =
                    try {
                        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                    } catch (exception: RuntimeException) {
                        runCatching { sensorManager.unregisterListener(listener) }
                        throw OrientationRegistrationException("Rotation-vector registration failed", exception)
                    }
                if (!registered) {
                    runCatching { sensorManager.unregisterListener(listener) }
                    throw OrientationRegistrationException("Rotation-vector registration was refused")
                }
                awaitClose { sensorManager.unregisterListener(listener) }
            }.shareRethrowingIn(scope, replay = 0)

        private fun rotationVectorSensor(): Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }
