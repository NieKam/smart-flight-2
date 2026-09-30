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
import org.junit.Before
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

    @Before
    fun letRegistrationsSucceed() {
        // The flag is static in ShadowSensorManager; do not depend on its reset between tests.
        shadowSensorManager.setForceListenersToFail(false)
    }

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

            assertEquals(2, samples.size)
            assertEquals(0.0, samples[0].headingDegrees, 0.001)
            assertEquals(180.0, samples[1].headingDegrees, 0.001)
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
    fun `without any orientation sensor it is unavailable and collection fails`() =
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

    @Test
    fun `without a rotation vector, accelerometer and magnetometer give samples`() =
        runTest {
            val accelerometer = addSensor(Sensor.TYPE_ACCELEROMETER)
            val magnetometer = addSensor(Sensor.TYPE_MAGNETIC_FIELD)
            val dataSource = dataSource()
            assertTrue(dataSource.isAvailable())
            val samples = mutableListOf<OrientationSample>()
            backgroundScope.launch { dataSource.samples.toList(samples) }
            runCurrent()
            assertEquals(1, shadowSensorManager.listeners.size)

            // Gravity alone gives no rotation matrix yet.
            send(accelerometer, floatArrayOf(0f, 0f, 9.81f))
            runCurrent()
            assertTrue(samples.isEmpty())

            // Flat, magnetic north along the device's y axis (pointing down into the ground): heading 0.
            send(magnetometer, floatArrayOf(0f, 22f, -42f))
            runCurrent()
            val sample = samples.single()
            assertEquals(0.0, sample.headingDegrees.let { if (it > 180) it - 360 else it }, 0.5)
            assertEquals(0.0, sample.pitchDegrees, 0.5)
            assertEquals(0.0, sample.rollDegrees, 0.5)
        }

    @Test
    fun `an accelerometer alone is not enough`() =
        runTest {
            addSensor(Sensor.TYPE_ACCELEROMETER)
            val dataSource = dataSource()
            assertFalse(dataSource.isAvailable())

            val failure = backgroundScope.async { runCatching { dataSource.samples.first() }.exceptionOrNull() }
            runCurrent()
            assertTrue(failure.await() is OrientationRegistrationException)
        }

    @Test
    fun `with a rotation vector the fallback sensors are not used`() =
        runTest {
            val rotationVector = addRotationVectorSensor()
            val accelerometer = addSensor(Sensor.TYPE_ACCELEROMETER)
            val magnetometer = addSensor(Sensor.TYPE_MAGNETIC_FIELD)
            val dataSource = dataSource()
            backgroundScope.launch { dataSource.samples.collect {} }
            runCurrent()

            val listener = shadowSensorManager.listeners.single()
            assertTrue(shadowSensorManager.hasListener(listener, rotationVector))
            assertFalse(shadowSensorManager.hasListener(listener, accelerometer))
            assertFalse(shadowSensorManager.hasListener(listener, magnetometer))
        }

    private fun addSensor(type: Int): Sensor = ShadowSensor.newInstance(type).also(shadowSensorManager::addSensor)

    private fun send(
        sensor: Sensor,
        values: FloatArray,
    ) {
        shadowSensorManager.sendSensorEventToListeners(SensorEventBuilder.newBuilder(sensor, values).setTimestamp(1L).build(), sensor)
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
