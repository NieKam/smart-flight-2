package kniezrec.com.flightinfo.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Colors live in `ui/theme` as tokens; UI code must not declare its own color literals. */
class ColorLiteralGuardTest {
    @Test
    fun noColorLiteralOutsideUiTheme() {
        // Gradle runs unit tests with the module directory as the working directory.
        val sources = File("src/main/java")
        assertTrue("Source directory not found: ${sources.absolutePath}", sources.isDirectory)
        val themeDirectory = File(sources, "kniezrec/com/flightinfo/ui/theme")

        val offenders =
            sources
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filterNot { it.startsWith(themeDirectory) }
                .flatMap { file ->
                    file.readLines().mapIndexedNotNull { index, line ->
                        if (COLOR_LITERAL.containsMatchIn(line)) "${file.path}:${index + 1}: ${line.trim()}" else null
                    }
                }.toList()

        assertTrue("Color literals outside ui/theme:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    private companion object {
        val COLOR_LITERAL = Regex("""Color\s*\(\s*0x""")
    }
}
