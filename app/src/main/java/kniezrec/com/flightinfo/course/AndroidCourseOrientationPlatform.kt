package kniezrec.com.flightinfo.course

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import java.util.concurrent.Executor

internal class AndroidCourseOrientationPlatform(
    context: Context,
    private val callbackExecutor: Executor,
) : CourseOrientationPlatform {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val display = context.display
    private val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private var listener: SensorEventListener? = null

    override fun isOrientationAvailable(): Boolean = rotationVector != null

    override fun registerOrientationListener(onHeading: (Double) -> Unit): Boolean {
        val sensor = rotationVector ?: return false
        val newListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val matrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                val adjusted = FloatArray(9)
                val axes = when (display?.rotation ?: Surface.ROTATION_0) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(matrix, axes.first, axes.second, adjusted)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(adjusted, orientation)
                onHeading(Math.toDegrees(orientation[0].toDouble()).let { if (it < 0) it + 360 else it })
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listener = newListener
        return sensorManager.registerListener(newListener, sensor, SensorManager.SENSOR_DELAY_UI, callbackExecutor)
    }

    override fun unregisterOrientationListener() {
        listener?.let(sensorManager::unregisterListener)
        listener = null
    }
}
