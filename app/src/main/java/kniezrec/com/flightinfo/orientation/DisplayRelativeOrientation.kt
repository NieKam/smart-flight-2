package kniezrec.com.flightinfo.orientation

/** Device orientation relative to the current display rotation, in degrees. */
data class OrientationSample(
    val headingDegrees: Double,
    val pitchDegrees: Double,
    val rollDegrees: Double,
)

/** Rotation of the display from the device's natural (portrait) orientation. */
enum class DisplayRotation {
    Portrait,
    Landscape,
    ReversePortrait,
    ReverseLandscape,
}

/** Pure display-coordinate conversion kept separate from the Android sensor callback. */
internal object DisplayRelativeOrientation {
    fun calculate(
        rotationMatrix: FloatArray,
        rotation: DisplayRotation,
    ): OrientationSample? {
        if (rotationMatrix.size < 9 || rotationMatrix.take(9).any { !it.isFinite() }) return null
        // Remapped x and y columns are these (signed, 1-based) device columns. This equals
        // `SensorManager.remapCoordinateSystem` with (AXIS_Y, AXIS_MINUS_X) for ROTATION_90 and
        // (AXIS_MINUS_Y, AXIS_X) for ROTATION_270: Android moves device column x INTO remapped
        // column X, so its arguments read inverted here.
        val (xAxis, yAxis) =
            when (rotation) {
                DisplayRotation.Portrait -> 1 to 2
                DisplayRotation.Landscape -> -2 to 1
                DisplayRotation.ReversePortrait -> -1 to -2
                DisplayRotation.ReverseLandscape -> 2 to -1
            }
        val remapped = FloatArray(9)
        for (row in 0..2) {
            remapped[row * 3] = rotationMatrix.axisValue(row, xAxis)
            remapped[row * 3 + 1] = rotationMatrix.axisValue(row, yAxis)
            remapped[row * 3 + 2] = rotationMatrix[row * 3 + 2]
        }
        val heading = Math.toDegrees(kotlin.math.atan2(remapped[1].toDouble(), remapped[4].toDouble()))
        val pitch = Math.toDegrees(kotlin.math.asin(-remapped[7].toDouble()))
        val roll = Math.toDegrees(kotlin.math.atan2(-remapped[6].toDouble(), remapped[8].toDouble()))
        if (!heading.isFinite() || !pitch.isFinite() || !roll.isFinite()) return null
        return OrientationSample(
            headingDegrees = if (heading < 0) heading + 360 else heading,
            pitchDegrees = pitch,
            rollDegrees = roll,
        )
    }

    private fun FloatArray.axisValue(
        row: Int,
        axis: Int,
    ): Float {
        val column = kotlin.math.abs(axis) - 1
        val value = this[row * 3 + column]
        return if (axis < 0) -value else value
    }
}
