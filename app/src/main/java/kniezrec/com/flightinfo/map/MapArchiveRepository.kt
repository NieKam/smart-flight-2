package kniezrec.com.flightinfo.map

import android.content.Context
import java.io.File
import java.io.FileOutputStream
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
                        archive.delete()
                        temporary.delete()
                        context.assets.open("osmdroid.zip").use { input ->
                            FileOutputStream(temporary).use { output -> input.copyTo(output) }
                        }
                        if (!temporary.renameTo(archive)) error("Could not commit offline map archive")
                    }
                    require(archive.isFile && archive.length() > 0L) { "Offline map archive is empty" }
                    ZipFile(archive).use { require(it.entries().hasMoreElements()) { "Offline map archive is corrupt" } }
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
