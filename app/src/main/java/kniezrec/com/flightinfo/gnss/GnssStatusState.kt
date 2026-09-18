package kniezrec.com.flightinfo.gnss

/** Presentation state for the foreground-only GNSS status card. */
sealed interface GnssStatusState {
    data object Waiting : GnssStatusState

    data class Available(
        val satellites: List<GnssSatellite>,
    ) : GnssStatusState

    data object LocationServicesDisabled : GnssStatusState

    data object Unavailable : GnssStatusState

    data object Error : GnssStatusState
}

data class GnssSatellite(
    val usedInFix: Boolean,
    val signalStrengthDbHz: Float? = null,
)
