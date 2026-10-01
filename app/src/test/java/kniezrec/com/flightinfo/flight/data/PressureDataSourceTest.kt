package kniezrec.com.flightinfo.flight.data

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager

/** [AndroidPressureDataSource] against Robolectric's [ShadowSensorManager]. */
@RunWith(AndroidJUnit4::class)
class PressureDataSourceTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val sensorManager: SensorManager = application.getSystemService(SensorManager::class.java)
    private val shadowSensorManager: ShadowSensorManager = shadowOf(sensorManager)
    private val dataSource = AndroidPressureDataSource(sensorManager)

    @Test
    fun `collection registers a pressure listener and cancelling unregisters it`() =
        runTest {
            val sensor = addPressureSensor()
            assertTrue(dataSource.hasPressureSensor())

            val job = backgroundScope.launch { dataSource.pressureMillibars().toList(mutableListOf()) }
            runCurrent()
            assertEquals(1, shadowSensorManager.listeners.size)
            assertTrue(shadowSensorManager.hasListener(shadowSensorManager.listeners.single(), sensor))

            job.cancel()
            runCurrent()
            assertTrue(shadowSensorManager.listeners.isEmpty())
        }

    @Test
    fun `valid pressure is delivered and invalid values are ignored`() =
        runTest {
            val sensor = addPressureSensor()
            val values = mutableListOf<Double>()
            backgroundScope.launch { dataSource.pressureMillibars().toList(values) }
            runCurrent()

            listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 1013.25f, Float.NEGATIVE_INFINITY, -2f, 0f).forEach { value ->
                shadowSensorManager.sendSensorEventToListeners(
                    SensorEventBuilder.newBuilder(sensor, floatArrayOf(value)).setTimestamp(1L).build(),
                    sensor,
                )
            }
            runCurrent()

            assertEquals(listOf(1013.25, 0.0), values)
        }

    @Test
    fun `without a pressure sensor the flow completes without values`() =
        runTest {
            assertFalse(dataSource.hasPressureSensor())

            assertEquals(emptyList<Double>(), dataSource.pressureMillibars().toList())
            assertTrue(shadowSensorManager.listeners.isEmpty())
        }

    @Test
    fun `a refused registration completes without values`() =
        runTest {
            addPressureSensor()
            shadowSensorManager.setForceListenersToFail(true)

            assertEquals(emptyList<Double>(), dataSource.pressureMillibars().toList())
            assertTrue(shadowSensorManager.listeners.isEmpty())
        }

    @Test
    fun `a value sent after the collection ended is not delivered`() =
        runTest {
            val sensor = addPressureSensor()
            val values = mutableListOf<Double>()
            val job = backgroundScope.launch { dataSource.pressureMillibars().toList(values) }
            runCurrent()
            val oldListener = shadowSensorManager.listeners.single()
            job.cancel()
            runCurrent()

            oldListener.onSensorChanged(SensorEventBuilder.newBuilder(sensor, floatArrayOf(900f)).build())
            runCurrent()

            assertTrue(values.isEmpty())
        }

    private fun addPressureSensor(): Sensor = ShadowSensor.newInstance(Sensor.TYPE_PRESSURE).also(shadowSensorManager::addSensor)
}
