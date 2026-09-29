package kniezrec.com.flightinfo.route.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.NearbyCoordinate
import kniezrec.com.flightinfo.nearby.data.CityRepository
import kniezrec.com.flightinfo.route.RouteEndpoint
import kniezrec.com.flightinfo.route.RoutePickerError
import kniezrec.com.flightinfo.route.RoutePickerState
import kniezrec.com.flightinfo.route.data.RouteRepository
import kniezrec.com.flightinfo.route.validCity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State holder of the city picker: search by name, nearest city to a map long-press, selection and
 * confirmation of a route endpoint.
 *
 * - One lookup runs at a time: a new search, long-press or retry cancels the running one, and
 *   closing or reopening the picker cancels it too, so a late result is never shown.
 * - [retry] runs the last lookup again after copying the city data again.
 * - The open endpoint, the typed query and the selected city id are kept in [SavedStateHandle]
 *   (the lookup results are not: they can be large), so the picker survives a configuration change
 *   and process death; after process death the selection is read again from the city data.
 * - [state] is updated synchronously by the input actions, so a text field can show [RoutePickerState.query]
 *   without lagging behind the typing.
 *
 * A separate ViewModel rather than a part of [RouteViewModel]: the picker has its own lifetime
 * (open/closed), saved input and lookup job, and shares only [RouteRepository] with the card.
 */
@HiltViewModel
class RoutePickerViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val cityRepository: CityRepository,
        private val routeRepository: RouteRepository,
    ) : ViewModel() {
        private val mutableState =
            MutableStateFlow(
                RoutePickerState(
                    endpoint =
                        savedStateHandle.get<String>(KEY_ENDPOINT)?.let { name ->
                            RouteEndpoint.entries.firstOrNull { it.name == name }
                        },
                    query = savedStateHandle.get<String>(KEY_QUERY).orEmpty(),
                ),
            )

        val state: StateFlow<RoutePickerState> = mutableState.asStateFlow()

        private var lookupJob: Job? = null
        private var selectionJob: Job? = null
        private var lastLookup: Lookup? = null

        init {
            val savedSelection = savedStateHandle.get<Long>(KEY_SELECTED_ID)
            if (mutableState.value.endpoint != null && savedSelection != null) restoreSelection(savedSelection)
        }

        /** Opens the picker for [endpoint], with the endpoint's current city selected. */
        fun open(endpoint: RouteEndpoint) {
            cancelJobs()
            mutableState.value = RoutePickerState(endpoint = endpoint)
            savedStateHandle[KEY_ENDPOINT] = endpoint.name
            savedStateHandle[KEY_QUERY] = ""
            val currentId = routeRepository.endpoints.value[endpoint]
            savedStateHandle[KEY_SELECTED_ID] = currentId
            currentId?.let { restoreSelection(it) }
        }

        /** Closes the picker without saving anything; a lookup still running is dropped. */
        fun close() {
            cancelJobs()
            mutableState.value = RoutePickerState()
            savedStateHandle.remove<String>(KEY_ENDPOINT)
            savedStateHandle.remove<String>(KEY_QUERY)
            savedStateHandle.remove<Long>(KEY_SELECTED_ID)
        }

        fun updateQuery(query: String) {
            if (mutableState.value.endpoint == null) return
            mutableState.update { it.copy(query = query) }
            savedStateHandle[KEY_QUERY] = query
        }

        /** Cities whose name contains [query] (see [CityRepository.search]). */
        fun search(query: String) {
            startLookup(Lookup.Search(query), reload = false)
        }

        /**
         * The city nearest to [coordinate] becomes the only result and the selection. A null
         * [coordinate] (the long-press did not hit a valid point) finds no city.
         */
        fun nearest(coordinate: NearbyCoordinate?) {
            if (mutableState.value.endpoint == null) return
            if (coordinate == null) {
                lookupJob?.cancel()
                mutableState.update { it.copy(results = emptyList(), loading = false, error = RoutePickerError.NoCityAtLocation) }
                return
            }
            startLookup(Lookup.Nearest(coordinate), reload = false)
        }

        /** Runs the last lookup again, copying the city data again first. */
        fun retry() {
            lastLookup?.let { startLookup(it, reload = true) }
        }

        fun select(city: NearbyCityRecord) {
            if (mutableState.value.endpoint == null) return
            setSelected(city)
        }

        /**
         * Saves the selected city as the open endpoint and closes the picker; false (and nothing
         * changes) when no valid city is selected.
         */
        fun confirm(): Boolean {
            val current = mutableState.value
            val endpoint = current.endpoint ?: return false
            val city = current.selected ?: return false
            if (!validCity(city)) return false
            viewModelScope.launch { routeRepository.set(endpoint, city.id) }
            close()
            return true
        }

        private fun startLookup(
            lookup: Lookup,
            reload: Boolean,
        ) {
            if (mutableState.value.endpoint == null) return
            lastLookup = lookup
            lookupJob?.cancel()
            mutableState.update { it.copy(loading = true, error = null) }
            lookupJob =
                viewModelScope.launch {
                    val outcome =
                        try {
                            if (reload) cityRepository.reload()
                            Result.success(lookup.run(cityRepository))
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (failure: Exception) {
                            Result.failure(failure)
                        }
                    outcome.fold(
                        onSuccess = { found -> onFound(lookup, found) },
                        onFailure = { mutableState.update { it.copy(loading = false, error = RoutePickerError.SearchFailed) } },
                    )
                }
        }

        private fun onFound(
            lookup: Lookup,
            found: List<NearbyCityRecord>,
        ) {
            when (lookup) {
                is Lookup.Search -> mutableState.update { it.copy(results = found, loading = false) }
                is Lookup.Nearest -> {
                    val city = found.firstOrNull()
                    when {
                        city == null ->
                            mutableState.update {
                                it.copy(
                                    results = emptyList(),
                                    loading = false,
                                    error = RoutePickerError.NoCityAtLocation,
                                )
                            }
                        !validCity(city) ->
                            mutableState.update { it.copy(results = emptyList(), loading = false, error = RoutePickerError.InvalidCity) }
                        else -> {
                            mutableState.update { it.copy(results = listOf(city), loading = false) }
                            setSelected(city)
                        }
                    }
                }
            }
        }

        private fun setSelected(city: NearbyCityRecord) {
            // An explicit choice wins over the current city still being read.
            selectionJob?.cancel()
            mutableState.update { it.copy(selected = city) }
            savedStateHandle[KEY_SELECTED_ID] = city.id
        }

        /** Reads the city [id] and selects it if it is valid; otherwise nothing is selected. */
        private fun restoreSelection(id: Long) {
            selectionJob =
                viewModelScope.launch {
                    val city =
                        try {
                            cityRepository.byId(id)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            null
                        }
                    if (city != null && validCity(city)) {
                        mutableState.update { it.copy(selected = city) }
                    } else {
                        savedStateHandle.remove<Long>(KEY_SELECTED_ID)
                    }
                }
        }

        private fun cancelJobs() {
            lookupJob?.cancel()
            lookupJob = null
            selectionJob?.cancel()
            selectionJob = null
            lastLookup = null
        }

        private sealed interface Lookup {
            suspend fun run(cities: CityRepository): List<NearbyCityRecord>

            data class Search(
                val query: String,
            ) : Lookup {
                override suspend fun run(cities: CityRepository): List<NearbyCityRecord> = cities.search(query)
            }

            data class Nearest(
                val coordinate: NearbyCoordinate,
            ) : Lookup {
                override suspend fun run(cities: CityRepository): List<NearbyCityRecord> = listOfNotNull(cities.nearest(coordinate))
            }
        }

        private companion object {
            const val KEY_ENDPOINT = "route_picker_endpoint"
            const val KEY_QUERY = "route_picker_query"
            const val KEY_SELECTED_ID = "route_picker_selected_id"
        }
    }
