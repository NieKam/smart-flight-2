package kniezrec.com.flightinfo.flight

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import java.util.concurrent.Executor

internal class AndroidPressurePlatform(
    private val sensorManager: SensorManager,
    private val callbackExecutor: Executor,
) : PressurePlatform {
    private var listener: SensorEventListener? = null

    override fun hasPressureSensor(): Boolean = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null

    override fun registerPressureListener(onPressureMillibars: (Float) -> Unit): Boolean {
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return false
        val newListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                event.values.firstOrNull()?.let(onPressureMillibars)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listener = newListener
        return try {
            sensorManager.registerListener(newListener, sensor, SensorManager.SENSOR_DELAY_UI, callbackExecutor)
        } catch (_: RuntimeException) {
            listener = null
            false
        }
    }

    override fun unregisterPressureListener() {
        listener?.let(sensorManager::unregisterListener)
        listener = null
    }
}
