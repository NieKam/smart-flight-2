package kniezrec.com.flightinfo.dashboard.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.dashboard.HideableCard
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardVisibilityRepositoryTest {
    private val preferences: SharedPreferences =
        ApplicationProvider
            .getApplicationContext<Context>()
            .getSharedPreferences("card_visibility", Context.MODE_PRIVATE)

    @Test
    fun nothingIsHiddenByDefault() =
        runTest {
            assertEquals(emptySet<HideableCard>(), repository().hiddenCards.value)
        }

    @Test
    fun malformedValuesCountAsShown() =
        runTest {
            preferences
                .edit()
                .putString(COURSE, "true")
                .putBoolean(HORIZON, false)
                .commit()

            assertEquals(emptySet<HideableCard>(), repository().hiddenCards.value)
        }

    @Test
    fun hidePublishesAtOnceAndPersists() =
        runTest {
            val repository = repository()

            repository.hide(HideableCard.Course)

            assertEquals(setOf(HideableCard.Course), repository.hiddenCards.value)
            assertEquals(true, preferences.getBoolean(COURSE, false))
            assertFalse(preferences.contains(HORIZON))
            // A new instance (e.g. after the next launch) reads the same value back.
            assertEquals(setOf(HideableCard.Course), repository().hiddenCards.value)

            repository.hide(HideableCard.Horizon)

            assertEquals(setOf(HideableCard.Course, HideableCard.Horizon), repository().hiddenCards.value)
        }

    @Test
    fun showAllClearsAndPersists() =
        runTest {
            val repository = repository()
            repository.hide(HideableCard.Course)
            repository.hide(HideableCard.Horizon)

            repository.showAll()

            assertEquals(emptySet<HideableCard>(), repository.hiddenCards.value)
            assertFalse(preferences.contains(COURSE))
            assertFalse(preferences.contains(HORIZON))
            assertEquals(emptySet<HideableCard>(), repository().hiddenCards.value)
        }

    @Test
    fun changesAreEmitted() =
        runTest {
            val repository = repository()
            val emitted = mutableListOf<Set<HideableCard>>()
            backgroundScope.launch { repository.hiddenCards.toList(emitted) }
            runCurrent()

            repository.hide(HideableCard.Horizon)
            runCurrent()
            // Written elsewhere (not through this instance).
            preferences.edit().putBoolean(COURSE, true).commit()
            runCurrent()
            repository.showAll()
            runCurrent()

            assertEquals(
                listOf(
                    emptySet(),
                    setOf(HideableCard.Horizon),
                    setOf(HideableCard.Course, HideableCard.Horizon),
                    emptySet(),
                ),
                emitted,
            )
        }

    private fun TestScope.repository(): SharedPreferencesCardVisibilityRepository =
        SharedPreferencesCardVisibilityRepository(preferences, backgroundScope).also { runCurrent() }

    private companion object {
        const val COURSE = "card_hidden_course"
        const val HORIZON = "card_hidden_horizon"
    }
}
