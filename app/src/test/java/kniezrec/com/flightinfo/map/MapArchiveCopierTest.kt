package kniezrec.com.flightinfo.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MapArchiveCopierTest {
    @Test
    fun failedCopyRemovesPartialFileAndRetryCommitsAtomically() {
        val directory = Files.createTempDirectory("map-archive-test").toFile()
        val destination = directory.resolve("osmdroid.zip")
        val temporary = directory.resolve("osmdroid.zip.partial")
        try {
            val failed =
                MapArchiveCopier.copy(
                    openSource = {
                        object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
                            override fun close() = throw IllegalStateException("interrupted copy")
                        }
                    },
                    destination = destination,
                    temporary = temporary,
                )
            assertTrue(failed.isFailure)
            assertFalse(temporary.exists())
            assertFalse(destination.exists())

            val retried =
                MapArchiveCopier.copy(
                    openSource = { ByteArrayInputStream(validArchive()) },
                    destination = destination,
                    temporary = temporary,
                )
            assertTrue(retried.isSuccess)
            assertTrue(destination.isFile)
            assertFalse(temporary.exists())
        } finally {
            destination.delete()
            temporary.delete()
            directory.delete()
        }
    }

    @Test
    fun staleArchiveAttemptIsIgnoredAfterRetryBegins() {
        val gate = MapLoadAttemptGate()
        val stale = gate.begin()
        val current = gate.begin()

        assertFalse(gate.isCurrent(stale))
        assertTrue(gate.isCurrent(current))
    }

    private fun validArchive(): ByteArray =
        ByteArrayOutputStream()
            .also { bytes ->
                ZipOutputStream(bytes).use { zip ->
                    zip.putNextEntry(ZipEntry("tile.jpg"))
                    zip.write(byteArrayOf(0))
                    zip.closeEntry()
                }
            }.toByteArray()
}
