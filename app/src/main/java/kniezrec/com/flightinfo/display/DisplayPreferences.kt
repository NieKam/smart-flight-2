package kniezrec.com.flightinfo.display

data class DisplayPreferences(
    val keepScreenAlwaysOn: Boolean = false,
    val portraitOrientation: Boolean = true,
    val largerMapZoom: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
)

/** The app's Theme setting (TASK-037): follow the system night mode, or force light or dark. */
enum class ThemeMode(
    /** The value stored under the `theme_mode` key. */
    val storageValue: String,
) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    /** Whether the dark scheme applies, given whether the system is in night mode. */
    fun isDark(systemDark: Boolean): Boolean =
        when (this) {
            SYSTEM -> systemDark
            LIGHT -> false
            DARK -> true
        }

    companion object {
        /** The mode stored as [value]; a missing or unknown value is [SYSTEM]. */
        fun fromStorage(value: String?): ThemeMode = entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}
