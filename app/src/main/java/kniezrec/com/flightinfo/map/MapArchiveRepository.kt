package kniezrec.com.flightinfo.map

import android.content.Context
import java.io.File
import java.util.concurrent.Executors
import java.util.zip.ZipFile

internal class MapArchiveRepository(
    private val context: Context,
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val archive = File(context.cacheDir, "osmdroid.zip")
    private val temporary = File(context.cacheDir, "osmdroid.zip.partial")

    fun prepare(onResult: (Result<File>) -> Unit) {
        executor.execute {
            val result =
                runCatching {
                    val existingIsUsable =
                        archive.isFile &&
                            runCatching {
                                ZipFile(archive).use { require(it.entries().hasMoreElements()) }
                            }.isSuccess
                    if (!existingIsUsable) {
                        MapArchiveCopier
                            .copy(
                                openSource = { context.assets.open("osmdroid.zip") },
                                destination = archive,
                                temporary = temporary,
                            ).getOrThrow()
                    }
                    MapArchiveCopier.validate(archive)
                    archive
                }
            if (result.isFailure) temporary.delete()
            onResult(result)
        }
    }

    fun close() {
        executor.shutdownNow()
    }
}
