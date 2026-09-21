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

/** Pure display-coordinate conversion kept separate from the Android sensor callback. */
internal enum class DisplayRotation {
    Portrait,
    Landscape,
    ReversePortrait,
    ReverseLandscape,
    ;

    companion object {
        fun fromSurfaceRotation(rotation: Int): DisplayRotation =
            when (rotation) {
                Surface.ROTATION_90 -> Landscape
                Surface.ROTATION_180 -> ReversePortrait
                Surface.ROTATION_270 -> ReverseLandscape
                else -> Portrait
            }
    }
}

internal object DisplayRelativeOrientation {
    fun calculate(rotationMatrix: FloatArray, rotation: DisplayRotation): OrientationSample? {
        if (rotationMatrix.size < 9 || rotationMatrix.take(9).any { !it.isFinite() }) return null
        val (xAxis, yAxis) =
            when (rotation) {
                DisplayRotation.Portrait -> 1 to 2
                DisplayRotation.Landscape -> 2 to -1
                DisplayRotation.ReversePortrait -> -1 to -2
                DisplayRotation.ReverseLandscape -> -2 to 1
            }
        val remapped = FloatArray(9)
        for (row in 0..2) {
            remapped[row * 3] = rotationMatrix.axisValue(row, xAxis)
            remapped[row * 3 + 1] = rotationMatrix.axisValue(row, yAxis)
            remapped[row * 3 + 2] = rotationMatrix[row * 3 + 2]
        }
        val heading = Math.toDegrees(kotlin.math.atan2(remapped[1].toDouble(), remapped[4].toDouble()))
        val pitch = Math.toDegrees(kotlin.math.asin(-remapped[7].toDouble()))
        val roll = Math.toDegrees(kotlin.math.atan2(-remapped[6].toDouble(), remapped[8].toDouble()))
        if (!heading.isFinite() || !pitch.isFinite() || !roll.isFinite()) return null
        return OrientationSample(
            headingDegrees = if (heading < 0) heading + 360 else heading,
            pitchDegrees = pitch,
            rollDegrees = roll,
        )
    }

    private fun FloatArray.axisValue(row: Int, axis: Int): Float {
        val column = kotlin.math.abs(axis) - 1
        val value = this[row * 3 + column]
        return if (axis < 0) -value else value
    }
}

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
                    val sample =
                        DisplayRelativeOrientation.calculate(
                            rotationMatrix = matrix,
                            rotation = DisplayRotation.fromSurfaceRotation(display?.rotation ?: Surface.ROTATION_0),
                        ) ?: return
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
