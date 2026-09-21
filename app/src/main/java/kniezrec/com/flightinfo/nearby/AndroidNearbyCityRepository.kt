package kniezrec.com.flightinfo.nearby

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.FileOutputStream

/** Read-only platform boundary for the immutable legacy city asset. Call only from a worker. */
internal class AndroidNearbyCityRepository(
    private val context: Context,
) : NearbyCityRepository {
    override fun findNearest(
        position: NearbyCoordinate,
        reload: Boolean,
    ): NearbyCityRecord? {
        val databaseFile = copyAsset(reload)
        SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database
                .rawQuery(
                    "SELECT _id, city, latitude, longitude, timezone, country FROM cities_info ORDER BY _id",
                    null,
                ).use { cursor ->
                    var nearest: NearbyCityRecord? = null
                    var shortest = Double.POSITIVE_INFINITY
                    while (cursor.moveToNext()) {
                        val record =
                            NearbyCityRecord(
                                cursor.getLong(0),
                                cursor.getString(1),
                                cursor.getString(5),
                                cursor.getDouble(2),
                                cursor.getDouble(3),
                                cursor.getString(4),
                            )
                        val coordinate = NearbyCoordinate.from(record.latitude, record.longitude) ?: continue
                        val distance = distanceKilometres(position, coordinate)
                        if (distance < shortest) {
                            nearest = record
                            shortest = distance
                        }
                    }
                    return nearest
                }
        }
    }

    private fun copyAsset(reload: Boolean): File {
        val target = File(context.filesDir, "nearby-city/cities_info.db")
        if (reload || !target.isFile) {
            target.parentFile?.mkdirs()
            val replacement = File(target.parentFile, "${target.name}.tmp")
            context.assets.open(ASSET_PATH).use { input ->
                FileOutputStream(replacement).use(input::copyTo)
            }
            if (!replacement.renameTo(target)) {
                replacement.delete()
                throw IllegalStateException("Unable to replace nearby city database")
            }
        }
        return target
    }

    private companion object {
        const val ASSET_PATH = "databases/cities_info.db"
    }
}
