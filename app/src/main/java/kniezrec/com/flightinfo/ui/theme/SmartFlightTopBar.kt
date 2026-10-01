package kniezrec.com.flightinfo.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * The container color of a top bar (TASK-037): [SmartFlightColors.topBar] at rest and
 * [SmartFlightColors.topBarScrolled] while content scrolls under it ([scrollBehavior]), animated.
 * Read the state in the draw phase ([topBarBackground]) so scrolling does not recompose the bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberTopBarContainerColor(scrollBehavior: TopAppBarScrollBehavior?): State<Color> {
    val colors = SmartFlightTheme.colors
    val scrolled by remember(scrollBehavior) {
        derivedStateOf { (scrollBehavior?.state?.overlappedFraction ?: 0f) > SCROLLED_FRACTION }
    }
    return animateColorAsState(
        targetValue = if (scrolled) colors.topBarScrolled else colors.topBar,
        label = "top bar container",
    )
}

/**
 * Paints [color] behind the top bar and behind the whole window band above it (the status bar), so
 * the status bar always matches the bar, at rest and scrolled. The app is edge to edge and the bar
 * sits right below the status bar; nothing between them clips drawing.
 */
fun Modifier.topBarBackground(color: State<Color>): Modifier =
    drawBehind {
        val fill = color.value
        drawRect(fill)
        drawRect(
            fill,
            topLeft = Offset(-size.width, -STATUS_BAND_HEIGHT_PX),
            size = Size(size.width * 3, STATUS_BAND_HEIGHT_PX),
        )
    }

/**
 * Top bar content colors: [SmartFlightColors.toolbarTitle] title and icons on a transparent
 * container (the container is painted by [topBarBackground]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun smartFlightTopAppBarColors(): TopAppBarColors {
    val colors = SmartFlightTheme.colors
    return TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
        navigationIconContentColor = colors.toolbarTitle,
        titleContentColor = colors.toolbarTitle,
        actionIconContentColor = colors.toolbarTitle,
    )
}

/** As Material's own top bars: the bar counts as scrolled once content overlaps it. */
private const val SCROLLED_FRACTION = 0.01f

/** Far taller than any status bar or cutout; drawing is clipped at the window's top edge. */
private const val STATUS_BAND_HEIGHT_PX = 10_000f
