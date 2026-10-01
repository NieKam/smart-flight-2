package kniezrec.com.flightinfo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * The Material color scheme built from [colors]. Every role is set explicitly so no Material default
 * (or wallpaper color) leaks into a component. Elevation is tonal through distinct surface roles:
 * `surface`/`background` for the page, `surfaceContainer` for cards, `surfaceContainerHigh` for
 * dialogs and menus, `inverseSurface` for snackbars. `surfaceTint` equals `surface`, so Material's
 * own tonal overlay does not tint anything further.
 */
internal fun smartFlightColorScheme(colors: SmartFlightColors): ColorScheme =
    darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.page,
        primaryContainer = colors.raised,
        onPrimaryContainer = colors.valueText,
        inversePrimary = colors.inversePrimary,
        secondary = colors.accent,
        onSecondary = colors.page,
        secondaryContainer = colors.raised,
        onSecondaryContainer = colors.valueText,
        tertiary = colors.accentLight,
        onTertiary = colors.page,
        tertiaryContainer = colors.raised,
        onTertiaryContainer = colors.valueText,
        background = colors.page,
        onBackground = colors.valueText,
        surface = colors.page,
        onSurface = colors.valueText,
        surfaceVariant = colors.card,
        onSurfaceVariant = colors.labelText,
        surfaceTint = colors.page,
        inverseSurface = colors.inverseSurface,
        inverseOnSurface = colors.inverseOnSurface,
        error = colors.error,
        onError = colors.page,
        errorContainer = colors.card,
        onErrorContainer = colors.error,
        outline = colors.labelText,
        outlineVariant = colors.overlay20,
        scrim = colors.overlay50,
        surfaceBright = colors.raised,
        surfaceContainer = colors.card,
        surfaceContainerHigh = colors.raised,
        surfaceContainerHighest = colors.raised,
        surfaceContainerLow = colors.card,
        surfaceContainerLowest = colors.page,
        surfaceDim = colors.page,
    )

private val darkScheme = smartFlightColorScheme(DarkSmartFlightColors)

/** The app theme: the Smart Flight palette with any wallpaper (no dynamic color). */
@Composable
fun SmartFlightTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSmartFlightColors provides DarkSmartFlightColors) {
        MaterialTheme(
            colorScheme = darkScheme,
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
