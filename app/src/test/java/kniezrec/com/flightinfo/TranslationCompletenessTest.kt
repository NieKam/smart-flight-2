package kniezrec.com.flightinfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every translatable string and plural of `values/strings.xml` has a Polish translation in
 * `values-pl/strings.xml`, with the same format arguments, and every Polish plural defines the
 * categories Polish needs (a missing one silently falls back to `other` at runtime).
 */
class TranslationCompletenessTest {
    private val defaults = parse(DEFAULT_STRINGS)
    private val polish = parse(POLISH_STRINGS)

    @Test
    fun everyTranslatableStringHasPolishTranslation() {
        val missing = defaults.filterValues { it.translatable }.keys - polish.keys
        assertTrue("Missing Polish translations: ${missing.sorted()}", missing.isEmpty())
    }

    @Test
    fun polishHasNoUnknownOrNonTranslatableEntries() {
        val translatable = defaults.filterValues { it.translatable }.keys
        val extra = polish.keys - translatable
        assertTrue("Polish entries that are unknown or translatable=\"false\" in values: ${extra.sorted()}", extra.isEmpty())
    }

    @Test
    fun polishTranslationsAreNotBlank() {
        val blank =
            polish.values
                .filter { resource -> resource.texts.values.any { it.isBlank() } }
                .map { it.key }
        assertTrue("Blank Polish translations: $blank", blank.isEmpty())
    }

    @Test
    fun formatArgumentsMatch() {
        val mismatches =
            polish.values.flatMap { translated ->
                val original = defaults[translated.key] ?: return@flatMap emptyList<String>()
                val expected = formatArguments(original.referenceText())
                translated.texts.mapNotNull { (quantity, text) ->
                    val actual = formatArguments(text)
                    if (actual == expected) null else "${translated.key}[$quantity]: expected $expected, was $actual"
                }
            }
        assertTrue("Format arguments differ:\n${mismatches.joinToString("\n")}", mismatches.isEmpty())
    }

    @Test
    fun polishPluralsDefineAllPolishCategories() {
        val incomplete =
            polish.values
                .filter { it.key.type == PLURALS }
                .filter { it.texts.keys != POLISH_PLURAL_CATEGORIES }
                .map { "${it.key.name}: ${it.texts.keys.sorted()}" }
        assertTrue("Polish plurals need $POLISH_PLURAL_CATEGORIES:\n${incomplete.joinToString("\n")}", incomplete.isEmpty())
    }

    @Test
    fun parserFindsResourcesInBothFiles() {
        // Guards the other tests against passing vacuously on an empty or misparsed file.
        assertTrue(defaults.size > 100)
        assertEquals(defaults.filterValues { it.translatable }.size, polish.size)
        assertTrue(polish.keys.any { it.type == PLURALS })
    }

    private data class Key(
        val type: String,
        val name: String,
    ) : Comparable<Key> {
        override fun compareTo(other: Key): Int = compareValuesBy(this, other, { it.type }, { it.name })

        override fun toString(): String = "$type/$name"
    }

    private data class Resource(
        val key: Key,
        val translatable: Boolean,
        /** The text of a `<string>` under the key [STRING]; the text of each plural item under its quantity. */
        val texts: Map<String, String>,
    ) {
        /** The text whose format arguments a translation must match: the string, or the plural's `other` item. */
        fun referenceText(): String = texts[STRING] ?: texts.getValue("other")
    }

    private fun parse(path: String): Map<Key, Resource> {
        val file = resourceFile(path)
        val root =
            DocumentBuilderFactory
                .newInstance()
                .newDocumentBuilder()
                .parse(file)
                .documentElement
        return root
            .childElements()
            .filter { it.tagName == STRING || it.tagName == PLURALS }
            .map { element ->
                val texts =
                    if (element.tagName == STRING) {
                        mapOf(STRING to element.textContent)
                    } else {
                        element
                            .childElements()
                            .filter { it.tagName == "item" }
                            .associate { it.getAttribute("quantity") to it.textContent }
                    }
                Resource(
                    key = Key(element.tagName, element.getAttribute("name")),
                    translatable = element.getAttribute("translatable") != "false",
                    texts = texts,
                )
            }.associateBy { it.key }
    }

    private fun Element.childElements(): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).map { nodes.item(it) }.filterIsInstance<Element>()
    }

    private fun resourceFile(path: String): File {
        // Gradle runs unit tests with the module directory as the working directory; an IDE may
        // use the project root instead.
        val file = listOf(File(path), File("app", path)).firstOrNull { it.isFile }
        return checkNotNull(file) { "Resource file not found: ${File(path).absolutePath}" }
    }

    private fun formatArguments(text: String): List<String> =
        FORMAT_ARGUMENT
            .findAll(text)
            .map { it.value }
            .sorted()
            .toList()

    private companion object {
        const val STRING = "string"
        const val PLURALS = "plurals"
        const val DEFAULT_STRINGS = "src/main/res/values/strings.xml"
        const val POLISH_STRINGS = "src/main/res/values-pl/strings.xml"
        val POLISH_PLURAL_CATEGORIES = setOf("one", "few", "many", "other")

        // java.util.Formatter conversions: %d, %1$s, %2$02d, %.1f, %%.
        val FORMAT_ARGUMENT = Regex("""%(?:\d+\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z%]""")
    }
}
