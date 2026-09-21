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

internal enum class CompassCardinal {
    North,
    NorthEast,
    East,
    SouthEast,
    South,
    SouthWest,
    West,
    NorthWest,
}

internal fun compassCardinal(headingDegrees: Int): CompassCardinal = when (headingDegrees) {
    in 0..22, in 338..359 -> CompassCardinal.North
    in 23..67 -> CompassCardinal.NorthEast
    in 68..112 -> CompassCardinal.East
    in 113..157 -> CompassCardinal.SouthEast
    in 158..202 -> CompassCardinal.South
    in 203..247 -> CompassCardinal.SouthWest
    in 248..292 -> CompassCardinal.West
    else -> CompassCardinal.NorthWest
}
