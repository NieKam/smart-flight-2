package kniezrec.com.flightinfo.data

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Emits the changed key on every change of this file (`null` when the file is cleared).
 *
 * SharedPreferences holds listeners weakly; the listener stays strongly reachable through the
 * `awaitClose` block for as long as the flow is collected.
 */
internal fun SharedPreferences.keyChanges(): Flow<String?> =
    callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> trySend(key) }
        registerOnSharedPreferenceChangeListener(listener)
        awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
    }

/**
 * Holds the value parsed by [read]: synchronously read now (so window flags can be applied before
 * the first frame) and re-read in [scope] on every change of this file.
 *
 * The owning repository also sets the returned state right after its own writes, so a value
 * written through the repository is visible to every reader as soon as the setter returns.
 */
internal fun <T> SharedPreferences.observedState(
    scope: CoroutineScope,
    read: SharedPreferences.() -> T,
): MutableStateFlow<T> {
    val state = MutableStateFlow(read())
    keyChanges().onEach { state.value = read() }.launchIn(scope)
    return state
}
