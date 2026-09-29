package kniezrec.com.flightinfo.data

import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Copies a bundled asset to a file without ever exposing a partial or invalid destination: the
 * bytes go to [temporary] first, are checked by `validate` (which throws when the copy is unusable)
 * and only then replace the destination in one move. Blocking; call it from an IO thread.
 */
internal object AssetExtractor {
    fun extract(
        openSource: () -> InputStream,
        destination: File,
        temporary: File,
        validate: (File) -> Unit,
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
        } catch (_: AtomicMoveNotSupportedException) {
            require(source.renameTo(destination)) { "Could not commit ${destination.name}" }
        }
    }
}
