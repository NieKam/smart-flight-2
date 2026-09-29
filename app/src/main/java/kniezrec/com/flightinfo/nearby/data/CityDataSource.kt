package kniezrec.com.flightinfo.nearby.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kniezrec.com.flightinfo.data.AssetExtractor
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject

/** Platform boundary for the bundled, read-only city table. */
interface CityDataSource {
    /**
     * Reads every city, ordered by id. With [reextract] the database file is copied from the asset
     * again first. Blocking; throws when the data cannot be read.
     */
    fun readAll(reextract: Boolean): List<NearbyCityRecord>
}

/**
 * [CityDataSource] over the `databases/cities_info.db` asset. The asset is copied into
 * `filesDir/cities/` (SQLite cannot open an asset directly) once, or again when the copy is
 * missing or is not an SQLite file, or on request.
 */
internal class AndroidCityDataSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : CityDataSource {
        private val database = File(context.filesDir, "cities/cities_info.db")
        private val temporary = File(context.filesDir, "cities/cities_info.db.partial")

        override fun readAll(reextract: Boolean): List<NearbyCityRecord> {
            if (reextract || runCatching { requireSqliteDatabase(database) }.isFailure) {
                AssetExtractor
                    .extract(
                        openSource = { context.assets.open(ASSET_PATH) },
                        destination = database,
                        temporary = temporary,
                        validate = ::requireSqliteDatabase,
                    ).getOrThrow()
            }
            return SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db
                    .rawQuery("SELECT _id, city, latitude, longitude, timezone, country FROM cities_info ORDER BY _id", null)
                    .use { cursor ->
                        // Countries and zones repeat across tens of thousands of rows; keep one copy of each.
                        val shared = HashMap<String, String>()
                        val records = ArrayList<NearbyCityRecord>(cursor.count)
                        while (cursor.moveToNext()) {
                            records +=
                                NearbyCityRecord(
                                    id = cursor.getLong(0),
                                    name = cursor.getString(1),
                                    country = cursor.getString(5).let { shared.getOrPut(it) { it } },
                                    latitude = cursor.getDouble(2),
                                    longitude = cursor.getDouble(3),
                                    timeZoneId = cursor.getString(4).let { shared.getOrPut(it) { it } },
                                )
                        }
                        records
                    }
            }
        }

        private companion object {
            const val ASSET_PATH = "databases/cities_info.db"
        }
    }

private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

/** Throws unless [file] is a non-empty file starting with the 16-byte SQLite header. */
internal fun requireSqliteDatabase(file: File) {
    require(file.isFile && file.length() > 0L) { "City database is missing or empty" }
    require(file.length() >= SQLITE_HEADER.size) { "City database is not an SQLite file" }
    val header = ByteArray(SQLITE_HEADER.size)
    RandomAccessFile(file, "r").use { it.readFully(header) }
    require(header.contentEquals(SQLITE_HEADER)) { "City database is not an SQLite file" }
}
