package kniezrec.com.flightinfo.map.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.map.MapCoordinate
import kniezrec.com.flightinfo.map.MapRules
import kniezrec.com.flightinfo.map.MapZoomTarget
import kniezrec.com.flightinfo.map.applyMapZoomPolicy
import kniezrec.com.flightinfo.route.RouteOverlay
import kniezrec.com.flightinfo.ui.theme.LabelText
import kniezrec.com.flightinfo.ui.theme.SmartFlightTheme
import kniezrec.com.flightinfo.ui.theme.ValueText
import kotlinx.coroutines.launch
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
 * new [state] (position, marker heading, center request, zoom setting) and [routeOverlay] re-runs the map's
 * `update`.
 *
 * The "expand" button doubles the map's height (at most [maxMapHeight]) and then scrolls the whole
 * map into view; "collapse" restores it.
 *
 * @param onUnavailable the map could not open [MapUiState.Ready.archive].
 * @param onCentered the map was centered on [MapUiState.Ready.centerRequest].
 * @param onZoomChanged the map's zoom level, reported when the map is created and on every zoom.
 * @param maxMapHeight height of the viewport the card scrolls in (the expanded map fits in it).
 */
@Composable
fun MapCard(
    state: MapUiState,
    onRetry: () -> Unit,
    onUnavailable: () -> Unit = {},
    onCentered: () -> Unit = {},
    onZoomChanged: (Double) -> Unit = {},
    routeOverlay: RouteOverlay? = null,
    maxMapHeight: Dp = Dp.Infinity,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Saved: the expanded map stays expanded across a configuration change.
    var expanded by rememberSaveable { mutableStateOf(false) }
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
                    val bringIntoViewRequester = remember { BringIntoViewRequester() }
                    val scope = rememberCoroutineScope()
                    val animatedMapHeight by animateDpAsState(
                        targetValue = mapHeight(maxWidth, maxHeight, maxMapHeight, expanded),
                        animationSpec = tween(RESIZE_ANIMATION_MILLIS),
                        label = "map height",
                        // Once expanded, the whole map is scrolled into view (as the original app).
                        finishedListener = { if (expanded) scope.launch { bringIntoViewRequester.bringIntoView() } },
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(animatedMapHeight)
                            .bringIntoViewRequester(bringIntoViewRequester)
                            .testTag(MAP_AREA_TAG),
                    ) {
                        val routeLineColor = SmartFlightTheme.colors.page.toArgb()
                        val instance = remember(state.archive) { MapInstance(MapOverlays(context, routeLineColor)) }
                        OfflineMap(
                            state = state,
                            instance = instance,
                            routeOverlay = routeOverlay,
                            onOpenFailure = onUnavailable,
                            onCentered = onCentered,
                            onZoomChanged = onZoomChanged,
                            modifier = Modifier.fillMaxSize(),
                        )
                        MapButton(
                            kind = MapButtonKind.Recenter,
                            modifier = Modifier.align(Alignment.TopEnd).padding(MAP_BUTTON_MARGIN),
                        ) { instance.recenter(state.position) }
                        MapButton(
                            kind = if (expanded) MapButtonKind.Collapse else MapButtonKind.Expand,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(MAP_BUTTON_MARGIN),
                        ) { expanded = !expanded }
                    }
                }
            }
        }
    }
}

/** The map's overlay buttons: the original icons, described by a (localized) label. */
internal enum class MapButtonKind(
    @param:StringRes val description: Int,
    @param:DrawableRes val icon: Int,
) {
    Recenter(R.string.map_recenter, R.drawable.drawing_pin_icon),
    Expand(R.string.map_expand, R.drawable.ic_expand),
    Collapse(R.string.map_collapse, R.drawable.ic_shrink),
}

/** Test tag of the map area (the map view and its buttons), the part whose height changes. */
internal const val MAP_AREA_TAG = "map-area"

/** Test tag of the icon currently shown by a map button of [kind]. */
internal fun mapButtonIconTag(kind: MapButtonKind) = "map-button-icon-${kind.name}"

private const val RESIZE_ANIMATION_MILLIS = 300
private val MAP_BUTTON_MARGIN = 4.dp

/**
 * Height of the map. Collapsed: 16/9 of [width] within 240..360 dp (and within [availableHeight]
 * when that is bounded). Expanded: twice the collapsed height, bounded by [viewportHeight] and
 * [availableHeight] (each at least 240 dp) but never smaller than collapsed.
 */
internal fun mapHeight(
    width: Dp,
    availableHeight: Dp,
    viewportHeight: Dp,
    expanded: Boolean,
): Dp {
    val collapsed = minOf((width.value * 16f / 9f).coerceIn(240f, 360f), availableHeight.boundOrInfinity())
    if (!expanded) return collapsed.dp
    val limit = minOf(viewportHeight.boundOrInfinity(), availableHeight.boundOrInfinity())
    return maxOf(collapsed, minOf(collapsed * 2f, limit)).dp
}

private fun Dp.boundOrInfinity(): Float =
    // Dp.Infinity (unbounded) and Dp.Unspecified (NaN) bound nothing.
    if (value.isFinite()) value.coerceAtLeast(240f) else Float.POSITIVE_INFINITY

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
        LabelText(stringResource(title), style = MaterialTheme.typography.titleLarge)
        ValueText(
            stringResource(body),
            Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (retry != null) {
            TextButton(onClick = retry, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.map_try_again), color = SmartFlightTheme.colors.accent)
            }
        }
    }
}

/**
 * An icon button drawn on the map (48 dp touch target): the original purple (`page`) icon, as the
 * original app, on a subtle `valueText` circle at 60% alpha so it stays visible on dark or missing
 * tiles. A new kind crossfades from the old one (expand and collapse).
 */
@Composable
private fun MapButton(
    kind: MapButtonKind,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = SmartFlightTheme.colors
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Crossfade(targetState = kind, label = "map button icon") { shown ->
            Box(
                Modifier.size(40.dp).background(colors.valueText.copy(alpha = MAP_BUTTON_BACKGROUND_ALPHA), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(shown.icon),
                    contentDescription = stringResource(shown.description),
                    modifier = Modifier.size(32.dp).testTag(mapButtonIconTag(shown)),
                    tint = colors.page,
                )
            }
        }
    }
}

private const val MAP_BUTTON_BACKGROUND_ALPHA = 0.6f

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
    onZoomChanged: (Double) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val largerMapZoom = state.largerMapZoom
    instance.largerMapZoom = largerMapZoom
    val currentOnZoomChanged by rememberUpdatedState(onZoomChanged)
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
                    // A new map starts below the maximum: reaching it again counts as a new transition.
                    currentOnZoomChanged(zoomLevelDouble)
                    addMapListener(
                        object : MapListener {
                            override fun onScroll(event: ScrollEvent): Boolean = true

                            override fun onZoom(event: ZoomEvent): Boolean {
                                currentOnZoomChanged(zoomLevelDouble)
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
            state.centerRequest?.let { center ->
                map.controller.setCenter(GeoPoint(center.latitude, center.longitude))
                onCentered()
            }
            instance.overlays.sync(map, state, routeOverlay)
        },
    )
}
