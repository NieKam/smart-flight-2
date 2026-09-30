package kniezrec.com.flightinfo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * Every color token and every color resource is in the palette table of TASK-018: the original
 * Smart Flight palette plus the documented extras. A new color needs a line in that table first.
 */
class PaletteGuardTest {
    @Test
    fun everySmartFlightColorsTokenIsInThePalette() {
        val tokens = tokens(DefaultSmartFlightColors)
        // Every property is seen (a getter the scan misses would escape the guard).
        val properties = SmartFlightColors::class.java.declaredFields.count { !Modifier.isStatic(it.modifiers) }
        assertEquals(properties, tokens.size)

        val outside = tokens.filterValues { it !in PALETTE }
        assertTrue("Tokens outside the palette: ${outside.mapValues { hex(it.value) }}", outside.isEmpty())
    }

    @Test
    fun everyColorResourceIsInThePalette() {
        // Gradle runs unit tests with the module directory as the working directory.
        val file = File("src/main/res/values/colors.xml")
        assertTrue("Color resources not found: ${file.absolutePath}", file.isFile)
        val xml = file.readText()
        val entries =
            COLOR_RESOURCE.findAll(xml).associate { match ->
                val digits = match.groupValues[2]
                val argb = (if (digits.length == 6) "FF$digits" else digits).toLong(16).toInt()
                match.groupValues[1] to argb
            }
        assertEquals("Every <color> must be a literal #RRGGBB or #AARRGGBB", Regex("<color\\b").findAll(xml).count(), entries.size)

        val outside = entries.filterValues { it !in PALETTE }
        assertTrue("Color resources outside the palette: ${outside.mapValues { hex(it.value) }}", outside.isEmpty())
    }

    @Test
    fun mapDrawablesUseOnlyPaletteColors() {
        val drawables = File("src/main/res/drawable")
        for (name in MAP_DRAWABLES) {
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
        /** The plane marker and the route pins drawn on the map. */
        val MAP_DRAWABLES = listOf("ic_plane_marker", "ic_map_pin_departure", "ic_map_pin_destination")

        val DRAWABLE_COLOR = Regex("android:(?:fillColor|strokeColor)=\"#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})\"")

        val COLOR_RESOURCE = Regex("""<color\s+name="([^"]+)"\s*>\s*#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})\s*</color>""")

        val PALETTE =
            setOf(
                // Original colors.xml
                0xFF484685, // purple_dark: page
                0xFF5B5999, // purple_main: card
                0xFF25E5FE, // cyan_main: accent
                0x8025E5FE, // cyan_main_50: accentPressed
                0xFF99E5FC, // cyan_light: accentLight
                0xFFA1A0C4, // text_color_dark: labelText
                0xFFD9D9ED, // text_color_light: valueText
                0xFF4CAF50, // satellite_green
                0xFFF44336, // satellite_red
                0x80000000, // dark_overlay_alpha_50
                0x33000000, // dark_overlay_alpha_20
                0xFF2C2163, // toast_background
                0xFFFFFFFF, // toolbar title (dark action bar)
                // Documented extras
                0xFFFFB4AB, // error
                0xFF7775B5, // horizon sky
                0xFF3F3D70, // horizon ground
            ).map { it.toInt() }.toSet()
    }
}
