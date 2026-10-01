package kniezrec.com.flightinfo.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** OpenType tabular figures: every digit has the same width, so changing values do not jump. */
internal const val TABULAR_FIGURES = "tnum"

/** Size of a unit next to its number ("12 500 ft"), relative to the number. */
internal val UNIT_RELATIVE_SIZE = 0.6.em

private val defaults = Typography()

/**
 * The app's type scale (TASK-037), Material 3 roles with the app's sizes:
 * - **Key values** (speed, altitude, vertical speed, pressure): [Typography.headlineMedium].
 * - **Compass heading**: [Typography.displayMedium].
 * - **Secondary values** (nearby city, route values, GPS bearing): [Typography.titleMedium].
 * - **Card titles**: [Typography.titleLarge].
 * - **Row labels**: [Typography.labelLarge] (medium weight, smaller than every value).
 * - **Body text**: [Typography.bodyLarge] and [Typography.bodyMedium].
 *
 * Value styles use tabular figures ([TABULAR_FIGURES]); units are drawn smaller with
 * [withSmallerUnit].
 */
val appTypography =
    defaults.copy(
        displayMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 45.sp,
                lineHeight = 52.sp,
                letterSpacing = 0.sp,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 18.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.1.sp,
                fontFeatureSettings = TABULAR_FIGURES,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp,
            ),
        // Larger than Material's 14sp: row labels are read at arm's length in a moving aircraft.
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                letterSpacing = 0.1.sp,
            ),
    )
