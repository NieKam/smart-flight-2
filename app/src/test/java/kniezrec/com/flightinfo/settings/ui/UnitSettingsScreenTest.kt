package kniezrec.com.flightinfo.settings.ui

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.dashboard.HideableCard
import kniezrec.com.flightinfo.dashboard.ui.DashboardHeader
import kniezrec.com.flightinfo.display.DisplayPreferences
import kniezrec.com.flightinfo.displayunits.AltitudeUnit
import kniezrec.com.flightinfo.displayunits.DistanceUnit
import kniezrec.com.flightinfo.displayunits.PressureUnit
import kniezrec.com.flightinfo.displayunits.SpeedUnit
import kniezrec.com.flightinfo.displayunits.UnitPreferences
import kniezrec.com.flightinfo.displayunits.VerticalSpeedUnit
import kniezrec.com.flightinfo.gnss.GnssStatusState
import kniezrec.com.flightinfo.gnss.ui.GnssStatusCard
import kniezrec.com.flightinfo.map.ui.MapCard
import kniezrec.com.flightinfo.map.ui.MapUiState
import kniezrec.com.flightinfo.testutil.MORE_OPTIONS
import kniezrec.com.flightinfo.testutil.openFromOverflowMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// When larger zoom is on, the row description also carries the tile-availability warning.
private const val LARGER_ZOOM_ON_DESCRIPTION =
    "Larger map zoom, current value On. Extra zoom may show unavailable or grey map areas " +
        "because offline tiles may not be available at those levels."

// osmdroid floors the map centre to whole Mercator pixels on every zoom change (TileSystem.ClipToLong),
// so zooming 8 -> 6 moves the centre by at most one zoom-8 pixel plus one zoom-6 pixel of longitude
// (a latitude pixel is smaller). Resetting or re-centring the viewport would move it by degrees.
private const val CENTER_TOLERANCE_DEGREES = 360.0 / (256 * 256) + 360.0 / (256 * 64)

@RunWith(AndroidJUnit4::class)
class UnitSettingsScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun showsAllMetricDefaultsAndAccessibleBackAction() {
        composeRule.setContent { UnitSettingsScreen(UnitPreferences(), {}, {}) }
        composeRule.onNodeWithText("Units").assertIsDisplayed()
        composeRule.onNode(hasContentDescription("Speed, current value km/h, double tap to change")).assertExists()
        composeRule.onNode(hasContentDescription("Altitude, current value m, double tap to change")).assertExists()
        composeRule.onNode(hasContentDescription("Distance, current value km, double tap to change")).assertExists()
        composeRule.onNode(hasContentDescription("Vertical speed, current value m/s, double tap to change")).assertExists()
        composeRule.onNode(hasContentDescription("Pressure, current value mbar, double tap to change")).assertExists()
        composeRule.onNodeWithContentDescription("Navigate up").assertExists()
    }

    @Test fun showHiddenCardsIsDisabledWhileNothingIsHidden() {
        composeRule.setContent { UnitSettingsScreen(UnitPreferences(), {}, {}) }

        composeRule
            .onNodeWithText("Show hidden cards")
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("No hidden cards").assertExists()
    }

    @Test fun showHiddenCardsListsTheHiddenCardsAndRestoresThem() {
        var hidden by mutableStateOf(setOf(HideableCard.Horizon, HideableCard.Course))
        composeRule.setContent {
            UnitSettingsScreen(UnitPreferences(), {}, {}, hiddenCards = hidden, onShowHiddenCards = { hidden = emptySet() })
        }
        composeRule.onNodeWithText("Compass, Horizon").assertExists()

        composeRule
            .onNodeWithText("Show hidden cards")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeRule.onNodeWithText("No hidden cards").assertExists()
        composeRule.onNodeWithText("Show hidden cards").assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(emptySet<HideableCard>(), hidden) }

        composeRule.runOnIdle { hidden = setOf(HideableCard.Horizon) }
        composeRule.onNodeWithText("Horizon").assertExists()
    }

    @Test fun selectingDistanceUpdatesSummaryAndCallbackImmediately() {
        var selected by mutableStateOf(UnitPreferences())
        composeRule.setContent {
            UnitSettingsScreen(selected, { selected = it }, {})
        }
        composeRule.onNode(hasContentDescription("Distance, current value km, double tap to change")).performClick()
        composeRule.onNodeWithText("Miles (mi)").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(DistanceUnit.MILES, selected.distance) }
        composeRule.onNode(hasContentDescription("Distance, current value mi, double tap to change")).assertExists()
    }

    @Test fun displaySwitchesExposeStateAndEnabledZoomWarning() {
        var selected by mutableStateOf(DisplayPreferences(largerMapZoom = true))
        composeRule.setContent {
            UnitSettingsScreen(UnitPreferences(), {}, {}, selected, onDisplayPreferenceChange = { selected = it })
        }
        composeRule
            .onNode(
                hasRole(Role.Switch) and
                    hasContentDescription(
                        "Larger map zoom, current value On. Extra zoom may show unavailable or grey map areas " +
                            "because offline tiles may not be available at those levels.",
                    ),
            ).assertIsOn()
        composeRule
            .onNodeWithText(
                "Extra zoom may show unavailable or grey map areas because offline tiles may not be available at those levels.",
            ).assertIsDisplayed()
        composeRule
            .onNode(
                hasRole(
                    Role.Switch,
                ) and hasContentDescription("Portrait orientation, current value Portrait"),
            ).assertExists()
    }

    @Test fun togglingEachDisplayRowUpdatesStateAndDisablingZoomRemovesWarning() {
        var selected by mutableStateOf(DisplayPreferences())
        composeRule.setContent {
            UnitSettingsScreen(UnitPreferences(), {}, {}, selected, onDisplayPreferenceChange = { selected = it })
        }
        composeRule.onNode(hasContentDescription("Keep screen always on, current value Off")).performClick()
        composeRule.onNode(hasContentDescription("Keep screen always on, current value On")).assertIsOn()
        composeRule.onNode(hasContentDescription("Portrait orientation, current value Portrait")).performClick()
        composeRule.onNode(hasContentDescription("Portrait orientation, current value Sensor")).assertIsOff()
        composeRule.onNode(hasContentDescription("Larger map zoom, current value Off")).performClick()
        composeRule
            .onNodeWithText(
                "Extra zoom may show unavailable or grey map areas because offline tiles may not be available at those levels.",
            ).assertIsDisplayed()
        composeRule.onNode(hasContentDescription(LARGER_ZOOM_ON_DESCRIPTION)).performClick()
        composeRule
            .onNodeWithText(
                "Extra zoom may show unavailable or grey map areas because offline tiles may not be available at those levels.",
            ).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(DisplayPreferences(keepScreenAlwaysOn = true, portraitOrientation = false), selected)
        }
    }

    @Test fun blockedNotificationShowsTheEffectiveStateAndTheAllowAction() {
        var allowed = false
        var on by mutableStateOf(true)
        composeRule.setContent {
            UnitSettingsScreen(
                UnitPreferences(),
                {},
                {},
                showBackgroundNotification = on,
                notificationsBlocked = true,
                onAllowNotifications = { allowed = true },
            )
        }
        composeRule.onNodeWithText("Notifications are blocked").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Allow notifications").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(true, allowed) }

        // Switched off, blocked notifications do not matter.
        composeRule.runOnIdle { on = false }
        composeRule.onNodeWithText("Allow notifications").assertDoesNotExist()
    }

    @Test fun everySelectorShowsItsExactOptionsAndUpdatesItsSummary() {
        var selected by mutableStateOf(UnitPreferences())
        composeRule.setContent {
            UnitSettingsScreen(selected, { selected = it }, {})
        }
        val selectors =
            listOf(
                Triple("Speed", listOf("Kilometres per hour (km/h)", "Miles per hour (mph)", "Knots (kt)"), "mph"),
                Triple("Altitude", listOf("Metres (m)", "Feet (ft)"), "ft"),
                Triple("Distance", listOf("Kilometres (km)", "Miles (mi)"), "mi"),
                Triple(
                    "Vertical speed",
                    listOf("Metres per second (m/s)", "Metres per minute (m/min)", "Feet per minute (ft/min)"),
                    "ft/min",
                ),
                Triple("Pressure", listOf("Millibar (mbar)", "Inch of mercury (inHg)"), "inHg"),
            )
        val defaults = listOf("km/h", "m", "km", "m/s", "mbar")
        selectors.forEachIndexed { index, (label, options, chosen) ->
            // The lower rows sit below the fold of a phone window; scroll them in like a user would.
            composeRule
                .onNode(hasContentDescription("$label, current value ${defaults[index]}, double tap to change"))
                .performScrollTo()
                .performClick()
            options.forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
            composeRule.onNodeWithText(options.single { it.endsWith("($chosen)") }).performClick()
            composeRule.onNode(hasContentDescription("$label, current value $chosen, double tap to change")).assertExists()
        }
        composeRule.runOnIdle {
            assertEquals(SpeedUnit.MILES_PER_HOUR, selected.speed)
            assertEquals(AltitudeUnit.FEET, selected.altitude)
            assertEquals(DistanceUnit.MILES, selected.distance)
            assertEquals(VerticalSpeedUnit.FEET_PER_MINUTE, selected.verticalSpeed)
            assertEquals(PressureUnit.INCHES_OF_MERCURY, selected.pressure)
        }
    }

    @Test fun authorizedDashboardSettingsEntryAndBackReturnToDashboard() {
        var showSettings by mutableStateOf(false)
        composeRule.setContent {
            if (showSettings) {
                UnitSettingsScreen(UnitPreferences(), {}, { showSettings = false })
            } else {
                Column {
                    DashboardHeader(onOpenSettings = { showSettings = true }, onOpenAbout = {})
                    GnssStatusCard(GnssStatusState.Waiting, onOpenLocationSettings = {}, onRetry = {})
                }
            }
        }
        composeRule.openFromOverflowMenu("Settings")
        composeRule.onNodeWithText("Units").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Navigate up").performClick()
        composeRule.onNodeWithText("GNSS status").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(MORE_OPTIONS).assertIsDisplayed()
    }

    @Test fun settingsFlowRetainsMapViewportAndAppliesLargerZoomPolicyInPlace() {
        val archive = File(composeRule.activity.cacheDir, "settings-map-test.zip")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("tile.jpg"))
            zip.write(byteArrayOf(0))
            zip.closeEntry()
        }
        var showSettings by mutableStateOf(false)
        var displayPreferences by mutableStateOf(DisplayPreferences(largerMapZoom = true))
        try {
            composeRule.setContent {
                Box(Modifier.fillMaxSize()) {
                    Column {
                        DashboardHeader(onOpenSettings = { showSettings = true }, onOpenAbout = {})
                        MapCard(MapUiState.Ready(archive, largerMapZoom = displayPreferences.largerMapZoom), onRetry = {})
                    }
                    if (showSettings) {
                        UnitSettingsScreen(
                            preferences = UnitPreferences(),
                            onPreferenceChange = {},
                            onBack = { showSettings = false },
                            displayPreferences = displayPreferences,
                            onDisplayPreferenceChange = { displayPreferences = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }

            val initialMap = requireNotNull(findMapView(composeRule.activity.window.decorView))
            composeRule.runOnIdle {
                // Zoom first: osmdroid zooms around the current centre snapped to whole pixels, so
                // centring at the default zoom 3 and then zooming would move the centre by a zoom-3 pixel.
                initialMap.controller.setZoom(8.0)
                initialMap.controller.setCenter(GeoPoint(48.8566, 2.3522))
            }
            composeRule.openFromOverflowMenu("Settings")
            composeRule.runOnIdle {
                assertSame(initialMap, findMapView(composeRule.activity.window.decorView))
                assertEquals(9.0, initialMap.maxZoomLevel, 0.0)
            }

            composeRule.onNodeWithContentDescription(LARGER_ZOOM_ON_DESCRIPTION).performClick()
            composeRule.runOnIdle {
                assertSame(initialMap, findMapView(composeRule.activity.window.decorView))
                assertEquals(6.0, initialMap.maxZoomLevel, 0.0)
                assertEquals(6.0, initialMap.zoomLevelDouble, 0.0)
                assertEquals(48.8566, initialMap.mapCenter.latitude, CENTER_TOLERANCE_DEGREES)
                assertEquals(2.3522, initialMap.mapCenter.longitude, CENTER_TOLERANCE_DEGREES)
            }

            composeRule.onNodeWithContentDescription("Navigate up").performClick()
            composeRule.runOnIdle {
                assertSame(initialMap, findMapView(composeRule.activity.window.decorView))
                assertEquals(6.0, initialMap.maxZoomLevel, 0.0)
                assertEquals(48.8566, initialMap.mapCenter.latitude, CENTER_TOLERANCE_DEGREES)
                assertEquals(2.3522, initialMap.mapCenter.longitude, CENTER_TOLERANCE_DEGREES)
            }
        } finally {
            archive.delete()
        }
    }

    @Test fun largerMapZoomRowFlashesWhenOpenedFromTheZoomTipAndThenReportsItIsDone() {
        var highlight by mutableStateOf(true)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            UnitSettingsScreen(
                UnitPreferences(),
                {},
                {},
                highlightLargerMapZoom = highlight,
                onHighlightFinished = { highlight = false },
            )
        }

        composeRule.mainClock.advanceTimeBy(200)
        composeRule.onNodeWithTag(LARGER_MAP_ZOOM_ROW_TAG).assert(highlighted(true))
        // Only that row.
        composeRule.onNodeWithText("Keep screen always on").assert(highlighted(false))

        // Three flashes of 450 ms.
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.onNodeWithTag(LARGER_MAP_ZOOM_ROW_TAG).assert(highlighted(false))
        composeRule.runOnIdle { assertFalse(highlight) }
    }

    @Test fun withoutARequestNoRowIsHighlighted() {
        composeRule.setContent { UnitSettingsScreen(UnitPreferences(), {}, {}) }

        composeRule.onNodeWithTag(LARGER_MAP_ZOOM_ROW_TAG).assert(highlighted(false))
    }

    private fun highlighted(value: Boolean): SemanticsMatcher = SemanticsMatcher.expectValue(SettingHighlighted, value)

    private fun hasRole(role: Role): SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, role)

    private fun findMapView(view: View): MapView? =
        when (view) {
            is MapView -> view
            is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findMapView(view.getChildAt(it)) }
            else -> null
        }
}
