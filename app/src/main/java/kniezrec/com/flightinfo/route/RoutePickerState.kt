package kniezrec.com.flightinfo.route

import kniezrec.com.flightinfo.nearby.NearbyCityRecord

/**
 * State of the city picker. The picker is open while [endpoint] is not null.
 *
 * [query] is the text typed in the search field; [results] are the cities of the last completed
 * lookup (a text search, or the one city found by a map long-press). [selected] is the city that
 * Confirm saves as [endpoint]; it starts as the endpoint's current city.
 */
data class RoutePickerState(
    val endpoint: RouteEndpoint? = null,
    val query: String = "",
    val results: List<NearbyCityRecord> = emptyList(),
    val loading: Boolean = false,
    val error: RoutePickerError? = null,
    val selected: NearbyCityRecord? = null,
) {
    /** A city is selected, but its coordinates or time zone are not usable. */
    val selectionInvalid: Boolean get() = selected != null && !validCity(selected)

    val canConfirm: Boolean get() = selected != null && !selectionInvalid && !loading
}

/** Why the last picker lookup found nothing to show; the UI maps each to a message. */
sealed interface RoutePickerError {
    /** The city data could not be read; the lookup can be retried. */
    data object SearchFailed : RoutePickerError

    /** A map long-press found no city (or did not hit a valid coordinate). */
    data object NoCityAtLocation : RoutePickerError

    /** A map long-press found a city whose record is not usable. */
    data object InvalidCity : RoutePickerError
}
