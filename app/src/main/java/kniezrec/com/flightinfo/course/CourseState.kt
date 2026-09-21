package kniezrec.com.flightinfo.course

sealed interface CourseState {
    data object Waiting : CourseState
    data object Unavailable : CourseState
    data object Error : CourseState
    data class Available(val headingDegrees: Int, val gpsBearingDegrees: Int?) : CourseState
}

internal fun normalizeCourseDegrees(value: Double): Int? {
    if (!value.isFinite()) return null
    return ((kotlin.math.floor(value).toInt() % 360) + 360) % 360
}

internal fun compassCardinal(headingDegrees: Int): String = when (headingDegrees) {
    in 0..22, in 338..359 -> "N"
    in 23..67 -> "NE"
    in 68..112 -> "E"
    in 113..157 -> "SE"
    in 158..202 -> "S"
    in 203..247 -> "SW"
    in 248..292 -> "W"
    else -> "NW"
}
