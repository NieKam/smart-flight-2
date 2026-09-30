package kniezrec.com.flightinfo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * The one Material color scheme of the app, built from [colors]. Every role is set explicitly so no
 * Material default (or wallpaper color) leaks into a component. `surfaceTint` equals `surface`, so
 * tonal elevation does not tint cards.
 */
internal fun smartFlightColorScheme(colors: SmartFlightColors): ColorScheme =
    darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.page,
        primaryContainer = colors.card,
        onPrimaryContainer = colors.valueText,
        inversePrimary = colors.accent,
        secondary = colors.accent,
        onSecondary = colors.page,
        secondaryContainer = colors.card,
        onSecondaryContainer = colors.valueText,
        tertiary = colors.accentLight,
        onTertiary = colors.page,
        tertiaryContainer = colors.card,
        onTertiaryContainer = colors.valueText,
        background = colors.page,
        onBackground = colors.valueText,
        surface = colors.card,
        onSurface = colors.valueText,
        surfaceVariant = colors.card,
        onSurfaceVariant = colors.labelText,
        surfaceTint = colors.card,
        inverseSurface = colors.toastBackground,
        inverseOnSurface = colors.valueText,
        error = colors.error,
        onError = colors.page,
        errorContainer = colors.card,
        onErrorContainer = colors.error,
        outline = colors.labelText,
        outlineVariant = colors.overlay20,
        scrim = colors.overlay50,
        surfaceBright = colors.card,
        surfaceContainer = colors.card,
        surfaceContainerHigh = colors.card,
        surfaceContainerHighest = colors.card,
        surfaceContainerLow = colors.card,
        surfaceContainerLowest = colors.card,
        surfaceDim = colors.page,
    )

private val smartFlightColorScheme = smartFlightColorScheme(DefaultSmartFlightColors)

/**
 * The app theme: the original Smart Flight palette, identical in light and dark system themes and
 * with any wallpaper (no dynamic color).
 */
@Composable
fun SmartFlightTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSmartFlightColors provides DefaultSmartFlightColors) {
        MaterialTheme(
            colorScheme = smartFlightColorScheme,
            typography = appTypography,
            content = content,
        )
    }
}

/** Access to the app's design tokens inside composables, next to [MaterialTheme]. */
object SmartFlightTheme {
    val colors: SmartFlightColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSmartFlightColors.current
}
