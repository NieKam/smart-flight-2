package kniezrec.com.flightinfo.display.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.display.DisplayPreferences
import kniezrec.com.flightinfo.display.ThemeMode
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisplaySettingsRepositoryTest {
    private val preferences: SharedPreferences =
        ApplicationProvider
            .getApplicationContext<Context>()
            .getSharedPreferences("display_behavior", Context.MODE_PRIVATE)

    @Test
    fun missingValuesUseIndependentDefaults() =
        runTest {
            assertEquals(
                DisplayPreferences(keepScreenAlwaysOn = false, portraitOrientation = true, largerMapZoom = false),
                repository().display.value,
            )
        }

    @Test
    fun malformedValuesUseOnlyTheirOwnDefaults() =
        runTest {
            preferences
                .edit()
                .putString(KEEP_SCREEN, "invalid")
                .putBoolean(PORTRAIT, false)
                .putString(LARGER_ZOOM, "invalid")
                .commit()

            assertEquals(DisplayPreferences(portraitOrientation = false), repository().display.value)
        }

    @Test
    fun setterPublishesAtOnceAndPersistsUnderTheSameKeys() =
        runTest {
            val repository = repository()
            val expected = DisplayPreferences(keepScreenAlwaysOn = true, portraitOrientation = false, largerMapZoom = true)

            repository.set(expected)

            assertEquals(expected, repository.display.value)
            assertEquals(true, preferences.getBoolean(KEEP_SCREEN, false))
            assertEquals(false, preferences.getBoolean(PORTRAIT, true))
            assertEquals(true, preferences.getBoolean(LARGER_ZOOM, false))
            // A new instance (e.g. after process restart) reads the same values back.
            assertEquals(expected, repository().display.value)
        }

    @Test
    fun changesWrittenElsewhereAreEmitted() =
        runTest {
            val repository = repository()
            val emitted = mutableListOf<DisplayPreferences>()
            backgroundScope.launch { repository.display.toList(emitted) }
            runCurrent()

            preferences.edit().putBoolean(PORTRAIT, false).commit()
            runCurrent()
            preferences.edit().putBoolean(KEEP_SCREEN, true).commit()
            runCurrent()

            assertEquals(
                listOf(
                    DisplayPreferences(),
                    DisplayPreferences(portraitOrientation = false),
                    DisplayPreferences(keepScreenAlwaysOn = true, portraitOrientation = false),
                ),
                emitted,
            )
        }

    @Test
    fun themeModeDefaultsToSystemAndMalformedValuesFallBackToSystem() =
        runTest {
            assertEquals(ThemeMode.SYSTEM, repository().display.value.themeMode)

            preferences.edit().putString(THEME_MODE, "sepia").commit()
            assertEquals(ThemeMode.SYSTEM, repository().display.value.themeMode)

            preferences.edit().putBoolean(THEME_MODE, true).commit()
            assertEquals(ThemeMode.SYSTEM, repository().display.value.themeMode)
        }

    @Test
    fun themeModePersistsUnderTheThemeModeKeyAndIsReadBack() =
        runTest {
            val repository = repository()

            repository.set(DisplayPreferences(themeMode = ThemeMode.DARK))

            assertEquals("dark", preferences.getString(THEME_MODE, null))
            assertEquals(ThemeMode.DARK, repository().display.value.themeMode)

            repository.set(DisplayPreferences(themeMode = ThemeMode.LIGHT))

            assertEquals("light", preferences.getString(THEME_MODE, null))
            assertEquals(ThemeMode.LIGHT, repository().display.value.themeMode)
        }

    @Test
    fun themeModeFlowEmitsOnlyChangesOfTheTheme() =
        runTest {
            val repository = repository()
            val emitted = mutableListOf<ThemeMode>()
            backgroundScope.launch { repository.themeMode.toList(emitted) }
            runCurrent()

            repository.set(DisplayPreferences(themeMode = ThemeMode.DARK))
            runCurrent()
            // Another display setting changes: no new theme emission.
            repository.set(DisplayPreferences(themeMode = ThemeMode.DARK, keepScreenAlwaysOn = true))
            runCurrent()
            preferences.edit().putString(THEME_MODE, "system").commit()
            runCurrent()

            assertEquals(listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.SYSTEM), emitted)
        }

    private fun TestScope.repository(): SharedPreferencesDisplaySettingsRepository =
        SharedPreferencesDisplaySettingsRepository(preferences, backgroundScope).also { runCurrent() }

    private companion object {
        const val KEEP_SCREEN = "display_behavior_keep_screen_always_on"
        const val PORTRAIT = "display_behavior_portrait_orientation"
        const val LARGER_ZOOM = "display_behavior_larger_map_zoom"
        const val THEME_MODE = "theme_mode"
    }
}
