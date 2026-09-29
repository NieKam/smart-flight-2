package kniezrec.com.flightinfo.data

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files

class AssetExtractorTest {
    private val directory = Files.createTempDirectory("asset-extractor-test").toFile()
    private val destination = directory.resolve("nested/asset.bin")
    private val temporary = directory.resolve("nested/asset.bin.partial")

    @After fun tearDown() {
        directory.deleteRecursively()
    }

    @Test fun `valid copy is committed and the temporary file is gone`() {
        val result = extract(byteArrayOf(1, 2, 3)) { require(it.length() == 3L) }

        assertTrue(result.isSuccess)
        assertArrayEquals(byteArrayOf(1, 2, 3), destination.readBytes())
        assertFalse(temporary.exists())
    }

    @Test fun `failed validation keeps the previous destination and removes the partial copy`() {
        extract(byteArrayOf(1)) {}.getOrThrow()

        val result = extract(byteArrayOf(9, 9)) { throw IllegalArgumentException("corrupt") }

        assertTrue(result.isFailure)
        assertArrayEquals(byteArrayOf(1), destination.readBytes())
        assertFalse(temporary.exists())
    }

    @Test fun `interrupted copy exposes nothing and a retry succeeds`() {
        val failed =
            AssetExtractor.extract(
                openSource = {
                    object : ByteArrayInputStream(byteArrayOf(1, 2, 3)) {
                        override fun close() = throw IllegalStateException("interrupted copy")
                    }
                },
                destination = destination,
                temporary = temporary,
                validate = {},
            )
        assertTrue(failed.isFailure)
        assertFalse(destination.exists())
        assertFalse(temporary.exists())

        assertTrue(extract(byteArrayOf(4)) {}.isSuccess)
        assertTrue(destination.isFile)
    }

    private fun extract(
        bytes: ByteArray,
        validate: (java.io.File) -> Unit,
    ): Result<java.io.File> = AssetExtractor.extract({ ByteArrayInputStream(bytes) }, destination, temporary, validate)

    private fun sqliteBytes(): ByteArray = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(84)
}
