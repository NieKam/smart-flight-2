package kniezrec.com.flightinfo.ui.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The shared card (TASK-037): 16dp content padding, optional minimum height, used by every card. */
@RunWith(AndroidJUnit4::class)
class SmartFlightCardTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun contentIsPaddedSixteenDpAndCenteredInTheMinimumHeight() {
        composeRule.setContent {
            SmartFlightTheme {
                Box(Modifier.testTag(CARD_TAG)) {
                    SmartFlightCard(minHeight = SmartFlightCardDefaults.MinHeight) {
                        Box(Modifier.size(40.dp).testTag(CONTENT_TAG))
                    }
                }
            }
        }
        val density = composeRule.density.density
        val card = composeRule.onNodeWithTag(CARD_TAG).fetchSemanticsNode().boundsInRoot
        val content = composeRule.onNodeWithTag(CONTENT_TAG).fetchSemanticsNode().boundsInRoot

        assertEquals(160f * density, card.height, 1f)
        assertEquals(16f * density, content.left - card.left, 1f)
        // Shorter than the minimum height: centered vertically.
        assertEquals(card.center.y, content.center.y, 1f)
    }

    @Test
    fun noPaddingDrawsContentEdgeToEdge() {
        composeRule.setContent {
            SmartFlightTheme {
                Box(Modifier.testTag(CARD_TAG)) {
                    SmartFlightCard(contentPadding = SmartFlightCardDefaults.NoPadding) {
                        Box(Modifier.size(40.dp).testTag(CONTENT_TAG))
                    }
                }
            }
        }
        val card = composeRule.onNodeWithTag(CARD_TAG).fetchSemanticsNode().boundsInRoot
        val content = composeRule.onNodeWithTag(CONTENT_TAG).fetchSemanticsNode().boundsInRoot

        assertEquals(card.left, content.left, 1f)
        assertEquals(card.top, content.top, 1f)
        assertEquals(card.height, content.height, 1f)
    }

    @Test
    fun everyDashboardCardUsesTheSharedCardWithoutItsOwnShapeOrElevation() {
        // Gradle runs unit tests with the module directory as the working directory.
        val sources = File("src/main/java/kniezrec/com/flightinfo")
        val offenders =
            CARD_FILES.flatMap { path ->
                val file = File(sources, path)
                assertTrue("Card source not found: ${file.absolutePath}", file.isFile)
                val text = file.readText()
                buildList {
                    if (!text.contains("SmartFlightCard(")) add("$path does not use SmartFlightCard")
                    if (MATERIAL_CARD_IMPORT.containsMatchIn(text)) add("$path imports a Material card")
                    if (text.contains("cardElevation")) add("$path sets its own elevation")
                    if (text.contains("RoundedCornerShape(10")) add("$path sets its own card shape")
                }
            }
        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }

    private companion object {
        const val CARD_TAG = "card"
        const val CONTENT_TAG = "content"

        /** The eight dashboard cards (TASK-033 review finding). */
        val CARD_FILES =
            listOf(
                "flight/ui/FlightParametersCard.kt",
                "course/ui/CourseCard.kt",
                "horizon/ui/HorizonCard.kt",
                "nearby/ui/NearbyCityCard.kt",
                "route/ui/RouteCard.kt",
                "gnss/ui/GnssStatusCard.kt",
                "map/ui/MapCard.kt",
                "permission/ui/LocationPermissionCard.kt",
            )

        val MATERIAL_CARD_IMPORT = Regex("""import androidx\.compose\.material3\.(Card|ElevatedCard|OutlinedCard|CardDefaults)\b""")
    }
}
