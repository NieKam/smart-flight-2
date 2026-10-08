package kniezrec.com.flightinfo.route.ui

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.map.ui.MapButton
import kniezrec.com.flightinfo.map.ui.MapButtonKind
import kniezrec.com.flightinfo.map.ui.MapOverlays
import kniezrec.com.flightinfo.map.ui.MapZoomButtons
import kniezrec.com.flightinfo.map.ui.hideBuiltInZoomControls
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.NearbyCoordinate
import kniezrec.com.flightinfo.route.RouteEndpoint
import kniezrec.com.flightinfo.route.RoutePickerError
import kniezrec.com.flightinfo.route.RoutePickerState
import kniezrec.com.flightinfo.ui.theme.CardIconBadge
import kniezrec.com.flightinfo.ui.theme.LabelText
import kniezrec.com.flightinfo.ui.theme.SmartFlightCard
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme
import kniezrec.com.flightinfo.ui.theme.ValueText
import kniezrec.com.flightinfo.ui.theme.smartFlightFilledButtonColors
import kniezrec.com.flightinfo.ui.theme.smartFlightSearchFieldColors
import kniezrec.com.flightinfo.ui.theme.smartFlightTonalButtonColors
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow
import java.io.File

private val pickerMapSource = XYTileSource("MapquestOSM", 1, 6, 256, ".jpg", arrayOf())

/** Test tag of the picker's root layout. */
internal const val ROUTE_PICKER_TAG = "route-picker"

/** Test tag of the picker's map area (the map, or its placeholder when the archive is missing). */
internal const val ROUTE_PICKER_MAP_TAG = "route-picker-map"

/**
 * Stateless city picker for [state]'s endpoint; shows nothing while the picker is closed.
 *
 * Layout as the original `activity_find_city`: search field and Search button, the selection (or
 * instruction) line, the result list when a search found several cities, a map filling the remaining
 * height that centers on the selected city, and a full-width Confirm. Cancel is in the top row and
 * on system back. Long-press on the map asks for the nearest city ([onNearest] gets null for a point
 * that is not a valid coordinate).
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
    SelectionHapticFeedback(state.selected?.id)
    val titleDescription = stringResource(R.string.route_picker_title_description)
    val title = if (endpoint == RouteEndpoint.DEPARTURE) R.string.route_picker_departure else R.string.route_picker_destination
    // Plain text on the picker is light (original result and input text), whatever surface hosts it.
    CompositionLocalProvider(LocalContentColor provides SmartFlightTheme.colors.valueText) {
        Column(
            Modifier.fillMaxSize().padding(16.dp).testTag(ROUTE_PICKER_TAG),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) {
                    Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_up))
                }
                ValueText(
                    stringResource(title),
                    modifier = Modifier.weight(1f).semantics { contentDescription = titleDescription },
                    style = MaterialTheme.typography.titleLarge,
                )
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = SmartFlightTheme.colors.valueText),
                ) { Text(stringResource(R.string.route_cancel)) }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // A pill as in the design; the label stays (it names the field after typing too).
                TextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f).border(1.dp, SmartFlightTheme.colors.cardOutline, CircleShape),
                    label = { Text(stringResource(R.string.route_city_name)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                    singleLine = true,
                    shape = CircleShape,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch(state.query) }),
                    colors = smartFlightSearchFieldColors(),
                )
                Button(
                    onClick = { onSearch(state.query) },
                    enabled = !state.loading,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = smartFlightTonalButtonColors(),
                ) {
                    Text(stringResource(R.string.route_search))
                }
            }
            if (state.loading) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = SmartFlightTheme.colors.accent)
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
            // The selected city in place of the instruction, as the original info label.
            val selected = state.selected
            if (selected != null) {
                SelectedCity(selected)
            } else {
                Text(
                    // "Long-press" in bold (the design); the resource marks it with <b>.
                    AnnotatedString.fromHtml(stringResource(R.string.route_map_instruction)),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            if (state.selectionInvalid) Text(stringResource(R.string.route_invalid_city))
            if (state.results.size > 1) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = RESULTS_MAX_HEIGHT)) {
                    items(state.results, key = { it.id }) { city ->
                        TextButton(
                            onClick = { onSelect(city) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = SmartFlightTheme.colors.valueText),
                        ) { Text(stringResource(R.string.route_city_result, city.name, city.country)) }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(MAP_SHAPE)
                    .background(SmartFlightTheme.colors.card)
                    .testTag(ROUTE_PICKER_MAP_TAG),
                contentAlignment = Alignment.Center,
            ) {
                if (mapArchive != null) {
                    PickerMap(
                        mapArchive,
                        selected?.let { NearbyCoordinate.from(it.latitude, it.longitude) },
                        onNearest = onNearest,
                    )
                } else {
                    Text(
                        stringResource(R.string.route_picker_map_unavailable),
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Button(
                onClick = onConfirm,
                enabled = state.canConfirm,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                colors = smartFlightFilledButtonColors(),
            ) {
                Text(stringResource(R.string.route_confirm))
            }
        }
    }
}

/**
 * The selected city in place of the instruction (PR #51 feedback): a card with the pin badge, the
 * city in the card title style and its country below, read as "Selected: city (country)".
 */
@Composable
private fun SelectedCity(city: NearbyCityRecord) {
    val description = stringResource(R.string.route_selected_city, city.name, city.country)
    SmartFlightCard(
        Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardIconBadge(R.drawable.ic_card_place)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    city.name,
                    color = SmartFlightTheme.colors.valueText,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                LabelText(city.country, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Haptic feedback when a new city is selected, as the original map did on every shown city. The last
 * city felt survives a configuration change, so turning the device does not repeat it.
 */
@Composable
private fun SelectionHapticFeedback(selectedId: Long?) {
    val haptics = LocalHapticFeedback.current
    var feltId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(selectedId) {
        if (selectedId != null && selectedId != feltId) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        feltId = selectedId
    }
}

@Composable
private fun PickerMap(
    archive: File,
    selectedCoordinate: NearbyCoordinate?,
    onNearest: (NearbyCoordinate?) -> Unit,
) {
    // Not Compose state: `update` reads and replaces it, and a state read there would re-run
    // `update` after every write without end.
    val draft = remember { DraftMarkerHolder() }
    val tileTint = SmartFlightTheme.colors.mapTileTint.toArgb()
    Box(Modifier.fillMaxSize()) {
        PickerMapView(archive, draft, selectedCoordinate, tileTint, onNearest)
        MapButton(MapButtonKind.Recenter, Modifier.align(Alignment.TopEnd).padding(4.dp)) { recenter(draft) }
        MapZoomButtons(
            onZoomIn = { draft.map?.controller?.zoomIn() },
            onZoomOut = { draft.map?.controller?.zoomOut() },
            modifier = Modifier.align(Alignment.CenterEnd).padding(4.dp),
        )
    }
}

@Composable
private fun PickerMapView(
    archive: File,
    draft: DraftMarkerHolder,
    selectedCoordinate: NearbyCoordinate?,
    tileTint: Int,
    onNearest: (NearbyCoordinate?) -> Unit,
) {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxSize().semantics { contentDescription = context.getString(R.string.route_picker_map_description) },
        factory = {
            MapView(context, OfflineTileProvider(SimpleRegisterReceiver(context), arrayOf(archive))).apply {
                draft.map = this
                hideBuiltInZoomControls()
                setTileSource(pickerMapSource)
                setUseDataConnection(false)
                setMultiTouchControls(true)
                minZoomLevel = 1.0
                maxZoomLevel = 6.0
                controller.setZoom(PICKER_WORLD_ZOOM)
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
            // Dims the tiles in the dark scheme, as the dashboard map (TASK-047).
            map.overlayManager.tilesOverlay.setColorFilter(PorterDuffColorFilter(tileTint, PorterDuff.Mode.MULTIPLY))
            if (selectedCoordinate != draft.coordinate) {
                showCity(map, draft, selectedCoordinate)
                map.invalidate()
            }
        },
    )
}

/**
 * Moves the marker to [coordinate] and centers the map on it (see [pickerCamera]): at once when the
 * map shows its first city, animated afterwards, as the original `animateTo`.
 */
private fun showCity(
    map: MapView,
    draft: DraftMarkerHolder,
    coordinate: NearbyCoordinate?,
) {
    val firstCity = draft.coordinate == null
    draft.coordinate = coordinate
    if (coordinate == null) {
        draft.marker?.let { map.overlays.remove(it) }
        draft.marker = null
        return
    }
    val marker = draft.marker ?: cityMarker(map).also { draft.marker = it }
    marker.position = GeoPoint(coordinate.latitude, coordinate.longitude)
    val camera = pickerCamera(coordinate, map.zoomLevelDouble)
    val target = GeoPoint(camera.latitude, camera.longitude)
    if (firstCity) {
        map.controller.setZoom(camera.zoom)
        map.controller.setCenter(target)
    } else {
        map.controller.animateTo(target, camera.zoom, null)
    }
}

/** The picker's city marker: the original `ic_city_found_marker` in the page color, without an info window. */
private fun cityMarker(map: MapView): Marker =
    Marker(map).also { marker ->
        marker.icon = ContextCompat.getDrawable(map.context, R.drawable.ic_city_found_marker)
        marker.setAnchor(Marker.ANCHOR_CENTER, MapOverlays.PIN_TIP_ANCHOR_V)
        marker.setInfoWindow(null as MarkerInfoWindow?)
        marker.setOnMarkerClickListener { _, _ -> true }
        map.overlays.add(marker)
    }

private val RESULTS_MAX_HEIGHT = 168.dp

private val MAP_SHAPE = RoundedCornerShape(12.dp)

/** The picker map, the selected city currently shown on it, and its marker. */
private class DraftMarkerHolder {
    var map: MapView? = null
    var coordinate: NearbyCoordinate? = null
    var marker: Marker? = null
}

/** The recenter button (TASK-051): back to the selected city, or to the world view without one. */
private fun recenter(draft: DraftMarkerHolder) {
    val map = draft.map ?: return
    val coordinate = draft.coordinate
    if (coordinate == null) {
        map.controller.setZoom(PICKER_WORLD_ZOOM)
        map.controller.setCenter(GeoPoint(0.0, 0.0))
    } else {
        val camera = pickerCamera(coordinate, map.zoomLevelDouble)
        map.controller.animateTo(GeoPoint(camera.latitude, camera.longitude), camera.zoom, null)
    }
}
