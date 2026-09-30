package kniezrec.com.flightinfo.orientation.data

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kniezrec.com.flightinfo.data.shareRethrowingIn
import kniezrec.com.flightinfo.di.ApplicationScope
import kniezrec.com.flightinfo.orientation.DisplayRelativeOrientation
import kniezrec.com.flightinfo.orientation.OrientationSample
import kniezrec.com.flightinfo.orientation.lowPass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Display-relative device orientation from the rotation-vector sensor, or, on devices without one,
 * from the accelerometer and the magnetometer (as the original compass).
 */
interface OrientationDataSource {
    /** Whether the device has a rotation-vector sensor, or an accelerometer and a magnetometer. */
    fun isAvailable(): Boolean

    /**
     * Orientation samples, shared: one sensor registration however many collectors there are,
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
 * [OrientationDataSource] over `TYPE_ROTATION_VECTOR` when the device has it; otherwise over
 * `TYPE_ACCELEROMETER` + `TYPE_MAGNETIC_FIELD` (low-pass filtered as the original, then
 * `SensorManager.getRotationMatrix`). Both paths go through the same [DisplayRelativeOrientation],
 * so Course and Horizon work on either. Events arrive on the main thread; the display rotation is
 * read for every event.
 */
@Singleton
internal class AndroidOrientationDataSource
    @Inject
    constructor(
        private val sensorManager: SensorManager,
        private val displayRotationProvider: DisplayRotationProvider,
        @ApplicationScope scope: CoroutineScope,
    ) : OrientationDataSource {
        override fun isAvailable(): Boolean = rotationVectorSensor() != null || magneticSensors() != null

        override val samples: Flow<OrientationSample> =
            callbackFlow {
                val rotationVector = rotationVectorSensor()
                val listener: SensorEventListener
                val sensors: List<Sensor>
                if (rotationVector != null) {
                    listener =
                        orientationListener { event ->
                            FloatArray(9).also { SensorManager.getRotationMatrixFromVector(it, event.values) }
                        }
                    sensors = listOf(rotationVector)
                } else {
                    val (accelerometer, magnetometer) =
                        magneticSensors() ?: throw OrientationRegistrationException("No orientation sensor")
                    var gravity: FloatArray? = null
                    var geomagnetic: FloatArray? = null
                    listener =
                        orientationListener { event ->
                            when (event.sensor.type) {
                                Sensor.TYPE_ACCELEROMETER -> gravity = lowPass(gravity, event.values)
                                Sensor.TYPE_MAGNETIC_FIELD -> geomagnetic = lowPass(geomagnetic, event.values)
                            }
                            val currentGravity = gravity ?: return@orientationListener null
                            val currentGeomagnetic = geomagnetic ?: return@orientationListener null
                            FloatArray(9).takeIf { SensorManager.getRotationMatrix(it, null, currentGravity, currentGeomagnetic) }
                        }
                    sensors = listOf(accelerometer, magnetometer)
                }
                val registered =
                    try {
                        sensors.all { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
                    } catch (exception: RuntimeException) {
                        runCatching { sensorManager.unregisterListener(listener) }
                        throw OrientationRegistrationException("Orientation sensor registration failed", exception)
                    }
                if (!registered) {
                    runCatching { sensorManager.unregisterListener(listener) }
                    throw OrientationRegistrationException("Orientation sensor registration was refused")
                }
                awaitClose { sensorManager.unregisterListener(listener) }
            }.shareRethrowingIn(scope, replay = 0)

        /** A listener that sends the orientation of the rotation matrix [rotationMatrix] makes of an event, if any. */
        private fun ProducerScope<OrientationSample>.orientationListener(rotationMatrix: (SensorEvent) -> FloatArray?) =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val matrix = rotationMatrix(event) ?: return
                    DisplayRelativeOrientation
                        .calculate(matrix, displayRotationProvider.current())
                        ?.let { trySend(it) }
                }

                override fun onAccuracyChanged(
                    sensor: Sensor?,
                    accuracy: Int,
                ) = Unit
            }

        private fun rotationVectorSensor(): Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        /** Accelerometer and magnetometer, only when the device has both. */
        private fun magneticSensors(): Pair<Sensor, Sensor>? {
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return null
            val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) ?: return null
            return accelerometer to magnetometer
        }
    }
