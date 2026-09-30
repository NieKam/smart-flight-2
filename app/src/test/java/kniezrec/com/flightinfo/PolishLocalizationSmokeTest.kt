package kniezrec.com.flightinfo

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.testutil.idleMainLooper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocationManager
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * TASK-036: under a Polish locale the dashboard, Settings and About render the `values-pl`
 * translations (asserted with literal Polish text, so an English fallback fails) without a
 * missing-resource or format crash, and Polish plurals pick the right category.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "pl-w411dp-h891dp")
class PolishLocalizationSmokeTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val locationManager: ShadowLocationManager = shadowOf(application.getSystemService(LocationManager::class.java))
    private var scenario: ActivityScenario<MainActivity>? = null
    private var previousCreateActivityContexts: String? = null

    @Before
    fun setUp() {
        // As in DashboardPermissionTest: activities get a display-associated context, and a
        // one-entry map archive keeps the launch fast.
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

    @Test
    fun dashboardWithoutPermissionSettingsAndAboutRenderInPolish() {
        scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.onNodeWithText("Uprawnienia lokalizacji").assertIsDisplayed()
        composeRule.onNodeWithText("Przyznaj uprawnienia").assertIsDisplayed()
        // Robolectric has no rotation-vector sensor: the Course card offers to hide itself.
        composeRule.onNodeWithText("To urządzenie nie wspiera funkcji kompasu, ukryć kartę?").assertIsDisplayed()

        openFromOverflowMenu("Ustawienia")
        composeRule.onNodeWithText("Jednostki").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Wstecz").assertIsDisplayed()
        checkNotNull(scenario).onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()

        openFromOverflowMenu("O aplikacji")
        composeRule.onNodeWithText("Informacje o bezpieczeństwie").assertIsDisplayed()
        composeRule.onNodeWithText("Wyślij sugestie").assertExists()
    }

    @Test
    fun dashboardWithPermissionAndGpsOffRendersInPolish() {
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        locationManager.setLocationEnabled(false)
        scenario = ActivityScenario.launch(MainActivity::class.java)

        composeRule.onNodeWithText("Włącz GPS").assertIsDisplayed()
        composeRule.onNodeWithText("Lokalizacja w telefonie jest wyłączona, czy chcesz ją włączyć?").assertIsDisplayed()
        composeRule.onNodeWithText("Nie").performClick()

        composeRule.waitUntil(ASYNC_TIMEOUT_MILLIS) {
            idleMainLooper()
            composeRule.onAllNodesWithText("Usługi lokalizacji są wyłączone").fetchSemanticsNodes().isNotEmpty()
        }
        // With location services off the GNSS card shows this state instead of its "Stan GNSS"
        // title; the title node only lingers in the Crossfade's outgoing Waiting content.
        composeRule.onNodeWithText("Włącz usługi lokalizacji, aby odbierać sygnał GNSS.").assertIsDisplayed()
        composeRule.onNodeWithText("Otwórz ustawienia lokalizacji").assertExists()
        composeRule.onNodeWithText("Parametry lotu").assertExists()
    }

    @Test
    fun pluralsUsePolishCategories() {
        val resources = application.resources

        assertEquals("1 godzina", resources.getQuantityString(R.plurals.nearby_city_utc_offset_hours, 1, 1))
        assertEquals("3 godziny", resources.getQuantityString(R.plurals.nearby_city_utc_offset_hours, 3, 3))
        assertEquals("5 godzin", resources.getQuantityString(R.plurals.nearby_city_utc_offset_hours, 5, 5))
        assertEquals("12 minut", resources.getQuantityString(R.plurals.nearby_city_utc_offset_minutes, 12, 12))
        assertEquals("22 minuty", resources.getQuantityString(R.plurals.nearby_city_utc_offset_minutes, 22, 22))
        assertEquals("Używanych 0 satelitów", resources.getQuantityString(R.plurals.gnss_satellites_used, 0, 0))
    }

    @Test
    fun nonTranslatableStringsFallBackToDefaults() {
        assertEquals("Smart Flight", application.getString(R.string.app_name))
        assertEquals("km/h", application.getString(R.string.unit_kmh))
        assertEquals("NE", application.getString(R.string.course_cardinal_north_east))
    }

    /** Same as testutil's openFromOverflowMenu, with the Polish "More options" description. */
    private fun openFromOverflowMenu(label: String) {
        composeRule.onNodeWithContentDescription("Więcej opcji").performClick()
        composeRule.onNodeWithText(label).performClick()
    }

    private companion object {
        const val CREATE_ACTIVITY_CONTEXTS = "robolectric.createActivityContexts"
        const val ASYNC_TIMEOUT_MILLIS = 20_000L
    }
}
