package kniezrec.com.flightinfo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's own color tokens, derived from the original Smart Flight palette (`colors.xml` of the
 * original app). Every screen, card, dialog and overlay takes its colors from here (directly or
 * through [SmartFlightTheme]'s Material color scheme); read them through [SmartFlightTheme.colors].
 *
 * The token table (with the contrast of every pair the app draws) is pinned by `PaletteGuardTest`
 * and `ContrastTest`: a new color needs a line in that table first.
 *
 * Dark scheme (TASK-037): every surface moved one step darker than the original so muted labels,
 * values and the cyan accent pass WCAG AA as text (the original card #5B5999 left no room for a
 * muted label at 4.5:1). The original `purple_main` stays the top bar and `purple_dark` becomes the
 * card, so the dashboard keeps the original look.
 *
 * Light scheme (TASK-038): brand-tinted "lavender mist" surfaces instead of near-white; the page
 * and the top bar at rest share one tone, cards and dialogs are lighter tones of the same hue.
 */
@Immutable
data class SmartFlightColors(
    /** Page (window) background, Settings and the city picker (Material `background`/`surface`). */
    val page: Color,
    /** Cards and picker buttons (Material `surfaceContainer`). */
    val card: Color,
    /** Dialogs and menus, one tone above the card (Material `surfaceContainerHigh`). */
    val raised: Color,
    /** Top bar at rest, and the status bar behind it. */
    val topBar: Color,
    /** Top bar while content scrolls under it. */
    val topBarScrolled: Color,
    /** Actions, switches, radio buttons, text-field indicator, compass cardinal (Material `primary`). */
    val accent: Color,
    /** Pressed/highlight accent, checked switch track (accent at 50%). */
    val accentPressed: Color,
    /** Secondary accent: the Settings row highlight. */
    val accentLight: Color,
    /** Muted labels, card titles, dialog titles, secondary text. */
    val labelText: Color,
    /** Values, body text, dialog content, primary text. */
    val valueText: Color,
    /** Top bar title and icons. */
    val toolbarTitle: Color,
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
    /** `dark_overlay_alpha_20`: dividers, unchecked switch track. */
    val overlay20: Color,
    /** Artificial horizon sky half (the instrument looks the same in every theme). */
    val horizonSky: Color,
    /** Artificial horizon ground half. */
    val horizonGround: Color,
    /** Artificial horizon line, ticks and aircraft symbol. */
    val horizonLine: Color,
    /**
     * Drawn on the map tiles (theme-independent, the tiles are the same in every theme): route line,
     * map button icons, pin and plane marker fill.
     */
    val mapInk: Color,
    /** Light halo on the map tiles: map button container (at [MAP_BUTTON_CONTAINER_ALPHA]), pin and marker outline. */
    val mapHalo: Color,
)

/** Opacity of the map buttons' [SmartFlightColors.mapHalo] circle, so the icon reads on any tile. */
const val MAP_BUTTON_CONTAINER_ALPHA = 0.8f

// Shared by both schemes: the map overlays and the horizon instrument do not change with the theme.
private val PurpleMain = Color(0xFF5B5999)
private val PurpleDark = Color(0xFF484685)
private val CyanMain = Color(0xFF25E5FE)
private val CyanLight = Color(0xFF99E5FC)
private val White = Color(0xFFFFFFFF)
private val Overlay50 = Color(0x80000000)
private val Overlay20 = Color(0x33000000)
private val HorizonSky = Color(0xFF7775B5)
private val HorizonGround = Color(0xFF3F3D70)
private val MapHalo = Color(0xFFD9D9ED)
private val ToastPurple = Color(0xFF2C2163)

/** The dark scheme: the original purple palette, refined for contrast. */
val DarkSmartFlightColors =
    SmartFlightColors(
        page = Color(0xFF38366E),
        card = PurpleDark,
        raised = Color(0xFF4F4D8E),
        topBar = PurpleMain,
        topBarScrolled = Color(0xFF67659F),
        accent = CyanMain,
        accentPressed = Color(0x8025E5FE),
        accentLight = CyanLight,
        labelText = Color(0xFFCAC9E3),
        valueText = Color(0xFFF1F0FA),
        toolbarTitle = White,
        satelliteUsed = Color(0xFF4CAF50),
        satelliteUnused = Color(0xFFFF7A6E),
        error = Color(0xFFFFC0B8),
        inverseSurface = Color(0xFFE8E7F5),
        inverseOnSurface = ToastPurple,
        inversePrimary = Color(0xFF00687A),
        overlay50 = Overlay50,
        overlay20 = Overlay20,
        horizonSky = HorizonSky,
        horizonGround = HorizonGround,
        horizonLine = White,
        mapInk = PurpleDark,
        mapHalo = MapHalo,
    )

/**
 * The light scheme, derived from the same hues (TASK-038, "lavender mist"): surfaces are tones of
 * the brand purple hue instead of near-white, as in Material 3 light schemes. The page sits in a
 * mid-light tone, cards one step lighter (luminance ratio >= 1.15 against the page, so the flat
 * cards stand out without shadows) and dialogs the lightest. The top bar uses the page tone at rest
 * (seamless) and turns one step darker while content scrolls under it. Text is dark purple and the
 * accent a dark teal that passes AA on every light surface. Snackbars use the original toast purple
 * with the original cyan action. The map overlays and the horizon instrument keep their colors.
 */
val LightSmartFlightColors =
    SmartFlightColors(
        page = Color(0xFFE6E4F4),
        card = Color(0xFFF7F6FC),
        raised = Color(0xFFFDFCFF),
        topBar = Color(0xFFE6E4F4),
        topBarScrolled = Color(0xFFDAD7EF),
        accent = Color(0xFF00687A),
        accentPressed = Color(0x8000687A),
        accentLight = CyanLight,
        labelText = Color(0xFF55537D),
        valueText = Color(0xFF1E1C3A),
        toolbarTitle = ToastPurple,
        satelliteUsed = Color(0xFF2E7D32),
        satelliteUnused = Color(0xFFC62828),
        error = Color(0xFFB3261E),
        inverseSurface = ToastPurple,
        inverseOnSurface = Color(0xFFF1F0FA),
        inversePrimary = CyanMain,
        overlay50 = Overlay50,
        overlay20 = Overlay20,
        horizonSky = HorizonSky,
        horizonGround = HorizonGround,
        horizonLine = White,
        mapInk = PurpleDark,
        mapHalo = MapHalo,
    )

/**
 * Provides [SmartFlightColors]; the default lets composables rendered without [SmartFlightTheme]
 * (for example in tests) use the dark scheme.
 */
val LocalSmartFlightColors = staticCompositionLocalOf { DarkSmartFlightColors }
