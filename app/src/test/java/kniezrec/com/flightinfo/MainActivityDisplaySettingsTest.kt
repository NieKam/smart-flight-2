package kniezrec.com.flightinfo

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.testutil.openFromOverflowMenu
import kniezrec.com.flightinfo.ui.theme.DarkSmartFlightColors
import kniezrec.com.flightinfo.ui.theme.LightSmartFlightColors
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
 * Keep-screen-on, requested orientation and the Theme setting follow the display settings, on
 * launch and after a change in Settings.
 */
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

    @Test
    fun choosingDarkInSettingsPersistsAndAppliesTheDarkScheme() {
        // Robolectric's default configuration is not night: the System default theme is light.
        launch()
        composeRule.openFromOverflowMenu(string(R.string.settings_title))
        assertEquals(LightSmartFlightColors.toolbarTitle, textColor(string(R.string.settings_title)))

        composeRule
            .onNodeWithContentDescription(
                string(R.string.settings_row_description, string(R.string.theme), string(R.string.theme_system)),
            ).performScrollTo()
            .performClick()
        composeRule.onNode(hasText(string(R.string.theme_dark)) and isSelectable()).performClick()
        composeRule.waitForIdle()

        val stored = application.getSharedPreferences("display_behavior", Context.MODE_PRIVATE)
        assertEquals("dark", stored.getString("theme_mode", null))
        assertEquals(DarkSmartFlightColors.toolbarTitle, textColor(string(R.string.settings_title)))
    }

    @Test
    @Config(qualifiers = "+night")
    fun storedLightThemeOverridesANightSystemOnLaunch() {
        application
            .getSharedPreferences("display_behavior", Context.MODE_PRIVATE)
            .edit()
            .putString("theme_mode", "light")
            .commit()

        launch()

        assertEquals(LightSmartFlightColors.toolbarTitle, textColor(string(R.string.app_name)))
    }

    @Test
    @Config(qualifiers = "+night")
    fun systemThemeFollowsANightSystemOnLaunch() {
        launch()

        assertEquals(DarkSmartFlightColors.toolbarTitle, textColor(string(R.string.app_name)))
    }

    /** The color [text] is laid out with. */
    private fun textColor(text: String): Color {
        composeRule.waitForIdle()
        val node = composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results
            .single()
            .layoutInput.style.color
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
