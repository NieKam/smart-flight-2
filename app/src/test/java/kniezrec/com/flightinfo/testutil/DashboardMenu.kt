package kniezrec.com.flightinfo.testutil

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

/** Content description of the dashboard top bar's "⋮" button (`R.string.dashboard_more_options`). */
const val MORE_OPTIONS = "More options"

/** Opens the dashboard's overflow menu and picks the entry labelled [label] (Settings or About). */
fun ComposeTestRule.openFromOverflowMenu(label: String) {
    onNodeWithContentDescription(MORE_OPTIONS).performClick()
    onNodeWithText(label).performClick()
}
