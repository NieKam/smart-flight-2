package kniezrec.com.flightinfo.testutil

import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.data.CityDataSource

/** [CityDataSource] over a fixed list of cities. */
class ListCityDataSource(
    private val cities: List<NearbyCityRecord>,
) : CityDataSource {
    override fun readAll(reextract: Boolean): List<NearbyCityRecord> = cities
}
