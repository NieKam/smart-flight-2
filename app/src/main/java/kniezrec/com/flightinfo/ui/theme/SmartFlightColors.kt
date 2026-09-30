package kniezrec.com.flightinfo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's own color tokens: the original Smart Flight palette (`colors.xml` of the original app)
 * plus a few documented extras. Every screen, card, dialog and overlay takes its colors from here
 * (directly or through [SmartFlightTheme]'s Material color scheme); read them through
 * [SmartFlightTheme.colors]. `PaletteGuardTest` keeps new tokens inside the palette.
 */
@Immutable
data class SmartFlightColors(
    /** `purple_dark`: page (window) background, dialogs, city picker. */
    val page: Color,
    /** `purple_main`: cards, top bar and status bar, picker buttons. */
    val card: Color,
    /** `cyan_main`: actions, switches, radio buttons, text-field indicator, compass cardinal. */
    val accent: Color,
    /** `cyan_main_50`: pressed/highlight accent, checked switch track. */
    val accentPressed: Color,
    /** `cyan_light`: secondary accent, only where the original app used it. */
    val accentLight: Color,
    /** `text_color_dark`: muted labels, card titles, dialog titles, secondary text. */
    val labelText: Color,
    /** `text_color_light`: values, body text, dialog content, primary text. */
    val valueText: Color,
    /** `satellite_green`: satellite used in the fix. */
    val satelliteUsed: Color,
    /** `satellite_red`: satellite not used in the fix. */
    val satelliteUnused: Color,
    /** `dark_overlay_alpha_50`: strong dim overlay. */
    val overlay50: Color,
    /** `dark_overlay_alpha_20`: light dim overlay, dividers, unchecked switch track. */
    val overlay20: Color,
    /** `toast_background`: toast and snackbar background. */
    val toastBackground: Color,
    /** Top bar title and icons (white, as the original dark action bar). */
    val toolbarTitle: Color,
    /** Documented extra: inline error text. */
    val error: Color,
    /** Documented extra: artificial horizon sky half. */
    val horizonSky: Color,
    /** Documented extra: artificial horizon ground half. */
    val horizonGround: Color,
)

internal val DefaultSmartFlightColors =
    SmartFlightColors(
        page = Color(0xFF484685),
        card = Color(0xFF5B5999),
        accent = Color(0xFF25E5FE),
        accentPressed = Color(0x8025E5FE),
        accentLight = Color(0xFF99E5FC),
        labelText = Color(0xFFA1A0C4),
        valueText = Color(0xFFD9D9ED),
        satelliteUsed = Color(0xFF4CAF50),
        satelliteUnused = Color(0xFFF44336),
        overlay50 = Color(0x80000000),
        overlay20 = Color(0x33000000),
        toastBackground = Color(0xFF2C2163),
        toolbarTitle = Color(0xFFFFFFFF),
        error = Color(0xFFFFB4AB),
        horizonSky = Color(0xFF7775B5),
        horizonGround = Color(0xFF3F3D70),
    )

/**
 * Provides [SmartFlightColors]; the default lets composables rendered without [SmartFlightTheme]
 * (for example in tests) use the same colors.
 */
val LocalSmartFlightColors = staticCompositionLocalOf { DefaultSmartFlightColors }
