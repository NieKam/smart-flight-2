package kniezrec.com.flightinfo.monitoring

import android.Manifest
import android.app.Application
import android.content.Context
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowLocationManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocationForegroundServiceEligibilityTest {
    private lateinit var controller: ServiceController<EligibilityProbeService>
    private lateinit var application: Application
    private lateinit var permissions: ShadowApplication
    private lateinit var locationManager: ShadowLocationManager

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        permissions = Shadow.extract(application)
        locationManager = Shadow.extract(application.getSystemService(Context.LOCATION_SERVICE))
        controller = Robolectric.buildService(EligibilityProbeService::class.java).create()
    }

    @After
    fun tearDown() {
        controller.destroy()
        permissions.denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    @Test
    fun `actual eligibility requires fine permission and enabled location provider`() {
        permissions.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        locationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        assertTrue(controller.get().isEligibleForTest())

        permissions.denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertFalse(controller.get().isEligibleForTest())

        permissions.grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        locationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        assertFalse(controller.get().isEligibleForTest())

        locationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        assertTrue(controller.get().isEligibleForTest())
    }

    @Test
    fun `actual notification permission check supports denial without prompting`() {
        assertFalse(controller.get().canPostNotificationsForTest())
        permissions.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(controller.get().canPostNotificationsForTest())
        permissions.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(controller.get().canPostNotificationsForTest())
    }
}

internal class EligibilityProbeService : LocationForegroundService() {
    fun isEligibleForTest(): Boolean = isMonitoringEligible()

    fun canPostNotificationsForTest(): Boolean = canPostNotifications()
}
