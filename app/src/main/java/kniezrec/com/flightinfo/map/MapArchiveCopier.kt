package kniezrec.com.flightinfo.map

import kniezrec.com.flightinfo.data.AssetExtractor
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/** Copies and validates an archive without exposing a partial destination (see [AssetExtractor]). */
internal object MapArchiveCopier {
    fun copy(
        openSource: () -> InputStream,
        destination: File,
        temporary: File,
    ): Result<File> = AssetExtractor.extract(openSource, destination, temporary, ::validate)

    fun validate(file: File) {
        require(file.isFile && file.length() > 0L) { "Offline map archive is empty" }
        ZipFile(file).use { zip -> require(zip.entries().hasMoreElements()) { "Offline map archive is corrupt" } }
    }
}
