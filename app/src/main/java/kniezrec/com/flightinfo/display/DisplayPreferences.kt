package kniezrec.com.flightinfo.display

data class DisplayPreferences(
    val keepScreenAlwaysOn: Boolean = false,
    val portraitOrientation: Boolean = true,
    val largerMapZoom: Boolean = false,
)
