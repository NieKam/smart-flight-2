package kniezrec.com.flightinfo.location.data

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.gnss.GnssSatellite
import kniezrec.com.flightinfo.testutil.idleMainLooper
import kotlinx.coroutines.flow.collect
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
import org.robolectric.shadows.ShadowLocationManager

/** [AndroidLocationDataSource] against Robolectric's [ShadowLocationManager]. */
@Suppress("DEPRECATION") // getLocationUpdateListeners is the only way to count registrations.
@RunWith(AndroidJUnit4::class)
class AndroidLocationDataSourceTest {
    private val application: Application = ApplicationProvider.getApplicationContext()
    private val locationManager: LocationManager = application.getSystemService(LocationManager::class.java)
    private val shadowLocationManager: ShadowLocationManager = shadowOf(locationManager)
    private val dataSource = AndroidLocationDataSource(application, locationManager, application.packageManager)

    @Test
    fun `fix collection registers one one-second gps request and unregisters on cancel`() =
        runTest {
            val job = backgroundScope.launch { dataSource.locationFixes().collect() }
            runCurrent()

            assertEquals(1, shadowLocationManager.getLocationUpdateListeners(LocationManager.GPS_PROVIDER).size)
            assertEquals(
                GPS_UPDATE_INTERVAL_MILLIS,
                shadowLocationManager.getLocationRequests(LocationManager.GPS_PROVIDER).single().intervalMillis,
            )

            job.cancel()
            runCurrent()
            assertTrue(shadowLocationManager.locationUpdateListeners.isEmpty())
        }

    @Test
    fun `gps locations are mapped to flight fixes`() =
        runTest {
            val fixes = mutableListOf<FlightLocationFix>()
            backgroundScope.launch { dataSource.locationFixes().toList(fixes) }
            runCurrent()

            shadowLocationManager.simulateLocation(
                Location(LocationManager.GPS_PROVIDER).apply {
                    latitude = 52.2
                    longitude = 21.0
                    speed = 10f
                    altitude = 100.0
                    bearing = 90f
                    time = 1_000L
                    elapsedRealtimeNanos = 1_000_000_000L
                },
            )
            deliverCallbacks()

            assertEquals(
                listOf(
                    FlightLocationFix(
                        speedMetresPerSecond = 10.0,
                        altitudeMetres = 100.0,
                        elapsedRealtimeNanos = 1_000_000_000L,
                        bearingDegrees = 90.0,
                        latitude = 52.2,
                        longitude = 21.0,
                    ),
                ),
                fixes,
            )
        }

    @Test
    fun `missing speed altitude and bearing are mapped to null`() =
        runTest {
            val fixes = mutableListOf<FlightLocationFix>()
            backgroundScope.launch { dataSource.locationFixes().toList(fixes) }
            runCurrent()

            shadowLocationManager.simulateLocation(
                Location(LocationManager.GPS_PROVIDER).apply {
                    latitude = 1.0
                    longitude = 2.0
                    time = 1_000L
                    elapsedRealtimeNanos = 2_000_000_000L
                },
            )
            deliverCallbacks()

            assertEquals(listOf(FlightLocationFix(null, null, 2_000_000_000L, null, 1.0, 2.0)), fixes)
        }

    @Test
    fun `repository over the platform keeps one listener for two collectors and none after both stop`() =
        runTest {
            val repository = LocationRepository(dataSource, backgroundScope)
            val first = mutableListOf<FlightLocationFix>()
            val second = mutableListOf<FlightLocationFix>()
            val firstJob = backgroundScope.launch { repository.fixes.toList(first) }
            val secondJob = backgroundScope.launch { repository.fixes.toList(second) }
            runCurrent()

            assertEquals(1, shadowLocationManager.locationUpdateListeners.size)

            shadowLocationManager.simulateLocation(
                Location(LocationManager.GPS_PROVIDER).apply {
                    speed = 5f
                    time = 1_000L
                    elapsedRealtimeNanos = 3_000_000_000L
                },
            )
            deliverCallbacks()
            assertEquals(1, first.size)
            assertEquals(first, second)

            firstJob.cancel()
            runCurrent()
            assertEquals(1, shadowLocationManager.locationUpdateListeners.size)

            secondJob.cancel()
            runCurrent()
            assertTrue(shadowLocationManager.locationUpdateListeners.isEmpty())
        }

    @Test
    fun `gnss status is mapped to satellites with signal strength until the collection stops`() =
        runTest {
            val reports = mutableListOf<List<GnssSatellite>>()
            val job = backgroundScope.launch { dataSource.satellites().toList(reports) }
            runCurrent()

            shadowLocationManager.simulateGnssStatus(
                GnssStatus
                    .Builder()
                    .addSatellite(GnssStatus.CONSTELLATION_GPS, 1, 35f, 45f, 90f, true, true, true, false, 0f, false, 0f)
                    .addSatellite(GnssStatus.CONSTELLATION_GALILEO, 2, 12.5f, 10f, 180f, false, false, false, false, 0f, false, 0f)
                    .build(),
            )
            deliverCallbacks()

            assertEquals(
                listOf(
                    listOf(
                        GnssSatellite(usedInFix = true, signalStrengthDbHz = 35f),
                        GnssSatellite(usedInFix = false, signalStrengthDbHz = 12.5f),
                    ),
                ),
                reports,
            )

            job.cancel()
            runCurrent()
            shadowLocationManager.simulateGnssStatus(GnssStatus.Builder().build())
            deliverCallbacks()
            assertEquals(1, reports.size)
        }

    @Test
    fun `location switch emits the current state and every provider change until cancelled`() =
        runTest {
            shadowLocationManager.setLocationEnabled(true)
            val states = mutableListOf<Boolean>()
            val job = backgroundScope.launch { dataSource.locationEnabledChanges().toList(states) }
            runCurrent()

            shadowLocationManager.setLocationEnabled(false)
            application.sendBroadcast(Intent(LocationManager.PROVIDERS_CHANGED_ACTION))
            deliverCallbacks()

            assertEquals(listOf(true, false), states)
            assertFalse(dataSource.isLocationEnabled())

            job.cancel()
            runCurrent()
            assertFalse(shadowOf(application).hasReceiverForIntent(Intent(LocationManager.PROVIDERS_CHANGED_ACTION)))
        }

    @Test
    fun `location counts as enabled only with the gps provider on`() {
        shadowLocationManager.setLocationEnabled(true)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        assertTrue(dataSource.isLocationEnabled())

        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        assertFalse(dataSource.isLocationEnabled())

        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowLocationManager.setLocationEnabled(false)
        assertFalse(dataSource.isLocationEnabled())
    }

    @Test
    fun `gnss hardware check reads the gps system feature`() {
        shadowOf(application.packageManager).setSystemFeature(PackageManager.FEATURE_LOCATION_GPS, false)
        assertFalse(dataSource.hasGnssHardware())

        shadowOf(application.packageManager).setSystemFeature(PackageManager.FEATURE_LOCATION_GPS, true)
        assertTrue(dataSource.hasGnssHardware())
    }

    /** Runs callbacks posted to the main executor, then the coroutines they resumed. */
    private fun TestScope.deliverCallbacks() {
        idleMainLooper()
        runCurrent()
    }
}
