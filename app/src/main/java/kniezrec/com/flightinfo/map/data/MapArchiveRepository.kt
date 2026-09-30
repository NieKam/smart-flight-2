package kniezrec.com.flightinfo.map.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kniezrec.com.flightinfo.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** The offline map archive (`osmdroid.zip`), copied out of the app's assets into [directory]. */
@Singleton
class MapArchiveRepository internal constructor(
    private val openAsset: () -> InputStream,
    directory: File,
    private val ioDispatcher: CoroutineDispatcher,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this({ context.assets.open(ARCHIVE_NAME) }, context.cacheDir, ioDispatcher)

    private val archive = File(directory, ARCHIVE_NAME)
    private val temporary = File(directory, "$ARCHIVE_NAME.partial")

    // One preparation at a time: a cancelled one keeps writing until its blocking copy ends, and a
    // new one must not share the temporary file with it.
    private val mutex = Mutex()

    /**
     * The archive, ready to open. An existing archive that is a readable, non-empty zip is reused;
     * otherwise (missing, unreadable, empty) it is copied again from the assets. Throws when the
     * copy fails or is unusable; no partial file is left behind.
     */
    suspend fun prepare(): File =
        mutex.withLock {
            withContext(ioDispatcher) {
                try {
                    if (!isUsable(archive)) {
                        MapArchiveCopier.copy(openSource = openAsset, destination = archive, temporary = temporary).getOrThrow()
                    }
                    archive
                } catch (failure: Exception) {
                    temporary.delete()
                    throw failure
                }
            }
        }

    private fun isUsable(file: File): Boolean = runCatching { MapArchiveCopier.validate(file) }.isSuccess

    private companion object {
        const val ARCHIVE_NAME = "osmdroid.zip"
    }
}
