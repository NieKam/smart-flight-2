package kniezrec.com.flightinfo.map

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.Executors
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process singleton: its single worker thread lives as long as the process and is shared by every
 * Activity instance, so callers must not shut it down.
 */
@Singleton
class MapArchiveRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
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
    }
