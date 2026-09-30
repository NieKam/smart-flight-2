package kniezrec.com.flightinfo.monitoring

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.MainActivity
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.testutil.idleMainLooper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * POST_NOTIFICATIONS is requested right after location is granted, when the background notification
 * is on (Android 13+ only).
 */
@RunWith(AndroidJUnit4::class)
class NotificationPermissionFlowTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    private val application: Application = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var previousCreateActivityContexts: String? = null

    @Before
    fun setUp() {
        // As in DashboardScreenTest: activities get a display-associated context, and a one-entry
        // map archive keeps the launch fast.
        previousCreateActivityContexts = System.getProperty(CREATE_ACTIVITY_CONTEXTS)
        System.setProperty(CREATE_ACTIVITY_CONTEXTS, "true")
        ZipOutputStream(FileOutputStream(File(application.cacheDir, "osmdroid.zip"))).use { zip ->
            zip.putNextEntry(ZipEntry("tile.jpg"))
            zip.write(byteArrayOf(0))
            zip.closeEntry()
        }
        shadowOf(application.packageManager).setSystemFeature(PackageManager.FEATURE_LOCATION_GPS, true)
    }

    @After
    fun tearDown() {
        scenario?.close()
        previousCreateActivityContexts?.let { System.setProperty(CREATE_ACTIVITY_CONTEXTS, it) }
            ?: System.clearProperty(CREATE_ACTIVITY_CONTEXTS)
    }

    @Config(sdk = [33])
    @Test
    fun locationGrantWithTheNotificationOnRequestsNotifications() {
        grantLocationThroughTheDialog()

        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), lastRequestedPermissions())
    }

    @Config(sdk = [33])
    @Test
    fun locationGrantWithTheNotificationOffDoesNotRequestNotifications() {
        application
            .getSharedPreferences(MONITORING_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_BACKGROUND_NOTIFICATION, false)
            .commit()

        grantLocationThroughTheDialog()

        assertEquals(LOCATION_PERMISSIONS, lastRequestedPermissions())
    }

    @Config(sdk = [32])
    @Test
    fun belowAndroid13NotificationsAreNeverRequested() {
        grantLocationThroughTheDialog()

        assertEquals(LOCATION_PERMISSIONS, lastRequestedPermissions())
    }

    /** Taps "Grant permission", then answers the system dialog with "allow". */
    private fun grantLocationThroughTheDialog() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.onNodeWithText(application.getString(R.string.permission_grant)).performClick()
        val request = checkNotNull(lastRequest())
        assertEquals(LOCATION_PERMISSIONS, request.requestedPermissions.toList())

        shadowOf(application).grantPermissions(*request.requestedPermissions)
        checkNotNull(scenario).onActivity { activity ->
            @Suppress("DEPRECATION") // Delivers the result to the Activity Result API, as the platform does.
            activity.onRequestPermissionsResult(
                request.requestCode,
                request.requestedPermissions,
                IntArray(request.requestedPermissions.size) { PackageManager.PERMISSION_GRANTED },
            )
        }
        idleMainLooper()
        composeRule.waitForIdle()
    }

    private fun lastRequest() =
        run {
            var request: org.robolectric.shadows.ShadowActivity.PermissionsRequest? = null
            checkNotNull(scenario).onActivity { request = shadowOf(it).lastRequestedPermission }
            request
        }

    private fun lastRequestedPermissions(): List<String> = checkNotNull(lastRequest()).requestedPermissions.toList()

    private companion object {
        const val CREATE_ACTIVITY_CONTEXTS = "robolectric.createActivityContexts"
        const val MONITORING_PREFERENCES = "monitoring_behavior"
        const val KEY_SHOW_BACKGROUND_NOTIFICATION = "monitoring_show_background_notification"
        val LOCATION_PERMISSIONS = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}
