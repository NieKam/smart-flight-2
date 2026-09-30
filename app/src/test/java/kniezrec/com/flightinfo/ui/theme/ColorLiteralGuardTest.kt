package kniezrec.com.flightinfo.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Colors live in `ui/theme` as palette tokens; UI code must not declare its own color literals or
 * use named platform/Compose colors (only `Color.Transparent` and `Color.Unspecified` are allowed).
 */
class ColorLiteralGuardTest {
    @Test
    fun noColorLiteralOutsideUiTheme() {
        val offenders = offendingLines(COLOR_LITERAL)
        assertTrue("Color literals outside ui/theme:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test
    fun noNamedColorConstantOutsideUiTheme() {
        val offenders = offendingLines(ANDROID_NAMED_COLOR) + offendingLines(COMPOSE_NAMED_COLOR)
        assertTrue("Named colors outside ui/theme:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    private fun offendingLines(pattern: Regex): List<String> {
        // Gradle runs unit tests with the module directory as the working directory.
        val sources = File("src/main/java")
        assertTrue("Source directory not found: ${sources.absolutePath}", sources.isDirectory)
        val themeDirectory = File(sources, "kniezrec/com/flightinfo/ui/theme")
        return sources
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.startsWith(themeDirectory) }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (pattern.containsMatchIn(line)) "${file.path}:${index + 1}: ${line.trim()}" else null
                }
            }.toList()
    }

    private companion object {
        val COLOR_LITERAL = Regex("""Color\s*\(\s*0x""")

        // android.graphics.Color.CYAN, Color.WHITE, … (upper-case constants), and parseColor.
        val ANDROID_NAMED_COLOR = Regex("""\bColor\.(?:[A-Z][A-Z_]+\b|parseColor)""")

        // Compose's named colors other than Transparent and Unspecified.
        val COMPOSE_NAMED_COLOR = Regex("""\bColor\.(?:Black|DarkGray|Gray|LightGray|White|Red|Green|Blue|Yellow|Cyan|Magenta)\b""")
    }
}
