package kniezrec.com.flightinfo.orientation.data

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.orientation.DisplayRotation
import kniezrec.com.flightinfo.orientation.OrientationSample
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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

/** [AndroidOrientationDataSource] against Robolectric's [ShadowSensorManager]. */
@RunWith(AndroidJUnit4::class)
class OrientationDataSourceTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val sensorManager: SensorManager = application.getSystemService(SensorManager::class.java)
    private val shadowSensorManager: ShadowSensorManager = shadowOf(sensorManager)
    private val displayRotation = FakeDisplayRotationProvider()

    @Test
    fun `two collectors share one sensor listener, released when both stop`() =
        runTest {
            val sensor = addRotationVectorSensor()
            val dataSource = dataSource()
            assertTrue(dataSource.isAvailable())

            val course = backgroundScope.launch { dataSource.samples.collect {} }
            val horizon = backgroundScope.launch { dataSource.samples.collect {} }
            runCurrent()
            assertEquals(1, shadowSensorManager.listeners.size)
            assertTrue(shadowSensorManager.hasListener(shadowSensorManager.listeners.single(), sensor))

            course.cancel()
            runCurrent()
            assertEquals(1, shadowSensorManager.listeners.size)

            horizon.cancel()
            runCurrent()
            assertTrue(shadowSensorManager.listeners.isEmpty())
        }

    @Test
    fun `nothing is registered before a collector starts`() =
        runTest {
            addRotationVectorSensor()
            dataSource()
            runCurrent()

            assertTrue(shadowSensorManager.listeners.isEmpty())
        }

    @Test
    fun `every collector receives the same samples`() =
        runTest {
            val sensor = addRotationVectorSensor()
            val dataSource = dataSource()
            val first = mutableListOf<OrientationSample>()
            val second = mutableListOf<OrientationSample>()
            backgroundScope.launch { dataSource.samples.toList(first) }
            backgroundScope.launch { dataSource.samples.toList(second) }
            runCurrent()

            sendIdentityRotation(sensor)
            runCurrent()

            assertEquals(1, first.size)
            assertEquals(first, second)
        }

    @Test
    fun `the display rotation is read for every sample`() =
        runTest {
            val sensor = addRotationVectorSensor()
            val dataSource = dataSource()
            val samples = mutableListOf<OrientationSample>()
            backgroundScope.launch { dataSource.samples.toList(samples) }
            runCurrent()

            // Device flat with its top edge to the north.
            sendIdentityRotation(sensor)
            runCurrent()
            displayRotation.rotation = DisplayRotation.ReversePortrait
            sendIdentityRotation(sensor)
            runCurrent()

            assertEquals(listOf(0.0, 180.0), samples.map { it.headingDegrees })
        }

    @Test
    fun `a sample sent after the collection ended is not delivered`() =
        runTest {
            val sensor = addRotationVectorSensor()
            val dataSource = dataSource()
            val samples = mutableListOf<OrientationSample>()
            val job = backgroundScope.launch { dataSource.samples.toList(samples) }
            runCurrent()
            val oldListener = shadowSensorManager.listeners.single()
            job.cancel()
            runCurrent()

            oldListener.onSensorChanged(SensorEventBuilder.newBuilder(sensor, IDENTITY_ROTATION_VECTOR).build())
            runCurrent()

            assertTrue(samples.isEmpty())
        }

    @Test
    fun `without a rotation-vector sensor it is unavailable and collection fails`() =
        runTest {
            val dataSource = dataSource()
            assertFalse(dataSource.isAvailable())

            val failure = backgroundScope.async { runCatching { dataSource.samples.first() }.exceptionOrNull() }
            runCurrent()

            assertTrue(failure.await() is OrientationRegistrationException)
            assertTrue(shadowSensorManager.listeners.isEmpty())
        }

    @Test
    fun `a refused registration fails every collector, and a later collection registers again`() =
        runTest {
            addRotationVectorSensor()
            val dataSource = dataSource()
            shadowSensorManager.setForceListenersToFail(true)

            val course = backgroundScope.async { runCatching { dataSource.samples.first() }.exceptionOrNull() }
            val horizon = backgroundScope.async { runCatching { dataSource.samples.first() }.exceptionOrNull() }
            runCurrent()
            assertTrue(course.await() is OrientationRegistrationException)
            assertTrue(horizon.await() is OrientationRegistrationException)
            assertTrue(shadowSensorManager.listeners.isEmpty())

            shadowSensorManager.setForceListenersToFail(false)
            backgroundScope.launch { dataSource.samples.collect {} }
            runCurrent()
            assertEquals(1, shadowSensorManager.listeners.size)
        }

    private fun TestScope.dataSource() = AndroidOrientationDataSource(sensorManager, displayRotation, backgroundScope)

    private fun addRotationVectorSensor(): Sensor =
        ShadowSensor.newInstance(Sensor.TYPE_ROTATION_VECTOR).also(shadowSensorManager::addSensor)

    private fun sendIdentityRotation(sensor: Sensor) {
        shadowSensorManager.sendSensorEventToListeners(
            SensorEventBuilder.newBuilder(sensor, IDENTITY_ROTATION_VECTOR).setTimestamp(1L).build(),
            sensor,
        )
    }

    private class FakeDisplayRotationProvider : DisplayRotationProvider {
        var rotation = DisplayRotation.Portrait

        override fun current(): DisplayRotation = rotation
    }

    private companion object {
        /** Unit quaternion (x, y, z, w) of no rotation: device axes equal world axes. */
        val IDENTITY_ROTATION_VECTOR = floatArrayOf(0f, 0f, 0f, 1f)
    }
}
