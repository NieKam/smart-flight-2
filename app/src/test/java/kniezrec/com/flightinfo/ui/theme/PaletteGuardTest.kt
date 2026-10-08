package kniezrec.com.flightinfo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Every color token (of every scheme) and every color resource is in the token table of TASK-037:
 * colors of the original Smart Flight palette and tones derived from it. A new color needs a line in
 * that table first.
 */
class PaletteGuardTest {
    @Test
    fun everySmartFlightColorsTokenIsInThePalette() {
        val tokens = SCHEMES.flatMap { (scheme, colors) -> tokens(colors).map { (name, argb) -> "$scheme.$name" to argb } }.toMap()
        // Every property is seen (a getter the scan misses would escape the guard).
        val properties = SmartFlightColors::class.java.declaredFields.count { !Modifier.isStatic(it.modifiers) }
        assertEquals(properties * SCHEMES.size, tokens.size)

        val outside = tokens.filterValues { it !in PALETTE }
        assertTrue("Tokens outside the palette: ${outside.mapValues { hex(it.value) }}", outside.isEmpty())
    }

    @Test
    fun everyColorResourceIsInThePalette() {
        val xml = colorResourcesXml()
        val entries = colorResources(xml)
        assertEquals("Every <color> must be a literal #RRGGBB or #AARRGGBB", Regex("<color\\b").findAll(xml).count(), entries.size)

        val outside = entries.filterValues { it !in PALETTE }
        assertTrue("Color resources outside the palette: ${outside.mapValues { hex(it.value) }}", outside.isEmpty())
    }

    /** The launch window matches the page of each scheme, so there is no flash before Compose draws. */
    @Test
    fun windowBackgroundsMatchThePageTokens() {
        val entries = colorResources(colorResourcesXml())
        assertEquals(hex(LightSmartFlightColors.page.toArgb()), entries["window_light"]?.let(::hex))
        assertEquals(hex(DarkSmartFlightColors.page.toArgb()), entries["window_dark"]?.let(::hex))
    }

    private fun colorResourcesXml(): String {
        // Gradle runs unit tests with the module directory as the working directory.
        val file = File("src/main/res/values/colors.xml")
        assertTrue("Color resources not found: ${file.absolutePath}", file.isFile)
        return file.readText()
    }

    private fun colorResources(xml: String): Map<String, Int> =
        COLOR_RESOURCE.findAll(xml).associate { match ->
            val digits = match.groupValues[2]
            val argb = (if (digits.length == 6) "FF$digits" else digits).toLong(16).toInt()
            match.groupValues[1] to argb
        }

    @Test
    fun mapRouteCardAndLauncherDrawablesUseOnlyPaletteColors() {
        val drawables = File("src/main/res/drawable")
        for (name in MAP_DRAWABLES + ROUTE_CARD_DRAWABLES + LAUNCHER_DRAWABLES) {
            val file = File(drawables, "$name.xml")
            assertTrue("Drawable not found: ${file.absolutePath}", file.isFile)
            val colors =
                DRAWABLE_COLOR
                    .findAll(file.readText())
                    .map { match ->
                        val digits = match.groupValues[1]
                        (if (digits.length == 6) "FF$digits" else digits).toLong(16).toInt()
                    }.toList()
            assertTrue("$name declares no colors", colors.isNotEmpty())
            val outside = colors.filter { it !in PALETTE }
            assertTrue("$name uses colors outside the palette: ${outside.map(::hex)}", outside.isEmpty())
        }
    }

    @Test
    fun launcherIconsUseThePaletteBackgroundAndThePlaneLayers() {
        // TASK-034: the original icon, a plane on flat purple_dark, with a themed (monochrome) layer.
        for (name in listOf("ic_launcher", "ic_launcher_round")) {
            val file = File("src/main/res/mipmap-anydpi/$name.xml")
            assertTrue("Launcher icon not found: ${file.absolutePath}", file.isFile)
            val xml = file.readText()
            assertTrue("$name background", xml.contains("<background android:drawable=\"@color/purple_dark\""))
            assertTrue("$name foreground", xml.contains("<foreground android:drawable=\"@drawable/ic_launcher_foreground\""))
            assertTrue("$name monochrome", xml.contains("<monochrome android:drawable=\"@drawable/ic_launcher_monochrome\""))
        }
    }

    @Test
    fun mapCardDeclaresNoColorsOfItsOwn() {
        // The map buttons take their colors from the tokens (no background such as the former #DD25133F).
        val file = File("src/main/java/kniezrec/com/flightinfo/map/ui/MapCard.kt")
        assertTrue("Map card not found: ${file.absolutePath}", file.isFile)
        val literals = COLOR_LITERAL.findAll(file.readText()).map { it.value }.toList()
        assertTrue("MapCard.kt declares colors outside the tokens: $literals", literals.isEmpty())
    }

    @Test
    fun cityPickerDeclaresNoColorsOfItsOwn() {
        // TASK-032: every picker color comes from the tokens.
        val file = File("src/main/java/kniezrec/com/flightinfo/route/ui/RoutePicker.kt")
        assertTrue("City picker not found: ${file.absolutePath}", file.isFile)
        val literals = COLOR_LITERAL.findAll(file.readText()).map { it.value }.toList()
        assertTrue("RoutePicker.kt declares colors outside the tokens: $literals", literals.isEmpty())
    }

    @Test
    fun routeCardDeclaresNoColorsOfItsOwn() {
        // TASK-033: the route card's icons and texts are tinted with the tokens.
        val file = File("src/main/java/kniezrec/com/flightinfo/route/ui/RouteCard.kt")
        assertTrue("Route card not found: ${file.absolutePath}", file.isFile)
        val literals = COLOR_LITERAL.findAll(file.readText()).map { it.value }.toList()
        assertTrue("RouteCard.kt declares colors outside the tokens: $literals", literals.isEmpty())
    }

    private fun tokens(colors: SmartFlightColors): Map<String, Int> =
        // A Color property compiles to a public getter returning the packed Long (value class).
        SmartFlightColors::class.java.declaredMethods
            .filter {
                Modifier.isPublic(it.modifiers) &&
                    !Modifier.isStatic(it.modifiers) &&
                    it.name.startsWith("get") &&
                    it.parameterCount == 0 &&
                    it.returnType == java.lang.Long.TYPE
            }.associate { getter ->
                val packed = getter.invoke(colors) as Long
                getter.name to Color(packed.toULong()).toArgb()
            }

    private fun hex(argb: Int) = "#%08X".format(argb)

    private companion object {
        val SCHEMES = listOf("dark" to DarkSmartFlightColors, "light" to LightSmartFlightColors)

        /** The plane marker, the route pins, the city picker marker and the button icons drawn on the maps. */
        val MAP_DRAWABLES =
            listOf(
                "ic_plane_marker",
                "ic_map_pin_departure",
                "ic_map_pin_destination",
                "ic_city_found_marker",
                "ic_expand",
                "ic_shrink",
                "drawing_pin_icon",
            )

        /** The original take-off, landing and trash icons of the route card. */
        val ROUTE_CARD_DRAWABLES = listOf("ic_route_take_off", "ic_route_landing", "ic_route_delete")

        /** The launcher icon's plane layers (TASK-034) and the top bar's overflow icon. */
        val LAUNCHER_DRAWABLES = listOf("ic_launcher_foreground", "ic_launcher_monochrome", "ic_more_vert")

        /** A Compose color literal (`Color(0x…)`, `Color(red, …)`) or an Android `Color.parseColor`/`Color.rgb`. */
        val COLOR_LITERAL = Regex("""\bColor\s*\(\s*(0x|\d)|Color\.(parseColor|rgb|argb)\b""")

        val DRAWABLE_COLOR = Regex("android:(?:fillColor|strokeColor)=\"#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})\"")

        val COLOR_RESOURCE = Regex("""<color\s+name="([^"]+)"\s*>\s*#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})\s*</color>""")

        /**
         * The token table of TASK-037 (derived from the original palette; see `SmartFlightColors`).
         * A new color needs a line here first.
         */
        val PALETTE =
            setOf(
                // Original colors.xml, still in use
                0xFF484685, // purple_dark: dark card, map ink, launcher background
                0xFF5B5999, // purple_main: dark top bar
                0xFF25E5FE, // cyan_main: dark accent, light snackbar action
                0x8025E5FE, // cyan_main_50: dark accentPressed
                0xFF99E5FC, // cyan_light: accentLight
                0xFFD9D9ED, // text_color_light: map halo (button container, pin and marker outline)
                0xFF4CAF50, // satellite_green: dark satelliteUsed
                0x80000000, // dark_overlay_alpha_50: scrim
                0x33000000, // dark_overlay_alpha_20: dividers, switch track
                0xFF2C2163, // toast_background: dark snackbar text, light toolbar title and snackbar
                0xFFFFFFFF, // white: dark toolbar title, horizon line
                // Dark scheme, derived (TASK-037)
                0xFF38366E, // page: purple_dark one step darker (and window_dark)
                0xFF4F4D8E, // raised: dialogs and menus
                0xFF67659F, // topBarScrolled
                0xFFCAC9E3, // labelText (AA on page, card, raised)
                0xFFF1F0FA, // valueText
                0xFFFF7A6E, // satelliteUnused: satellite_red lightened to 3:1 on the card
                0xFFFFC0B8, // error
                0xFFE8E7F5, // inverseSurface (snackbar)
                0xFF00687A, // inversePrimary (snackbar action)
                // Light scheme, derived (TASK-037)
                0xFFE6E4F4, // page and topBar (and window_light), lavender mist (TASK-038)
                0xFFF7F6FC, // card
                0xFFFDFCFF, // raised: dialogs and menus
                0xFFDAD7EF, // topBarScrolled
                0xFF00687A, // accent: dark teal (also the dark scheme's inversePrimary)
                0x8000687A, // accentPressed
                0xFF55537D, // labelText
                0xFF1E1C3A, // valueText
                0xFF2E7D32, // satelliteUsed: satellite_green darkened to 3:1 on the light card
                0xFFC62828, // satelliteUnused: satellite_red darkened
                0xFFB3261E, // error
                0xFFF1F0FA, // inverseOnSurface (the dark valueText)
                // Horizon instrument (theme-independent)
                0xFF7775B5, // horizon sky
                0xFF3F3D70, // horizon ground
            ).map { it.toInt() }.toSet()
    }
}
