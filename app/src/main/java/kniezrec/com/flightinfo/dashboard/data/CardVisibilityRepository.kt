package kniezrec.com.flightinfo.dashboard.data

import android.content.SharedPreferences
import kniezrec.com.flightinfo.dashboard.HideableCard
import kniezrec.com.flightinfo.dashboard.data.di.CardVisibilityPreferences
import kniezrec.com.flightinfo.data.observedState
import kniezrec.com.flightinfo.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Dashboard cards hidden by the user because this device lacks their sensor. */
interface CardVisibilityRepository {
    /** The hidden cards; nothing is hidden by default (a non-boolean stored value counts as shown). */
    val hiddenCards: StateFlow<Set<HideableCard>>

    /** Hides [card] persistently; [hiddenCards] holds it when this returns. */
    suspend fun hide(card: HideableCard)

    /** Shows every hidden card again, persistently; [hiddenCards] is empty when this returns. */
    suspend fun showAll()
}

/** [CardVisibilityRepository] over the `card_visibility` file. */
@Singleton
class SharedPreferencesCardVisibilityRepository
    @Inject
    constructor(
        @CardVisibilityPreferences private val preferences: SharedPreferences,
        @ApplicationScope scope: CoroutineScope,
    ) : CardVisibilityRepository {
        private val state = preferences.observedState(scope) { readHiddenCards() }

        override val hiddenCards: StateFlow<Set<HideableCard>> = state.asStateFlow()

        override suspend fun hide(card: HideableCard) {
            preferences.edit().putBoolean(card.key, true).apply()
            // apply() updates the in-memory values at once.
            state.value = preferences.readHiddenCards()
        }

        override suspend fun showAll() {
            val editor = preferences.edit()
            HideableCard.entries.forEach { editor.remove(it.key) }
            editor.apply()
            state.value = preferences.readHiddenCards()
        }

        private companion object {
            val HideableCard.key: String
                get() =
                    when (this) {
                        HideableCard.Course -> "card_hidden_course"
                        HideableCard.Horizon -> "card_hidden_horizon"
                    }

            fun SharedPreferences.readHiddenCards(): Set<HideableCard> {
                val stored = all
                return HideableCard.entries.filterTo(mutableSetOf()) { stored[it.key] == true }
            }
        }
    }
