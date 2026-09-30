package kniezrec.com.flightinfo.route.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.NearbyCoordinate
import kniezrec.com.flightinfo.route.RouteEndpoint
import kniezrec.com.flightinfo.route.RoutePickerError
import kniezrec.com.flightinfo.route.RoutePickerState
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

private val pickerMapSource = XYTileSource("MapquestOSM", 1, 6, 256, ".jpg", arrayOf())

/**
 * Stateless city picker for [state]'s endpoint; shows nothing while the picker is closed.
 * Long-press on the map asks for the nearest city ([onNearest] gets null for a point that is not a
 * valid coordinate).
 */
@Composable
fun RoutePicker(
    state: RoutePickerState,
    mapArchive: File?,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onNearest: (NearbyCoordinate?) -> Unit,
    onSelect: (NearbyCityRecord) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    val endpoint = state.endpoint ?: return
    BackHandler(onBack = onCancel)
    val titleDescription = stringResource(R.string.route_picker_title_description)
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(if (endpoint == RouteEndpoint.DEPARTURE) R.string.route_picker_departure else R.string.route_picker_destination),
            modifier = Modifier.semantics { contentDescription = titleDescription },
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.route_city_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(state.query) }),
        )
        Button(onClick = { onSearch(state.query) }, enabled = !state.loading, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.route_search))
        }
        Text(stringResource(R.string.route_map_instruction))
        if (state.loading) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator()
                Text(stringResource(R.string.route_searching))
            }
        }
        when (state.error) {
            RoutePickerError.SearchFailed -> {
                Text(stringResource(R.string.route_error))
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.route_retry)) }
            }
            RoutePickerError.NoCityAtLocation -> Text(stringResource(R.string.route_no_city_at_location))
            RoutePickerError.InvalidCity -> Text(stringResource(R.string.route_invalid_city))
            null -> Unit
        }
        if (!state.loading && state.error == null && state.query.isNotBlank() && state.results.isEmpty()) {
            Text(stringResource(R.string.route_no_cities))
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.results, key = { it.id }) { city ->
                TextButton(
                    onClick = { onSelect(city) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.route_city_result, city.name, city.country)) }
            }
            item {
                Box(Modifier.fillMaxWidth().heightIn(min = 180.dp)) {
                    if (mapArchive != null) {
                        PickerMap(
                            mapArchive,
                            state.selected?.let { NearbyCoordinate.from(it.latitude, it.longitude) },
                            onNearest = onNearest,
                        )
                    } else {
                        Text(stringResource(R.string.route_picker_map_unavailable))
                    }
                }
            }
        }
        state.selected?.let { city -> Text(stringResource(R.string.route_selected_city, city.name, city.country)) }
        if (state.selectionInvalid) Text(stringResource(R.string.route_invalid_city))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.route_cancel)) }
            Button(
                onClick = onConfirm,
                enabled = state.canConfirm,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.route_confirm))
            }
        }
    }
}

@Composable
private fun PickerMap(
    archive: File,
    selectedCoordinate: NearbyCoordinate?,
    onNearest: (NearbyCoordinate?) -> Unit,
) {
    val context = LocalContext.current
    // Not Compose state: `update` reads and replaces it, and a state read there would re-run
    // `update` after every write, creating a new marker without end.
    val draftMarker = remember { DraftMarkerHolder() }
    AndroidView(
        modifier = Modifier.fillMaxSize().semantics { contentDescription = context.getString(R.string.route_picker_map_description) },
        factory = {
            MapView(context, OfflineTileProvider(SimpleRegisterReceiver(context), arrayOf(archive))).apply {
                setTileSource(pickerMapSource)
                setUseDataConnection(false)
                setMultiTouchControls(true)
                minZoomLevel = 1.0
                maxZoomLevel = 6.0
                controller.setZoom(3.0)
                controller.setCenter(GeoPoint(0.0, 0.0))
                overlays.add(
                    MapEventsOverlay(
                        object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean = false

                            override fun longPressHelper(p: GeoPoint): Boolean {
                                onNearest(NearbyCoordinate.from(p.latitude, p.longitude))
                                return true
                            }
                        },
                    ),
                )
            }
        },
        update = { map ->
            draftMarker.marker?.let { map.overlays.remove(it) }
            draftMarker.marker =
                selectedCoordinate?.let { coordinate ->
                    Marker(map).also { marker ->
                        marker.icon =
                            androidx.core.content.ContextCompat
                                .getDrawable(context, R.drawable.ic_route_destination)
                        marker.position = GeoPoint(coordinate.latitude, coordinate.longitude)
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        map.overlays.add(marker)
                    }
                }
            map.invalidate()
        },
    )
}

/** The marker of the selected city currently on the picker map. */
private class DraftMarkerHolder {
    var marker: Marker? = null
}
