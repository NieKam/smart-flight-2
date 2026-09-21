package kniezrec.com.flightinfo.ui.gnss

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.onDispose
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.core.content.ContextCompat
import kniezrec.com.flightinfo.R
import kniezrec.com.flightinfo.map.MapSessionRules
import kniezrec.com.flightinfo.ui.permission.actionCyan
import kniezrec.com.flightinfo.ui.permission.cardPurple
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.OfflineTileProvider
import org.osmdroid.tileprovider.modules.ZipFileArchive
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

sealed interface MapCardState {
    data object Loading : MapCardState

    data class Ready(
        val archive: File,
    ) : MapCardState

    data object Unavailable : MapCardState

    data object Inactive : MapCardState
}

private val mapSource = XYTileSource("MapquestOSM", 1, 6, 256, ".jpg", arrayOf())

@Composable
fun MapCard(
    state: MapCardState,
    rules: MapSessionRules,
    onRetry: () -> Unit,
    onUnavailable: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth().heightIn(min = 240.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cardPurple),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        when (state) {
            MapCardState.Loading -> MapMessage(R.string.map_loading, R.string.map_loading_body)
            MapCardState.Unavailable -> MapMessage(R.string.map_unavailable, R.string.map_unavailable_body, onRetry)
            MapCardState.Inactive -> MapMessage(R.string.map_loading, R.string.map_inactive_body)
            is MapCardState.Ready -> {
                BoxWithConstraints(
                    Modifier.fillMaxWidth().height(mapHeight(maxWidth, maxHeight, expanded)).testTag("map-content"),
                ) {
                    val instance = remember(state.archive) { MapInstance() }
                    OfflineMap(
                        archive = state.archive,
                        rules = rules,
                        instance = instance,
                        onOpenFailure = onUnavailable,
                        modifier = Modifier.fillMaxSize(),
                    )
                    MapButton(
                        description = stringResource(R.string.map_recenter),
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                    ) { instance.recenter(rules) }
                    MapButton(
                        description = stringResource(if (expanded) R.string.map_collapse else R.string.map_expand),
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    ) { expanded = !expanded }
                }
            }
        }
    }
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
        Text(stringResource(title), color = Color(0xFFD9D9ED), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(body), Modifier.padding(top = 12.dp), color = Color(0xFFD9D9ED), style = MaterialTheme.typography.bodyLarge)
        if (retry != null) {
            TextButton(onClick = retry, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.map_try_again), color = actionCyan)
            }
        }
    }
}

@Composable
private fun MapButton(
    description: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier =
            modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xDD25133F)).semantics {
                contentDescription = description
                role = Role.Button
            },
    ) {
        Text(
            if (description.startsWith("Expand") ||
                description.startsWith("Collapse")
            ) {
                "↕"
            } else {
                "◎"
            },
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

private class MapInstance {
    var map: MapView? = null
    var marker: Marker? = null

    fun recenter(rules: MapSessionRules) {
        val map = map ?: return
        val viewport = rules.recenter()
        map.controller.setCenter(GeoPoint(viewport.center.latitude, viewport.center.longitude))
        map.controller.setZoom(viewport.zoom)
    }

    fun dispose() {
        map?.overlays?.clear()
        map?.onDetach()
        marker = null
        map = null
    }
}

@Composable
private fun OfflineMap(
    archive: File,
    rules: MapSessionRules,
    instance: MapInstance,
    onOpenFailure: () -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    DisposableEffect(instance) {
        onDispose {
            instance.dispose()
        }
    }
    AndroidView(
        modifier =
            modifier.semantics {
                contentDescription = context.getString(R.string.map_ready_summary)
                stateDescription =
                    if (rules.latestPosition ==
                        null
                    ) {
                        context.getString(R.string.map_no_position)
                    } else {
                        context.getString(R.string.map_position_shown)
                    }
            },
        factory = {
            try {
                Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
                val provider = OfflineTileProvider(arrayOf(ZipFileArchive(archive)))
                MapView(context, provider).apply {
                    setTileSource(mapSource)
                    setUseDataConnection(false)
                    setMultiTouchControls(true)
                    minZoomLevel = 1.0
                    maxZoomLevel = 6.0
                    controller.setZoom(MapSessionRules.DEFAULT_ZOOM)
                    controller.setCenter(GeoPoint(MapSessionRules.DEFAULT_CENTER.latitude, MapSessionRules.DEFAULT_CENTER.longitude))
                    instance.map = this
                }
            } catch (_: Exception) {
                onOpenFailure()
                MapView(context).also { instance.map = it }
            }
        },
        update = { map ->
            val firstFix = rules.consumeFirstFixCenter()
            if (firstFix != null) map.controller.setCenter(GeoPoint(firstFix.latitude, firstFix.longitude))
            val position = rules.latestPosition
            if (position != null) {
                if (instance.marker == null) {
                    instance.marker =
                        Marker(map).also { marker ->
                            marker.icon = ContextCompat.getDrawable(context, R.drawable.ic_plane_map)
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            map.overlays.add(marker)
                        }
                }
                instance.marker?.apply {
                    this.position = GeoPoint(position.latitude, position.longitude)
                    rotation = rules.markerCourse
                }
                map.invalidate()
            }
        },
    )
}
