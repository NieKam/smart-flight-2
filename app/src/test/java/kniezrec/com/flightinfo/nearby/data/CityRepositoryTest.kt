package kniezrec.com.flightinfo.nearby.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.NearbyCoordinate
import kniezrec.com.flightinfo.nearby.distanceKilometres
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class CityRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun `the table is read once and later queries do not touch the data source`() =
        runTest {
            val source = FakeCityDataSource(listOf(city(1, "Near", 0.0, 0.0), city(2, "Far", 40.0, 40.0)))
            val repository = repository(source)

            repeat(5) { assertEquals("Near", repository.nearest(NearbyCoordinate(0.1, 0.1))?.name) }
            assertEquals("Far", repository.byId(2)?.name)
            assertEquals(listOf("Far"), repository.search("fa").map { it.name })

            assertEquals(listOf(false), source.reads)
        }

    @Test fun `a failed read is not cached and the next query reads again`() =
        runTest {
            val source = FakeCityDataSource(listOf(city(1, "City", 0.0, 0.0)), failuresLeft = 1)
            val repository = repository(source)

            assertTrue(runCatching { repository.nearest(NearbyCoordinate(0.0, 0.0)) }.isFailure)
            assertEquals("City", repository.nearest(NearbyCoordinate(0.0, 0.0))?.name)
            assertEquals(listOf(false, false), source.reads)
        }

    @Test fun `reload copies the asset again and replaces the cached cities`() =
        runTest {
            val source = FakeCityDataSource(listOf(city(1, "Old", 0.0, 0.0)))
            val repository = repository(source)
            assertEquals("Old", repository.byId(1)?.name)

            source.cities = listOf(city(1, "New", 0.0, 0.0))
            repository.reload()

            assertEquals("New", repository.byId(1)?.name)
            assertEquals("New", repository.nearest(NearbyCoordinate(0.0, 0.0))?.name)
            assertEquals(listOf(false, true), source.reads)
        }

    @Test fun `a failed reload throws and the next query reads again`() =
        runTest {
            val source = FakeCityDataSource(listOf(city(1, "City", 0.0, 0.0)))
            val repository = repository(source)
            repository.byId(1)

            source.failuresLeft = 1
            assertTrue(runCatching { repository.reload() }.isFailure)

            assertEquals("City", repository.byId(1)?.name)
            assertEquals(listOf(false, true, false), source.reads)
        }

    @Test fun `search trims the query, ignores case and keeps id order`() =
        runTest {
            val source =
                FakeCityDataSource(
                    listOf(
                        city(1, "Omaha", 0.0, 0.0),
                        city(2, " BETA town ", 0.0, 0.0),
                        city(3, "Gamma", 0.0, 0.0),
                        city(4, "Beta", 0.0, 0.0),
                    ),
                )
            val repository = repository(source)

            assertEquals(listOf(2L, 4L), repository.search("  bEtA  ").map { it.id })
            assertEquals(listOf(1L, 3L), repository.search("mA").map { it.id })
            assertEquals(emptyList<NearbyCityRecord>(), repository.search("zeta"))
        }

    @Test fun `a blank search returns nothing without reading`() =
        runTest {
            val source = FakeCityDataSource(listOf(city(1, "City", 0.0, 0.0)))
            val repository = repository(source)

            assertEquals(emptyList<NearbyCityRecord>(), repository.search("   "))
            assertEquals(emptyList<Boolean>(), source.reads)
        }

    @Test fun `unknown id and empty table give no city`() =
        runTest {
            val repository = repository(FakeCityDataSource(emptyList()))

            assertNull(repository.byId(1))
            assertNull(repository.nearest(NearbyCoordinate(0.0, 0.0)))
        }

    /**
     * Real asset: the indexed lookup equals the brute-force haversine minimum. Also prints the row
     * count, the one-time load time and the lookup times (reported in the TASK-011 PR).
     */
    @Test fun `indexed nearest equals brute force on the real city table`() =
        runTest(timeout = 5.minutes) {
            val records = AndroidCityDataSource(context).readAll(reextract = false)
            val repository = repository(AndroidCityDataSource(context))

            val loadStart = System.nanoTime()
            repository.byId(Long.MIN_VALUE)
            val loadMillis = (System.nanoTime() - loadStart) / 1_000_000.0

            val random = Random(20260929)
            val points =
                edgeCases() +
                    List(1_000) {
                        NearbyCoordinate(Math.toDegrees(kotlin.math.asin(random.nextDouble(-1.0, 1.0))), random.nextDouble(-180.0, 180.0))
                    } +
                    // Around real cities too, where they are densest.
                    List(200) {
                        val city = records[random.nextInt(records.size)]
                        NearbyCoordinate(
                            (city.latitude + random.nextDouble(-0.2, 0.2)).coerceIn(-90.0, 90.0),
                            (city.longitude + random.nextDouble(-0.2, 0.2)).coerceIn(-180.0, 180.0),
                        )
                    }
            val lookupNanos = LongArray(points.size)
            points.forEachIndexed { index, position ->
                val start = System.nanoTime()
                val indexed = repository.nearest(position)
                lookupNanos[index] = System.nanoTime() - start
                val expected = records.minByOrNull { distanceKilometres(position, NearbyCoordinate(it.latitude, it.longitude)) }
                assertEquals("at $position", expected, indexed)
            }

            lookupNanos.sort()
            val medianMicros = lookupNanos[lookupNanos.size / 2] / 1_000.0
            val maxMicros = lookupNanos.last() / 1_000.0
            println(
                "CITY_METRICS rows=${records.size} loadMs=${"%.1f".format(loadMillis)} " +
                    "lookups=${points.size} medianLookupUs=${"%.1f".format(medianMicros)} maxLookupUs=${"%.1f".format(maxMicros)}",
            )
            assertTrue(records.isNotEmpty())
            // Generous bound only; the printed numbers are the measurement.
            assertTrue("median lookup $medianMicros µs", medianMicros < 50_000.0)
        }

    @Test fun `the real table finds Warsaw at Warsaw`() =
        runTest {
            val repository = repository(AndroidCityDataSource(context))

            assertEquals("Warsaw", repository.nearest(NearbyCoordinate(52.22977, 21.01178))?.name)
            assertEquals("Warsaw", repository.byId(WARSAW_ID)?.name)
            assertTrue(repository.search("warsaw").any { it.id == WARSAW_ID })
        }

    @Test fun `a corrupt copy is replaced on first read and on reload`() =
        runTest {
            val copy = File(context.filesDir, "cities/cities_info.db")
            copy.parentFile!!.mkdirs()
            copy.writeText("not a database")

            val repository = repository(AndroidCityDataSource(context))
            assertEquals("Warsaw", repository.byId(WARSAW_ID)?.name)
            requireSqliteDatabase(copy)

            copy.writeText("corrupted later")
            repository.reload()
            requireSqliteDatabase(copy)
            assertEquals("Warsaw", repository.byId(WARSAW_ID)?.name)
        }

    @Test fun `the same repository instance answers from memory after the copy disappears`() =
        runTest {
            val repository = repository(AndroidCityDataSource(context))
            val warsaw = repository.byId(WARSAW_ID)

            File(context.filesDir, "cities").deleteRecursively()

            assertSame(warsaw, repository.byId(WARSAW_ID))
            if (File(context.filesDir, "cities/cities_info.db").exists()) fail("A query should not touch the file")
        }

    private fun TestScope.repository(source: CityDataSource) = CityRepository(source, StandardTestDispatcher(testScheduler))

    private fun edgeCases(): List<NearbyCoordinate> =
        listOf(
            NearbyCoordinate(90.0, 0.0),
            NearbyCoordinate(-90.0, 0.0),
            NearbyCoordinate(89.99, 179.99),
            NearbyCoordinate(-89.99, -179.99),
            NearbyCoordinate(0.0, 180.0),
            NearbyCoordinate(0.0, -180.0),
            NearbyCoordinate(-17.0, 179.999),
            NearbyCoordinate(-17.0, -179.999),
            NearbyCoordinate(65.0, -179.5),
            NearbyCoordinate(65.0, 179.5),
            NearbyCoordinate(0.0, 0.0),
            // Point Nemo, far from any land.
            NearbyCoordinate(-48.8767, -123.3933),
            NearbyCoordinate(78.22334, 15.6),
            NearbyCoordinate(-54.8, -68.3),
        )

    private fun city(
        id: Long,
        name: String,
        latitude: Double,
        longitude: Double,
    ) = NearbyCityRecord(id, name, "Country", latitude, longitude, "UTC")

    private class FakeCityDataSource(
        var cities: List<NearbyCityRecord>,
        var failuresLeft: Int = 0,
    ) : CityDataSource {
        val reads = mutableListOf<Boolean>()

        override fun readAll(reextract: Boolean): List<NearbyCityRecord> {
            reads += reextract
            if (failuresLeft > 0) {
                failuresLeft--
                throw IllegalStateException("fake read failure")
            }
            return cities
        }
    }

    private companion object {
        const val WARSAW_ID = 31395L
    }
}
