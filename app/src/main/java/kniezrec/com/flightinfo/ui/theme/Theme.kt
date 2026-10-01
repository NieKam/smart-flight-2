package kniezrec.com.flightinfo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
internal fun smartFlightColorScheme(
    colors: SmartFlightColors,
    dark: Boolean,
): ColorScheme =
    (if (dark) darkColorScheme() else lightColorScheme()).copy(
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

private val darkScheme = smartFlightColorScheme(DarkSmartFlightColors, dark = true)
private val lightScheme = smartFlightColorScheme(LightSmartFlightColors, dark = false)

/**
 * The app theme: [DarkSmartFlightColors] (the purple palette) when [darkTheme], otherwise
 * [LightSmartFlightColors]. The brand stays fixed: no dynamic (wallpaper) color.
 *
 * @param darkTheme the system night mode by default; the app's Theme setting overrides it.
 */
@Composable
fun SmartFlightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSmartFlightColors provides if (darkTheme) DarkSmartFlightColors else LightSmartFlightColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkScheme else lightScheme,
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
