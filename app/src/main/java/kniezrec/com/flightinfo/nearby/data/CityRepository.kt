package kniezrec.com.flightinfo.nearby.data

import kniezrec.com.flightinfo.di.IoDispatcher
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.NearbyCoordinate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the bundled cities.
 *
 * The table (about 47,000 rows) is read once, on first use, and kept in memory with a spatial index;
 * later queries do not touch the database. A failed read is not cached: the next query reads again.
 * [reload] copies the asset again (for a corrupt copy) and replaces the cached cities.
 */
@Singleton
class CityRepository
    @Inject
    constructor(
        private val dataSource: CityDataSource,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        private val mutex = Mutex()

        // Guarded by mutex.
        private var loaded: LoadedCities? = null

        /** The city nearest to [position] by great-circle distance; null when there are no cities. */
        suspend fun nearest(position: NearbyCoordinate): NearbyCityRecord? = withContext(ioDispatcher) { cities().index.nearest(position) }

        /** Cities whose name contains the trimmed [query], ignoring case, in id order; none for a blank query. */
        suspend fun search(query: String): List<NearbyCityRecord> {
            val normalized = query.trim()
            if (normalized.isEmpty()) return emptyList()
            return withContext(ioDispatcher) {
                cities().records.filter { it.name.trim().contains(normalized, ignoreCase = true) }
            }
        }

        suspend fun byId(id: Long): NearbyCityRecord? = withContext(ioDispatcher) { cities().byId[id] }

        /** Copies the asset again and reads it; throws when that fails (then the next query reads again). */
        suspend fun reload() {
            withContext(ioDispatcher) {
                mutex.withLock {
                    loaded = null
                    loaded = LoadedCities(dataSource.readAll(reextract = true))
                }
            }
        }

        private suspend fun cities(): LoadedCities =
            mutex.withLock {
                loaded ?: LoadedCities(dataSource.readAll(reextract = false)).also { loaded = it }
            }

        private class LoadedCities(
            val records: List<NearbyCityRecord>,
        ) {
            val byId: Map<Long, NearbyCityRecord> = records.associateBy { it.id }
            val index = CitySpatialIndex(records)
        }
    }
