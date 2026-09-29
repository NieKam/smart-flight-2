package kniezrec.com.flightinfo.flight.data

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

/** Platform boundary for the optional barometer. */
interface PressureDataSource {
    fun hasPressureSensor(): Boolean

    /**
     * Valid pressure readings in millibars (hPa). Cold: each collection registers its own sensor
     * listener, unregistered when the collection ends. Without a sensor, or when the platform
     * refuses the registration, the flow completes without values (the reading stays unavailable).
     */
    fun pressureMillibars(): Flow<Double>
}

/** [PressureDataSource] over [SensorManager] `TYPE_PRESSURE`; events arrive on the main thread. */
internal class AndroidPressureDataSource
    @Inject
    constructor(
        private val sensorManager: SensorManager,
    ) : PressureDataSource {
        override fun hasPressureSensor(): Boolean = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null

        override fun pressureMillibars(): Flow<Double> =
            callbackFlow {
                val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
                if (sensor == null) {
                    close()
                    return@callbackFlow
                }
                val listener =
                    object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent) {
                            event.values
                                .firstOrNull()
                                ?.let(::validPressureMillibars)
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
                    } catch (_: RuntimeException) {
                        false
                    }
                if (!registered) {
                    close()
                    return@callbackFlow
                }
                awaitClose { sensorManager.unregisterListener(listener) }
            }
    }

/** A barometer value in millibars, or null when it is not a usable pressure (non-finite or negative). */
internal fun validPressureMillibars(value: Float): Double? = value.toDouble().takeIf { it.isFinite() && it >= 0.0 }
