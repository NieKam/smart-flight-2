package kniezrec.com.flightinfo.ui.route

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.osmdroid.config.Configuration
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

@Composable
fun RoutePicker(
    endpoint: RouteEndpoint,
    initial: NearbyCityRecord?,
    results: List<NearbyCityRecord>,
    loading: Boolean,
    error: String?,
    mapArchive: File?,
    onSearch: (String) -> Unit,
    onNearest: (NearbyCoordinate) -> Unit,
    nearestDraft: NearbyCityRecord? = null,
    onConfirm: (NearbyCityRecord) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(initial) }
    var mapMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(initial) { selected = initial }
    LaunchedEffect(nearestDraft) { if (nearestDraft != null) selected = nearestDraft }
    BackHandler(onBack = onCancel)
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(if (endpoint == RouteEndpoint.DEPARTURE) R.string.route_picker_departure else R.string.route_picker_destination),
            modifier = Modifier.semantics { contentDescription = stringResource(R.string.route_picker_title_description) },
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.route_city_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
        )
        Button(onClick = { onSearch(query) }, enabled = !loading, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.route_search))
        }
        Text(stringResource(R.string.route_map_instruction))
        if (loading) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator()
                Text(stringResource(R.string.route_searching))
            }
        }
        if (error != null) {
            Text(stringResource(R.string.route_error))
            Text(error)
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.route_retry)) }
        }
        if (!loading && error == null && query.isNotBlank() && results.isEmpty()) {
            Text(stringResource(R.string.route_no_cities))
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(results, key = { it.id }) { city ->
                TextButton(
                    onClick = { selected = city },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(stringResource(R.string.route_city_result, city.name, city.country)) }
            }
            item {
                Box(Modifier.fillMaxWidth().heightIn(min = 180.dp)) {
                    if (mapArchive != null) {
                        PickerMap(mapArchive, selected?.let { NearbyCoordinate.from(it.latitude, it.longitude) }, onNearest = { coordinate ->
                            mapMessage = null
                            onNearest(coordinate)
                        }, onInvalidLongPress = { mapMessage = it })
                    } else {
                        Text(stringResource(R.string.route_picker_map_unavailable))
                    }
                }
            }
        }
        mapMessage?.let { Text(it) }
        selected?.let { city -> Text(stringResource(R.string.route_selected_city, city.name, city.country)) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.route_cancel)) }
            Button(
                onClick = { selected?.let(onConfirm) },
                enabled = selected != null && !loading,
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
    onNearest: (NearbyCoordinate) -> Unit,
    onInvalidLongPress: (String) -> Unit,
) {
    val context = LocalContext.current
    var draftMarker by remember { mutableStateOf<Marker?>(null) }
    AndroidView(
        modifier = Modifier.fillMaxSize().semantics { contentDescription = context.getString(R.string.route_picker_map_description) },
        factory = {
            Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
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
                                val coordinate = NearbyCoordinate.from(p.latitude, p.longitude)
                                if (coordinate == null) {
                                    onInvalidLongPress(context.getString(R.string.route_no_city_at_location))
                                } else {
                                    onNearest(coordinate)
                                }
                                return true
                            }
                        },
                    ),
                )
            }
        },
        update = { map ->
            draftMarker?.let { map.overlays.remove(it) }
            draftMarker = selectedCoordinate?.let { coordinate ->
                Marker(map).also { marker ->
                    marker.icon = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_route_destination)
                    marker.position = GeoPoint(coordinate.latitude, coordinate.longitude)
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    map.overlays.add(marker)
                }
            }
            map.invalidate()
        },
    )
}
