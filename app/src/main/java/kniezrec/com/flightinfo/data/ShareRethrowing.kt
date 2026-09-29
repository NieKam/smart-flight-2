package kniezrec.com.flightinfo.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn

/**
 * Shares this flow in [scope] and rethrows an upstream failure in every collector.
 *
 * The upstream (one platform registration) starts with the first collector and stops as soon as
 * the last one leaves ([SharingStarted.WhileSubscribed] without a stop timeout); the replay cache
 * is dropped when it stops, so a collector never receives a value from an earlier registration.
 *
 * Without the rethrowing, a failure would end the sharing coroutine in [scope] (reaching its
 * exception handler) while collectors wait forever. Instead the failure is kept, and the failed
 * registration stays "started" (without a platform callback), until the last collector stops:
 * every current and joining collector fails with it, none waits for data that cannot come. When
 * sharing stops, the failure is cleared and the next collector registers again.
 */
internal fun <T> Flow<T>.shareRethrowingIn(
    scope: CoroutineScope,
    replay: Int,
): Flow<T> {
    val failure = MutableStateFlow<Throwable?>(null)
    val shared =
        catch { cause ->
            failure.value = cause
            try {
                awaitCancellation()
            } finally {
                failure.value = null
            }
        }.shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay)
    return merge(shared, failure.filterNotNull().map<Throwable, T> { throw it })
}
