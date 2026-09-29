package kniezrec.com.flightinfo.nearby.data

import kniezrec.com.flightinfo.nearby.EARTH_RADIUS_KILOMETRES
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.NearbyCoordinate
import kniezrec.com.flightinfo.nearby.distanceKilometres
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Nearest-city lookup that does not scan every city.
 *
 * Each city is placed on the unit sphere (a 3D unit vector) and the cube around the sphere is cut
 * into a uniform grid of cells. A query visits the cells around its own in growing cube shells.
 * Every city outside the shells visited so far is at least `ring * cellSize` away in a straight
 * line (the chord), and the chord grows with the great-circle distance, so the search stops as
 * soon as the best city found is closer than that. Working in 3D has no special cases at the poles
 * or across the antimeridian.
 *
 * Candidates are ranked by [distanceKilometres], ties by list order, so the result is exactly what
 * `cities.minByOrNull { distanceKilometres(position, it) }` returns. Cities with invalid
 * coordinates are left out.
 */
internal class CitySpatialIndex(
    records: List<NearbyCityRecord>,
    private val cellsPerAxis: Int = DEFAULT_CELLS_PER_AXIS,
) {
    private val cities: List<NearbyCityRecord> = records.filter { NearbyCoordinate.from(it.latitude, it.longitude) != null }
    private val x = DoubleArray(cities.size)
    private val y = DoubleArray(cities.size)
    private val z = DoubleArray(cities.size)
    private val cellSize = 2.0 / cellsPerAxis
    private val cells = arrayOfNulls<IntArray>(cellsPerAxis * cellsPerAxis * cellsPerAxis)

    init {
        require(cellsPerAxis > 0) { "cellsPerAxis must be positive" }
        val cellOfCity = IntArray(cities.size)
        val counts = IntArray(cells.size)
        cities.forEachIndexed { index, city ->
            val vector = unitVector(city.latitude, city.longitude)
            x[index] = vector[0]
            y[index] = vector[1]
            z[index] = vector[2]
            val cell = cellIndex(cell(vector[0]), cell(vector[1]), cell(vector[2]))
            cellOfCity[index] = cell
            counts[cell]++
        }
        val filled = IntArray(cells.size)
        // Ascending city indices in every cell keep ties in list order.
        cellOfCity.forEachIndexed { index, cell ->
            val members = cells[cell] ?: IntArray(counts[cell]).also { cells[cell] = it }
            members[filled[cell]++] = index
        }
    }

    fun nearest(position: NearbyCoordinate): NearbyCityRecord? {
        if (cities.isEmpty()) return null
        val query = unitVector(position.latitude, position.longitude)
        val search = Search(position, query)
        val centerX = cell(query[0])
        val centerY = cell(query[1])
        val centerZ = cell(query[2])
        for (ring in 0 until cellsPerAxis) {
            visitShell(centerX, centerY, centerZ, ring, search)
            // Unvisited cities are at least ring * cellSize away along one axis, hence as a chord.
            if (search.best >= 0 && search.bestChord < ring * cellSize - CHORD_MARGIN) break
        }
        return cities[search.best]
    }

    private fun visitShell(
        centerX: Int,
        centerY: Int,
        centerZ: Int,
        ring: Int,
        search: Search,
    ) {
        val last = cellsPerAxis - 1
        for (i in max(0, centerX - ring)..min(last, centerX + ring)) {
            for (j in max(0, centerY - ring)..min(last, centerY + ring)) {
                if (abs(i - centerX) == ring || abs(j - centerY) == ring) {
                    for (k in max(0, centerZ - ring)..min(last, centerZ + ring)) visitCell(i, j, k, search)
                } else {
                    if (centerZ - ring >= 0) visitCell(i, j, centerZ - ring, search)
                    if (ring > 0 && centerZ + ring <= last) visitCell(i, j, centerZ + ring, search)
                }
            }
        }
    }

    private fun visitCell(
        i: Int,
        j: Int,
        k: Int,
        search: Search,
    ) {
        val members = cells[cellIndex(i, j, k)] ?: return
        for (index in members) {
            val dx = x[index] - search.query[0]
            val dy = y[index] - search.query[1]
            val dz = z[index] - search.query[2]
            val chordSquared = dx * dx + dy * dy + dz * dz
            // Cheap rejection of cities clearly farther than the best one; the margin leaves near
            // ties to the exact comparison below.
            if (search.best >= 0 && chordSquared > search.bestChordSquared + CHORD_SQUARED_MARGIN) continue
            val city = cities[index]
            val distance = distanceKilometres(search.position, NearbyCoordinate(city.latitude, city.longitude))
            if (search.best < 0 || distance < search.bestKilometres || (distance == search.bestKilometres && index < search.best)) {
                search.best = index
                search.bestKilometres = distance
                search.bestChord = chordOf(distance)
                search.bestChordSquared = search.bestChord * search.bestChord
            }
        }
    }

    private fun cell(coordinate: Double): Int = floor((coordinate + 1.0) / cellSize).toInt().coerceIn(0, cellsPerAxis - 1)

    private fun cellIndex(
        i: Int,
        j: Int,
        k: Int,
    ): Int = (i * cellsPerAxis + j) * cellsPerAxis + k

    private class Search(
        val position: NearbyCoordinate,
        val query: DoubleArray,
    ) {
        var best = -1
        var bestKilometres = Double.POSITIVE_INFINITY
        var bestChord = Double.POSITIVE_INFINITY
        var bestChordSquared = Double.POSITIVE_INFINITY
    }

    private companion object {
        /** 64 cells per axis: a cell is about 200 km wide on the Earth's surface. */
        const val DEFAULT_CELLS_PER_AXIS = 64

        /** Far above floating-point error (about 6 mm on the Earth), far below a cell. */
        const val CHORD_MARGIN = 1e-9
        const val CHORD_SQUARED_MARGIN = 1e-9

        fun unitVector(
            latitude: Double,
            longitude: Double,
        ): DoubleArray {
            val phi = Math.toRadians(latitude)
            val lambda = Math.toRadians(longitude)
            return doubleArrayOf(cos(phi) * cos(lambda), cos(phi) * sin(lambda), sin(phi))
        }

        /** Straight-line distance on the unit sphere for a great-circle distance in kilometres. */
        fun chordOf(kilometres: Double): Double = 2.0 * sin(kilometres / (2.0 * EARTH_RADIUS_KILOMETRES))
    }
}
