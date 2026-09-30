package kniezrec.com.flightinfo.map.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.map.MapCoordinate
import kniezrec.com.flightinfo.map.MapRules
import kniezrec.com.flightinfo.map.MapZoomTarget
import kniezrec.com.flightinfo.map.applyMapZoomPolicy
import kniezrec.com.flightinfo.route.RouteOverlay
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

private val mapSource = XYTileSource("MapquestOSM", 1, 9, 256, ".jpg", arrayOf())

/**
 * The offline map card. Stateless apart from view-interop state and the saved expanded flag: every
 * new [state] (position, course, center request, zoom setting) and [routeOverlay] re-runs the map's
 * `update`.
 *
 * @param onUnavailable the map could not open [MapUiState.Ready.archive].
 * @param onCentered the map was centered on [MapUiState.Ready.centerRequest].
 */
@Composable
fun MapCard(
    state: MapUiState,
    onRetry: () -> Unit,
    onUnavailable: () -> Unit = {},
    onCentered: () -> Unit = {},
    routeOverlay: RouteOverlay? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Saved: the expanded map stays expanded across a configuration change.
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showMaximumZoomWarning by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth().heightIn(min = 240.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SmartFlightTheme.colors.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        when (state) {
            MapUiState.Loading -> MapMessage(R.string.map_loading, R.string.map_loading_body)
            MapUiState.Unavailable -> MapMessage(R.string.map_unavailable, R.string.map_unavailable_body, onRetry)
            MapUiState.Inactive -> MapMessage(R.string.map_loading, R.string.map_inactive_body)
            is MapUiState.Ready -> {
                BoxWithConstraints(Modifier.fillMaxWidth().testTag("map-content")) {
                    val mapHeight = mapHeight(maxWidth, maxHeight, expanded)
                    Box(Modifier.fillMaxWidth().height(mapHeight)) {
                        val instance = remember(state.archive) { MapInstance(MapOverlays(context)) }
                        OfflineMap(
                            state = state,
                            instance = instance,
                            routeOverlay = routeOverlay,
                            onOpenFailure = onUnavailable,
                            onCentered = onCentered,
                            onMaximumZoomWarningChanged = { showMaximumZoomWarning = it },
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (showMaximumZoomWarning) {
                            Text(
                                stringResource(R.string.map_maximum_zoom_warning),
                                Modifier.align(Alignment.BottomStart).padding(12.dp),
                                color = SmartFlightTheme.colors.mapOverlayContent,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        MapButton(
                            kind = MapButtonKind.Recenter,
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                        ) { instance.recenter(state.position) }
                        MapButton(
                            kind = if (expanded) MapButtonKind.Collapse else MapButtonKind.Expand,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                        ) { expanded = !expanded }
                    }
                }
            }
        }
    }
}

/** The map's overlay buttons; the glyph comes from the kind, never from the (localized) label. */
internal enum class MapButtonKind(
    @param:StringRes val description: Int,
    val glyph: String,
) {
    Recenter(R.string.map_recenter, "◎"),
    Expand(R.string.map_expand, "↕"),
    Collapse(R.string.map_collapse, "↕"),
}

private fun mapHeight(
    width: androidx.compose.ui.unit.Dp,
    availableHeight: androidx.compose.ui.unit.Dp,
    expanded: Boolean,
): androidx.compose.ui.unit.Dp {
    val ratio = if (expanded) 4f / 3f else 16f / 9f
    val preferred = (width.value * ratio).coerceIn(240f, if (expanded) 520f else 360f)
    val bounded =
        if (availableHeight != androidx.compose.ui.unit.Dp.Infinity) {
            availableHeight.value.coerceAtLeast(240f)
        } else {
            Float.POSITIVE_INFINITY
        }
    return minOf(preferred, bounded).dp
}

@Composable
private fun MapMessage(
    title: Int,
    body: Int,
    retry: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 240.dp).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(title), color = SmartFlightTheme.colors.text, style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(body),
            Modifier.padding(top = 12.dp),
            color = SmartFlightTheme.colors.text,
            style = MaterialTheme.typography.bodyLarge,
        )
        if (retry != null) {
            TextButton(onClick = retry, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.map_try_again), color = SmartFlightTheme.colors.accent)
            }
        }
    }
}

@Composable
private fun MapButton(
    kind: MapButtonKind,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val description = stringResource(kind.description)
    IconButton(
        onClick = onClick,
        modifier =
            modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(SmartFlightTheme.colors.mapButtonBackground).semantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        Text(kind.glyph, color = SmartFlightTheme.colors.mapOverlayContent, style = MaterialTheme.typography.titleLarge)
    }
}

/** The map view of one Ready archive and its overlays; not Compose state (see [OfflineMap]). */
private class MapInstance(
    val overlays: MapOverlays,
) {
    var map: MapView? = null
    var largerMapZoom: Boolean = false

    fun recenter(position: MapCoordinate?) {
        val map = map ?: return
        val viewport = MapRules.recenter(position)
        map.controller.setCenter(GeoPoint(viewport.center.latitude, viewport.center.longitude))
        map.controller.setZoom(viewport.zoom)
    }

    fun dispose() {
        map?.overlays?.removeAll { it !== overlays.planeMarker }
        map?.onDetach()
        overlays.clear()
        largerMapZoom = false
        map = null
    }
}

/**
 * The osmdroid map. `update` reads only immutable parameters and writes no Compose state that it
 * reads, so it runs once per new [state] or [routeOverlay] and never schedules itself again.
 */
@Composable
private fun OfflineMap(
    state: MapUiState.Ready,
    instance: MapInstance,
    routeOverlay: RouteOverlay?,
    onOpenFailure: () -> Unit,
    onCentered: () -> Unit,
    onMaximumZoomWarningChanged: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val largerMapZoom = state.largerMapZoom
    instance.largerMapZoom = largerMapZoom
    DisposableEffect(instance) {
        onDispose {
            instance.dispose()
        }
    }
    AndroidView(
        modifier =
            modifier.semantics {
                contentDescription =
                    routeOverlay?.let { context.getString(R.string.route_map_summary, it.departureName, it.destinationName) }
                        ?: context.getString(R.string.map_ready_summary)
                stateDescription =
                    if (state.position == null) {
                        context.getString(R.string.map_no_position)
                    } else {
                        context.getString(R.string.map_position_shown)
                    }
            },
        factory = {
            try {
                val provider = OfflineTileProvider(SimpleRegisterReceiver(context), arrayOf(state.archive))
                // osmdroid logs and skips an archive it cannot read instead of throwing.
                if (provider.archives.isEmpty()) {
                    provider.detach()
                    throw IllegalStateException("Offline map archive could not be opened")
                }
                MapView(context, provider).apply {
                    setTileSource(mapSource)
                    setUseDataConnection(false)
                    setMultiTouchControls(true)
                    minZoomLevel = 1.0
                    maxZoomLevel = MapRules.maxZoom(largerMapZoom)
                    controller.setZoom(MapRules.DEFAULT_ZOOM)
                    // A map recreated after the first-fix centering (e.g. rotation) starts at the
                    // latest position instead of waiting for a center request that already happened.
                    val center = state.position ?: MapRules.DEFAULT_CENTER
                    controller.setCenter(GeoPoint(center.latitude, center.longitude))
                    instance.map = this
                    addMapListener(
                        object : MapListener {
                            override fun onScroll(event: ScrollEvent): Boolean {
                                onMaximumZoomWarningChanged(
                                    MapRules.shouldShowMaximumZoomWarning(zoomLevel.toDouble(), instance.largerMapZoom),
                                )
                                return true
                            }

                            override fun onZoom(event: ZoomEvent): Boolean {
                                onMaximumZoomWarningChanged(
                                    MapRules.shouldShowMaximumZoomWarning(zoomLevel.toDouble(), instance.largerMapZoom),
                                )
                                return true
                            }
                        },
                    )
                }
            } catch (_: Exception) {
                onOpenFailure()
                MapView(context).apply {
                    setUseDataConnection(false)
                    instance.map = this
                }
            }
        },
        update = { map ->
            applyMapZoomPolicy(
                target =
                    object : MapZoomTarget {
                        override var maxZoomLevel: Double
                            get() = map.maxZoomLevel.toDouble()
                            set(value) {
                                map.maxZoomLevel = value
                            }
                        override val zoomLevel: Double get() = map.zoomLevel.toDouble()

                        override fun setZoom(zoom: Double) {
                            map.controller.setZoom(zoom)
                        }

                        override fun invalidate() {
                            map.invalidate()
                        }
                    },
                largerMapZoom = largerMapZoom,
            )
            onMaximumZoomWarningChanged(MapRules.shouldShowMaximumZoomWarning(map.zoomLevel.toDouble(), largerMapZoom))
            state.centerRequest?.let { center ->
                map.controller.setCenter(GeoPoint(center.latitude, center.longitude))
                onCentered()
            }
            instance.overlays.sync(map, state, routeOverlay)
        },
    )
}
