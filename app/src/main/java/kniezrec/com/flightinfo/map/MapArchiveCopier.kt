package kniezrec.com.flightinfo.map

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

/** Copies and validates an archive without exposing a partial destination. */
internal object MapArchiveCopier {
    fun copy(
        openSource: () -> InputStream,
        destination: File,
        temporary: File,
    ): Result<File> =
        runCatching {
            temporary.delete()
            destination.parentFile?.mkdirs()
            openSource().use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            validate(temporary)
            moveIntoPlace(temporary, destination)
            validate(destination)
            destination
        }.onFailure {
            temporary.delete()
        }

    fun validate(file: File) {
        require(file.isFile && file.length() > 0L) { "Offline map archive is empty" }
        ZipFile(file).use { zip -> require(zip.entries().hasMoreElements()) { "Offline map archive is corrupt" } }
    }

    private fun moveIntoPlace(
        source: File,
        destination: File,
    ) {
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            require(source.renameTo(destination)) { "Could not commit offline map archive" }
        }
    }
}

internal class MapLoadAttemptGate {
    private var attempt = 0L

    fun begin(): Long = ++attempt

    fun isCurrent(token: Long): Boolean = token == attempt
}
