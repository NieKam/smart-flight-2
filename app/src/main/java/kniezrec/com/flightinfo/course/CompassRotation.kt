package kniezrec.com.flightinfo.course

/**
 * The rotation to animate to so that the plane turns from [currentDegrees] (an unwrapped angle,
 * any value) to [headingDegrees] the short way: 350° → 10° turns +20°, not −340°. The result stays
 * unwrapped (it may leave [0, 360)), so repeated turns across north keep accumulating.
 */
internal fun shortestRotationTarget(
    currentDegrees: Float,
    headingDegrees: Float,
): Float {
    val delta = (((headingDegrees - currentDegrees) % 360f) + 540f) % 360f - 180f
    return currentDegrees + delta
}
