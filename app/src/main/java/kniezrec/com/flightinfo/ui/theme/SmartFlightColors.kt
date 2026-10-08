package kniezrec.com.flightinfo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's own color tokens. Every screen, card, dialog and overlay takes its colors from here
 * (directly or through [SmartFlightTheme]'s Material color scheme); read them through
 * [SmartFlightTheme.colors].
 *
 * The token table (with the contrast of every pair the app draws) is pinned by `PaletteGuardTest`
 * and `ContrastTest`: a new color needs a line in that table first.
 *
 * Both schemes follow the 2026 redesign (TASK-039, `docs/design/2026-10-redesign/`): a cool
 * near-white page with white outlined cards in the light theme, a navy page with slate cards in the
 * dark theme, and a blue accent. The map pins, plane marker and buttons keep the original purple ink
 * and halo; the dark scheme dims the tiles and draws a light route line (TASK-047).
 */
@Immutable
data class SmartFlightColors(
    /** Page (window) background, Settings and the city picker (Material `background`/`surface`). */
    val page: Color,
    /** Cards and picker buttons (Material `surfaceContainer`). */
    val card: Color,
    /** Dialogs and menus (Material `surfaceContainerHigh`). */
    val raised: Color,
    /** Top bar at rest, and the status bar behind it. */
    val topBar: Color,
    /** Top bar while content scrolls under it. */
    val topBarScrolled: Color,
    /** Card outline, dividers inside cards and the compass ring (Material `outlineVariant`). */
    val cardOutline: Color,
    /** Actions, switches, radio buttons, text-field indicator, compass cardinal (Material `primary`). */
    val accent: Color,
    /** Content on an accent fill: the checked switch thumb. */
    val onAccent: Color,
    /** Tinted container of accent content: card icon badges, status pills, tonal buttons (Material `primaryContainer`). */
    val accentContainer: Color,
    /** Secondary accent: the Settings row highlight. */
    val accentLight: Color,
    /** Muted labels, secondary text. */
    val labelText: Color,
    /** Values, card titles, body text, dialog content, primary text. */
    val valueText: Color,
    /** Top bar title and icons. */
    val toolbarTitle: Color,
    /** The plane in the compass dial. */
    val compassPlane: Color,
    /** Satellite used in the fix (chart bar). */
    val satelliteUsed: Color,
    /** Satellite not used in the fix (chart bar). */
    val satelliteUnused: Color,
    /** Inline error text. */
    val error: Color,
    /** Snackbar container (Material `inverseSurface`). */
    val inverseSurface: Color,
    /** Snackbar text (Material `inverseOnSurface`). */
    val inverseOnSurface: Color,
    /** Snackbar action (Material `inversePrimary`). */
    val inversePrimary: Color,
    /** `dark_overlay_alpha_50`: scrim. */
    val overlay50: Color,
    /** `dark_overlay_alpha_20`: unchecked switch track. */
    val overlay20: Color,
    /** Artificial horizon sky at the top of the instrument. */
    val horizonSkyTop: Color,
    /** Artificial horizon sky at the horizon line (the lightest sky tone). */
    val horizonSky: Color,
    /** Artificial horizon ground at the horizon line (the lightest ground tone). */
    val horizonGround: Color,
    /** Artificial horizon ground at the bottom of the instrument. */
    val horizonGroundBottom: Color,
    /** Artificial horizon line, ticks and aircraft symbol. */
    val horizonLine: Color,
    /**
     * Drawn on the map tiles (theme-independent, the tiles are the same in every theme): route line,
     * map button icons, pin and plane marker fill.
     */
    val mapInk: Color,
    /** Light halo on the map tiles: map button container (at [MAP_BUTTON_CONTAINER_ALPHA]), pin and marker outline. */
    val mapHalo: Color,
    /** The great-circle route line on the (tinted) tiles. */
    val mapRoute: Color,
    /** Multiplied over the offline tiles: white leaves them as they are, the dark scheme dims them to navy. */
    val mapTileTint: Color,
)

/** Opacity of the map buttons' [SmartFlightColors.mapHalo] container, so the icon reads on any tile. */
const val MAP_BUTTON_CONTAINER_ALPHA = 0.8f

// Shared by both schemes: the map overlays do not change with the theme.
private val PurpleDark = Color(0xFF484685)
private val White = Color(0xFFFFFFFF)
private val Overlay50 = Color(0x80000000)
private val Overlay20 = Color(0x33000000)
private val MapHalo = Color(0xFFD9D9ED)
private val Navy = Color(0xFF172340)
private val LightBlue = Color(0xFF1A66D9)
private val DarkBlue = Color(0xFF4A9BFD)
private val Mist = Color(0xFFE8ECF5)

/**
 * The dark scheme (TASK-039): navy page, slate cards one tone lighter with an outline, dialogs one
 * more tone up; a bright blue accent and blue-grey labels. The horizon is a deep blue sky over a
 * dark green ground.
 */
val DarkSmartFlightColors =
    SmartFlightColors(
        page = Color(0xFF111722),
        card = Color(0xFF1A2330),
        raised = Color(0xFF232D3C),
        topBar = Color(0xFF111722),
        topBarScrolled = Color(0xFF1E2735),
        cardOutline = Color(0xFF283245),
        accent = DarkBlue,
        onAccent = White,
        accentContainer = Color(0xFF1C304A),
        accentLight = Color(0xFF23436B),
        labelText = Color(0xFF9DAED0),
        valueText = Mist,
        toolbarTitle = Mist,
        compassPlane = DarkBlue,
        satelliteUsed = Color(0xFF4CAF50),
        satelliteUnused = Color(0xFFFF7A6E),
        error = Color(0xFFFFB4AB),
        inverseSurface = Mist,
        inverseOnSurface = Navy,
        inversePrimary = Color(0xFF1558C0),
        overlay50 = Overlay50,
        overlay20 = Overlay20,
        horizonSkyTop = Color(0xFF0F3D74),
        horizonSky = Color(0xFF1C5EA0),
        horizonGround = Color(0xFF295744),
        horizonGroundBottom = Color(0xFF143839),
        horizonLine = White,
        mapInk = PurpleDark,
        mapHalo = MapHalo,
        mapRoute = Navy,
        mapTileTint = Color(0xFF8F9BBE),
    )

/**
 * The light scheme (TASK-039): a cool near-white page, white cards separated by a hairline outline
 * (the page/card tone difference alone is too small), navy text and a blue accent that passes AA on
 * every light surface. The horizon is a blue sky over a green ground, darkened from the design so the
 * white marks keep 3:1.
 */
val LightSmartFlightColors =
    SmartFlightColors(
        page = Color(0xFFF4F6FB),
        card = White,
        raised = Color(0xFFF9FAFD),
        topBar = Color(0xFFF4F6FB),
        topBarScrolled = Color(0xFFE9EDF5),
        cardOutline = Color(0xFFE2E7F0),
        accent = LightBlue,
        onAccent = White,
        accentContainer = Color(0xFFE7F0FD),
        accentLight = Color(0xFFCFE0FA),
        labelText = Color(0xFF5B6785),
        valueText = Navy,
        toolbarTitle = Navy,
        compassPlane = Navy,
        satelliteUsed = Color(0xFF2E7D32),
        satelliteUnused = Color(0xFFC62828),
        error = Color(0xFFB3261E),
        inverseSurface = Navy,
        inverseOnSurface = Color(0xFFF1F4FA),
        inversePrimary = Color(0xFF8DBBFF),
        overlay50 = Overlay50,
        overlay20 = Overlay20,
        horizonSkyTop = Color(0xFF2A73C9),
        horizonSky = Color(0xFF3B87DB),
        horizonGround = Color(0xFF3E7F5B),
        horizonGroundBottom = Color(0xFF2B6249),
        horizonLine = White,
        mapInk = PurpleDark,
        mapHalo = MapHalo,
        mapRoute = PurpleDark,
        mapTileTint = White,
    )

/**
 * Provides [SmartFlightColors]; the default lets composables rendered without [SmartFlightTheme]
 * (for example in tests) use the dark scheme.
 */
val LocalSmartFlightColors = staticCompositionLocalOf { DarkSmartFlightColors }
