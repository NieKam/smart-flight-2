package kniezrec.com.flightinfo.nearby

import kniezrec.com.flightinfo.flight.FlightLocationFix
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.Executor

sealed interface NearbyCityState {
    data object WaitingForPosition : NearbyCityState

    data object LookingUp : NearbyCityState

    data class Available(
        val cityName: String,
        val country: String,
        val distanceKilometres: Double,
        val localTime: String,
        val utcOffsetSeconds: Int,
    ) : NearbyCityState

    data object Unavailable : NearbyCityState
}

internal data class NearbyCoordinate(
    val latitude: Double,
    val longitude: Double,
) {
    companion object {
        fun from(
            latitude: Double?,
            longitude: Double?,
        ): NearbyCoordinate? =
            latitude?.takeIf(Double::isFinite)?.let { validLatitude ->
                longitude?.takeIf(Double::isFinite)?.takeIf { it in -180.0..180.0 }?.let { validLongitude ->
                    validLatitude.takeIf { it in -90.0..90.0 }?.let { NearbyCoordinate(it, validLongitude) }
                }
            }
    }
}

internal data class NearbyCityRecord(
    val id: Long,
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val timeZoneId: String,
)

internal interface NearbyCityRepository {
    @Throws(Exception::class)
    fun findNearest(
        position: NearbyCoordinate,
        reload: Boolean = false,
    ): NearbyCityRecord?
}

/** Owns city lookup state; callbacks from obsolete sessions or fixes are ignored. */
internal class NearbyCityController(
    private val repository: NearbyCityRepository,
    private val worker: Executor,
    private val callbackExecutor: Executor,
    private val clock: () -> Instant = { Instant.now() },
    private val onStateChanged: (NearbyCityState) -> Unit,
) {
    private var session = 0L
    private var fix = 0L
    private var active = false
    private var latestPosition: NearbyCoordinate? = null
    private var pendingRequest: LookupRequest? = null
    private var workerScheduled = false

    fun start() =
        synchronized(this) {
            session++
            fix = 0
            active = true
            latestPosition = null
            pendingRequest = null
            onStateChanged(NearbyCityState.WaitingForPosition)
        }

    fun stop() =
        synchronized(this) {
            session++
            active = false
            latestPosition = null
            pendingRequest = null
            onStateChanged(NearbyCityState.WaitingForPosition)
        }

    fun onLocationFix(location: FlightLocationFix) {
        val position = NearbyCoordinate.from(location.latitude, location.longitude) ?: return
        val request =
            synchronized(this) {
                if (!active) return
                latestPosition = position
                fix++
                LookupRequest(session, fix, position, false).also { onStateChanged(NearbyCityState.LookingUp) }
            }
        submit(request)
    }

    fun retry() {
        val request =
            synchronized(this) {
                if (!active) return
                fix++
                latestPosition?.let { LookupRequest(session, fix, it, true) }.also {
                    onStateChanged(if (it == null) NearbyCityState.WaitingForPosition else NearbyCityState.LookingUp)
                }
            }
        request?.let(::submit)
    }

    /** Keeps one lookup active and replaces any queued lookup with the newest accepted fix. */
    private fun submit(request: LookupRequest) {
        val scheduleWorker =
            synchronized(this) {
                pendingRequest = request
                if (workerScheduled) {
                    false
                } else {
                    workerScheduled = true
                    true
                }
            }
        if (scheduleWorker) worker.execute(::runPendingLookups)
    }

    private fun runPendingLookups() {
        while (true) {
            val request =
                synchronized(this) {
                    pendingRequest?.also { pendingRequest = null } ?: run {
                        workerScheduled = false
                        return
                    }
                }
            val result =
                runCatching { repository.findNearest(request.position, request.reload) }
                    .mapCatching { city -> city?.let { present(it, request.position) } }
            callbackExecutor.execute {
                synchronized(this) {
                    if (!active || session != request.session || fix != request.fix) return@synchronized
                    onStateChanged(result.getOrNull() ?: NearbyCityState.Unavailable)
                }
            }
        }
    }

    private fun present(
        city: NearbyCityRecord,
        position: NearbyCoordinate,
    ): NearbyCityState.Available {
        val zone = ZoneId.of(city.timeZoneId)
        val local = clock().atZone(zone)
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault()).format(local)
        return NearbyCityState.Available(
            city.name,
            city.country,
            distanceKilometres(position, NearbyCoordinate(city.latitude, city.longitude)),
            time,
            local.offset.totalSeconds,
        )
    }

    private data class LookupRequest(
        val session: Long,
        val fix: Long,
        val position: NearbyCoordinate,
        val reload: Boolean,
    )
}

internal fun distanceKilometres(
    first: NearbyCoordinate,
    second: NearbyCoordinate,
): Double {
    val latitudeDelta = Math.toRadians(second.latitude - first.latitude)
    val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
    val a =
        kotlin.math.sin(latitudeDelta / 2).let { it * it } +
            kotlin.math.cos(Math.toRadians(first.latitude)) * kotlin.math.cos(Math.toRadians(second.latitude)) *
            kotlin.math.sin(longitudeDelta / 2).let { it * it }
    return (2 * 6_371.0088 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))).takeIf(Double::isFinite)
        ?: throw IllegalArgumentException("Invalid distance")
}
