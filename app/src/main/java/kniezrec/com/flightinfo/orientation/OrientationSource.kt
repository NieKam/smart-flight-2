package kniezrec.com.flightinfo.orientation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import java.util.concurrent.Executor

internal data class OrientationSample(
    val headingDegrees: Double,
    val pitchDegrees: Double,
    val rollDegrees: Double,
)

/** A shared, display-relative orientation source. It owns at most one sensor listener. */
internal interface OrientationSource {
    fun isAvailable(): Boolean

    fun addListener(listener: (OrientationSample) -> Unit): Boolean

    fun removeListener(listener: (OrientationSample) -> Unit)
}

/**
 * Separates an Android sensor callback's registration from its main-thread delivery.
 *
 * A callback can already be queued when a sensor listener is unregistered. Capturing both the
 * listener set and registration generation prevents that callback from reaching a replacement
 * Course or Horizon session.
 */
internal class OrientationEventDispatcher {
    private var activeGeneration = 0L

    @Synchronized
    fun beginRegistration(): Long = ++activeGeneration

    @Synchronized
    fun invalidateRegistration() {
        ++activeGeneration
    }

    @Synchronized
    fun capture(
        generation: Long,
        listeners: Collection<(OrientationSample) -> Unit>,
        sample: OrientationSample,
    ): CapturedOrientationEvent? =
        if (generation == activeGeneration) {
            CapturedOrientationEvent(generation, listeners.toList(), sample)
        } else {
            null
        }

    @Synchronized
    fun isCurrent(event: CapturedOrientationEvent): Boolean = event.generation == activeGeneration
}

internal data class CapturedOrientationEvent(
    val generation: Long,
    val listeners: List<(OrientationSample) -> Unit>,
    val sample: OrientationSample,
)


internal class AndroidOrientationSource(
    context: Context,
    private val callbackExecutor: Executor,
) : OrientationSource {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val display = context.display
    private val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val listeners = linkedSetOf<(OrientationSample) -> Unit>()
    private val eventDispatcher = OrientationEventDispatcher()
    private var sensorListener: SensorEventListener? = null

    override fun isAvailable(): Boolean = rotationVector != null

    override fun addListener(listener: (OrientationSample) -> Unit): Boolean {
        if (listeners.contains(listener)) return true
        if (listeners.isEmpty() && !registerSensorListener()) return false
        listeners += listener
        return true
    }

    override fun removeListener(listener: (OrientationSample) -> Unit) {
        listeners -= listener
        if (listeners.isEmpty()) {
            sensorListener?.let(sensorManager::unregisterListener)
            sensorListener = null
            eventDispatcher.invalidateRegistration()
        }
    }

    private fun registerSensorListener(): Boolean {
        val sensor = rotationVector ?: return false
        val generation = eventDispatcher.beginRegistration()
        val newListener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val matrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(matrix, event.values)
                    val adjusted = FloatArray(9)
                    val axes =
                        when (display?.rotation ?: Surface.ROTATION_0) {
                            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                        }
                    if (!SensorManager.remapCoordinateSystem(matrix, axes.first, axes.second, adjusted)) return
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(adjusted, orientation)
                    val sample =
                        OrientationSample(
                            headingDegrees = Math.toDegrees(orientation[0].toDouble()).let { if (it < 0) it + 360 else it },
                            pitchDegrees = Math.toDegrees(orientation[1].toDouble()),
                            rollDegrees = Math.toDegrees(orientation[2].toDouble()),
                        )
                    if (!sample.headingDegrees.isFinite() || !sample.pitchDegrees.isFinite() || !sample.rollDegrees.isFinite()) return
                    val captured = eventDispatcher.capture(generation, listeners, sample) ?: return
                    callbackExecutor.execute {
                        if (eventDispatcher.isCurrent(captured)) {
                            captured.listeners.forEach { it(captured.sample) }
                        }
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
        val registered = sensorManager.registerListener(newListener, sensor, SensorManager.SENSOR_DELAY_UI)
        if (registered) {
            sensorListener = newListener
        } else {
            eventDispatcher.invalidateRegistration()
        }
        return registered
    }
}
