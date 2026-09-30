package kniezrec.com.flightinfo.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The app's own color tokens, used by every screen, card and overlay instead of color literals.
 * Read them through [SmartFlightTheme.colors].
 */
@Immutable
data class SmartFlightColors(
    /** Page (window) background behind the cards. */
    val page: Color,
    /** Card surface. */
    val card: Color,
    /** Actions, links and highlighted values on cards. */
    val accent: Color,
    /** Text and line art on cards and on the page. */
    val text: Color,
    /** Error message text on cards. */
    val error: Color,
    /** Artificial horizon: sky half. */
    val horizonSky: Color,
    /** Artificial horizon: ground half. */
    val horizonGround: Color,
    /** Background of the buttons drawn over the map. */
    val mapButtonBackground: Color,
    /** Glyphs and hints drawn over the map. */
    val mapOverlayContent: Color,
    /** Full-screen city picker background. */
    val pickerBackground: Color,
)

internal val DefaultSmartFlightColors =
    SmartFlightColors(
        page = Color(0xFF484685),
        card = Color(0xFF5B5999),
        accent = Color(0xFF6CF0FF),
        text = Color(0xFFD9D9ED),
        error = Color(0xFFFFB4AB),
        horizonSky = Color(0xFF7775B5),
        horizonGround = Color(0xFF3F3D70),
        mapButtonBackground = Color(0xDD25133F),
        mapOverlayContent = Color.White,
        pickerBackground = Color(0xFF211D46),
    )

/**
 * Provides [SmartFlightColors]; the default lets composables rendered without [SmartFlightTheme]
 * (for example in tests) use the same colors.
 */
val LocalSmartFlightColors = staticCompositionLocalOf { DefaultSmartFlightColors }
