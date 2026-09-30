package kniezrec.com.flightinfo

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Keep-screen-on and requested orientation follow the display settings, on launch and after toggling. */
@RunWith(AndroidJUnit4::class)
class MainActivityDisplaySettingsTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    private val application: Application = ApplicationProvider.getApplicationContext()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var previousCreateActivityContexts: String? = null

    @Before
    fun setUp() {
        // Same switches as MainActivityCharacterizationTest: activities get a display-associated
        // context, and a one-entry map archive skips the large asset copy.
        previousCreateActivityContexts = System.getProperty(CREATE_ACTIVITY_CONTEXTS)
        System.setProperty(CREATE_ACTIVITY_CONTEXTS, "true")
        ZipOutputStream(FileOutputStream(File(application.cacheDir, "osmdroid.zip"))).use { zip ->
            zip.putNextEntry(ZipEntry("tile.jpg"))
            zip.write(byteArrayOf(0))
            zip.closeEntry()
        }
        shadowOf(application).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }

    @After
    fun tearDown() {
        scenario?.close()
        previousCreateActivityContexts?.let { System.setProperty(CREATE_ACTIVITY_CONTEXTS, it) }
            ?: System.clearProperty(CREATE_ACTIVITY_CONTEXTS)
    }

    @Test
    fun defaultsKeepScreenOffAndRequestPortraitOnLaunch() {
        launch()

        assertWindow(keepScreenOn = false, orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    }

    @Test
    fun storedSettingsAreAppliedOnLaunch() {
        application
            .getSharedPreferences("display_behavior", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("display_behavior_keep_screen_always_on", true)
            .putBoolean("display_behavior_portrait_orientation", false)
            .commit()

        launch()

        assertWindow(keepScreenOn = true, orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR)
    }

    @Test
    fun togglingInSettingsAppliesAndPersistsBothDirections() {
        launch()
        composeRule.openFromOverflowMenu(string(R.string.settings_title))

        clickDisplayRow(R.string.keep_screen_always_on, R.string.settings_off)
        clickDisplayRow(R.string.portrait_orientation, R.string.orientation_portrait)

        assertWindow(keepScreenOn = true, orientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR)
        val stored = application.getSharedPreferences("display_behavior", Context.MODE_PRIVATE)
        assertEquals(true, stored.getBoolean("display_behavior_keep_screen_always_on", false))
        assertEquals(false, stored.getBoolean("display_behavior_portrait_orientation", true))

        clickDisplayRow(R.string.keep_screen_always_on, R.string.settings_on)
        clickDisplayRow(R.string.portrait_orientation, R.string.orientation_sensor)

        assertWindow(keepScreenOn = false, orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun clickDisplayRow(
        @StringRes label: Int,
        @StringRes value: Int,
    ) {
        composeRule
            .onNodeWithContentDescription(string(R.string.display_setting_description, string(label), string(value)))
            .performScrollTo()
            .performClick()
    }

    private fun assertWindow(
        keepScreenOn: Boolean,
        orientation: Int,
    ) {
        composeRule.waitForIdle()
        checkNotNull(scenario).onActivity { activity ->
            val flags = activity.window.attributes.flags
            assertEquals(keepScreenOn, flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0)
            assertEquals(orientation, activity.requestedOrientation)
        }
    }

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = application.getString(id, *args)

    private companion object {
        const val CREATE_ACTIVITY_CONTEXTS = "robolectric.createActivityContexts"
    }
}
