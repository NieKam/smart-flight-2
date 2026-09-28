package kniezrec.com.flightinfo.monitoring.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.monitoring.BackgroundNotificationPreferences
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

@RunWith(AndroidJUnit4::class)
class BackgroundNotificationSettingsRepositoryTest {
    private val preferences: SharedPreferences =
        ApplicationProvider
            .getApplicationContext<Context>()
            .getSharedPreferences("monitoring_behavior", Context.MODE_PRIVATE)

    @Test
    fun missingValueDefaultsToEnabled() =
        runTest {
            assertTrue(repository().settings.value.showBackgroundNotification)
        }

    @Test
    fun malformedValueDefaultsToEnabled() =
        runTest {
            preferences.edit().putString(KEY, "bad").commit()

            assertTrue(repository().settings.value.showBackgroundNotification)
        }

    @Test
    fun setterPublishesAtOnceAndPersistsUnderTheSameKey() =
        runTest {
            val repository = repository()

            repository.setShowBackgroundNotification(false)

            assertFalse(repository.settings.value.showBackgroundNotification)
            assertFalse(preferences.getBoolean(KEY, true))
            // A new instance (e.g. after process restart) reads the same value back.
            assertFalse(repository().settings.value.showBackgroundNotification)
        }

    @Test
    fun changesWrittenElsewhereAreEmitted() =
        runTest {
            val repository = repository()
            val emitted = mutableListOf<BackgroundNotificationPreferences>()
            backgroundScope.launch { repository.settings.toList(emitted) }
            runCurrent()

            preferences.edit().putBoolean(KEY, false).commit()
            runCurrent()

            assertEquals(
                listOf(BackgroundNotificationPreferences(true), BackgroundNotificationPreferences(false)),
                emitted,
            )
        }

    private fun TestScope.repository(): SharedPreferencesBackgroundNotificationSettingsRepository =
        SharedPreferencesBackgroundNotificationSettingsRepository(preferences, backgroundScope).also { runCurrent() }

    private companion object {
        const val KEY = "monitoring_show_background_notification"
    }
}
