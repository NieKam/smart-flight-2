package kniezrec.com.flightinfo.map.data

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class MapArchiveRepositoryTest {
    private val directory: File = Files.createTempDirectory("map-archive-repository").toFile()
    private val archive = File(directory, "osmdroid.zip")
    private val temporary = File(directory, "osmdroid.zip.partial")
    private var opens = 0

    @After fun tearDown() {
        directory.deleteRecursively()
    }

    @Test fun `a usable existing archive is reused without copying`() =
        runTest {
            archive.writeBytes(zipWith("existing.jpg"))

            val prepared = repository { ByteArrayInputStream(zipWith("asset.jpg")) }.prepare()

            assertEquals(archive, prepared)
            assertEquals(0, opens)
            assertEquals(listOf("existing.jpg"), entries(prepared))
        }

    @Test fun `a missing archive is copied from the assets`() =
        runTest {
            val prepared = repository { ByteArrayInputStream(zipWith("asset.jpg")) }.prepare()

            assertEquals(1, opens)
            assertEquals(listOf("asset.jpg"), entries(prepared))
            assertFalse(temporary.exists())
        }

    @Test fun `a corrupt or empty existing archive is copied again`() =
        runTest {
            val repository = repository { ByteArrayInputStream(zipWith("asset.jpg")) }

            archive.writeText("not a zip archive")
            assertEquals(listOf("asset.jpg"), entries(repository.prepare()))

            archive.writeBytes(zipWith())
            assertEquals(listOf("asset.jpg"), entries(repository.prepare()))

            assertEquals(2, opens)
        }

    @Test fun `a failed copy throws and leaves neither a temporary nor an archive`() =
        runTest {
            val interrupted =
                repository {
                    object : ByteArrayInputStream(zipWith("asset.jpg")) {
                        override fun close() = throw IllegalStateException("interrupted copy")
                    }
                }

            assertPrepareFails(interrupted)
            assertFalse(temporary.exists())
            assertFalse(archive.exists())
        }

    @Test fun `an unreadable asset throws and a later prepare copies again`() =
        runTest {
            var assetBytes = "not a zip archive".toByteArray()
            val repository = repository { ByteArrayInputStream(assetBytes) }

            assertPrepareFails(repository)
            assertFalse(temporary.exists())
            assertFalse(archive.exists())

            assetBytes = zipWith("asset.jpg")
            assertEquals(listOf("asset.jpg"), entries(repository.prepare()))
        }

    private fun TestScope.repository(openAsset: () -> InputStream) =
        MapArchiveRepository(
            openAsset = {
                opens++
                openAsset()
            },
            directory = directory,
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

    private suspend fun assertPrepareFails(repository: MapArchiveRepository) {
        val result = runCatching { repository.prepare() }
        assertTrue("prepare should fail", result.isFailure)
    }

    private fun entries(file: File): List<String> = ZipFile(file).use { zip -> zip.entries().toList().map { it.name } }

    private fun zipWith(vararg names: String): ByteArray =
        ByteArrayOutputStream()
            .also { bytes ->
                ZipOutputStream(bytes).use { zip ->
                    names.forEach { name ->
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(byteArrayOf(0))
                        zip.closeEntry()
                    }
                }
            }.toByteArray()
}
