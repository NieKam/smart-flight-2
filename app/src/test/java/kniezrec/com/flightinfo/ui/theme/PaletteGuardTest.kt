package kniezrec.com.flightinfo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Every color token (of every scheme) and every color resource is in the token table of TASK-039
 * (the redesign), plus the original colors still used on the map and the launcher icon. A new color needs a line in
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
        for (name in MAP_DRAWABLES + ROUTE_CARD_DRAWABLES + LAUNCHER_DRAWABLES + CARD_HEADER_DRAWABLES) {
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

        /** The plane marker, the route pins, the city picker marker and the expand/collapse icons drawn on the maps. */
        val MAP_DRAWABLES =
            listOf(
                "ic_plane_marker",
                "ic_map_pin_departure",
                "ic_map_pin_destination",
                "ic_city_found_marker",
                "ic_expand",
                "ic_shrink",
            )

        /** The original take-off, landing and trash icons of the route card. */
        val ROUTE_CARD_DRAWABLES = listOf("ic_route_take_off", "ic_route_landing", "ic_route_delete")

        /** The redesign's icons (card headers, Calibrate, flight tiles, Settings; TASK-040–048), tinted in code. */
        val CARD_HEADER_DRAWABLES =
            listOf(
                "ic_card_satellite",
                "ic_card_course",
                "ic_card_horizon",
                "ic_card_flight",
                "ic_card_place",
                "ic_calibrate",
                "ic_tile_gauge",
                "ic_tile_altitude",
                "ic_tile_vertical_speed",
                "ic_settings_display",
                "ic_settings_monitoring",
                "ic_settings_units",
                "ic_chevron_right",
            )

        /** The launcher icon's plane layers (TASK-034) and the top bar's overflow icon. */
        val LAUNCHER_DRAWABLES = listOf("ic_launcher_foreground", "ic_launcher_monochrome", "ic_more_vert")

        /** A Compose color literal (`Color(0x…)`, `Color(red, …)`) or an Android `Color.parseColor`/`Color.rgb`. */
        val COLOR_LITERAL = Regex("""\bColor\s*\(\s*(0x|\d)|Color\.(parseColor|rgb|argb)\b""")

        val DRAWABLE_COLOR = Regex("android:(?:fillColor|strokeColor)=\"#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})\"")

        val COLOR_RESOURCE = Regex("""<color\s+name="([^"]+)"\s*>\s*#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})\s*</color>""")

        /**
         * The token table of TASK-039 (see `SmartFlightColors` and docs/tasks/039-redesign-tokens).
         * A new color needs a line here first.
         */
        val PALETTE =
            setOf(
                // Original colors.xml, still used on the map and the launcher icon
                0xFF484685, // purple_dark: map ink, light route line, launcher background
                0xFFD9D9ED, // text_color_light: map halo, route card icon sources (tinted in code)
                0x80000000, // dark_overlay_alpha_50: scrim
                0x33000000, // dark_overlay_alpha_20: switch track, launcher shadow
                0xFFFFFFFF, // white: light card, horizon line, icon sources (tinted in code)
                0xFF4CAF50, // satellite_green: dark satelliteUsed
                // Dark scheme (TASK-039 redesign)
                0xFF111722, // page and topBar (and window_dark)
                0xFF1A2330, // card
                0xFF232D3C, // raised: dialogs and menus
                0xFF1E2735, // topBarScrolled
                0xFF283245, // cardOutline
                0xFF4A9BFD, // accent, compassPlane
                0xFF1C304A, // accentContainer
                0xFF23436B, // accentLight
                0xFF9DAED0, // labelText
                0xFFE8ECF5, // valueText, toolbarTitle, inverseSurface, mapRoute
                0xFFFF7A6E, // satelliteUnused
                0xFFFFB4AB, // error
                0xFF1558C0, // inversePrimary (snackbar action)
                0xFF0F3D74, // horizonSkyTop
                0xFF1C5EA0, // horizonSky
                0xFF295744, // horizonGround
                0xFF143839, // horizonGroundBottom
                0xFF4A5878, // mapTileTint: dims the tiles (TASK-047)
                // Light scheme (TASK-039 redesign)
                0xFFF4F6FB, // page and topBar (and window_light)
                0xFFF9FAFD, // raised: dialogs and menus
                0xFFE9EDF5, // topBarScrolled
                0xFFE2E7F0, // cardOutline
                0xFF1A66D9, // accent
                0xFFE7F0FD, // accentContainer
                0xFFCFE0FA, // accentLight
                0xFF5B6785, // labelText
                0xFF172340, // valueText, toolbarTitle, compassPlane, inverseSurface
                0xFF2E7D32, // satelliteUsed
                0xFFC62828, // satelliteUnused
                0xFFB3261E, // error
                0xFFF1F4FA, // inverseOnSurface
                0xFF8DBBFF, // inversePrimary (snackbar action)
                0xFF2A73C9, // horizonSkyTop
                0xFF3B87DB, // horizonSky
                0xFF3E7F5B, // horizonGround
                0xFF2B6249, // horizonGroundBottom
            ).map { it.toInt() }.toSet()
    }
}
