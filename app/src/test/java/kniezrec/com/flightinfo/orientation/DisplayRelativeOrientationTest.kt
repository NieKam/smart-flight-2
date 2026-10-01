package kniezrec.com.flightinfo.orientation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * [DisplayRelativeOrientation.calculate] with known rotation matrices.
 *
 * Matrices follow `SensorManager.getRotationMatrix`: row-major, world = R · device, world axes
 * east/north/up. For each [DisplayRotation] the device is posed physically so that the DISPLAY
 * (as the user sees it) is flat and faces north, then pitched nose-up (display top edge raised)
 * or rolled right (display right edge lowered) by 10°. `Surface.ROTATION_90` (Landscape) is the
 * device turned 90° counter-clockwise, `ROTATION_270` (ReverseLandscape) 90° clockwise
 * (`Display.getRotation` documentation).
 *
 * Sign convention (as `SensorManager.getOrientation`), the same for every display rotation:
 * nose-up gives NEGATIVE pitch, roll right gives POSITIVE roll.
 *
 * TASK-028 fixed the landscape remap, which was the inverse of the one Android's
 * `remapCoordinateSystem` uses (heading off by 180°, pitch and roll with the opposite sign).
 */
class DisplayRelativeOrientationTest {
    @Test fun portraitMatchesTheSignConvention() {
        assertPoses(DisplayRotation.Portrait, heading = 0.0, noseUpPitch = -10.0, rollRightRoll = 10.0)
    }

    @Test fun reversePortraitMatchesTheSignConvention() {
        assertPoses(DisplayRotation.ReversePortrait, heading = 0.0, noseUpPitch = -10.0, rollRightRoll = 10.0)
    }

    @Test fun landscapeMatchesTheSignConvention() {
        assertPoses(DisplayRotation.Landscape, heading = 0.0, noseUpPitch = -10.0, rollRightRoll = 10.0)
    }

    @Test fun reverseLandscapeMatchesTheSignConvention() {
        assertPoses(DisplayRotation.ReverseLandscape, heading = 0.0, noseUpPitch = -10.0, rollRightRoll = 10.0)
    }

    @Test fun deviceMatrixIsRemappedForEachDisplayRotation() {
        // The device itself (not the display) pitched and rolled in its natural frame.
        val pitchMatrix = deviceMatrixForPitch(12.0)
        val rollMatrix = deviceMatrixForRoll(21.0)
        val rotations =
            listOf(
                DisplayRotation.Portrait to ExpectedOrientation(0.0, 12.0, 0.0, 0.0, 0.0, 21.0),
                DisplayRotation.Landscape to ExpectedOrientation(90.0, 0.0, -12.0, 90.0, 21.0, 0.0),
                DisplayRotation.ReversePortrait to ExpectedOrientation(180.0, -12.0, 0.0, 180.0, 0.0, -21.0),
                DisplayRotation.ReverseLandscape to ExpectedOrientation(270.0, 0.0, 12.0, 270.0, -21.0, 0.0),
            )

        rotations.forEach { (rotation, expected) ->
            assertOrientation(
                actual = DisplayRelativeOrientation.calculate(pitchMatrix, rotation)!!,
                heading = expected.pitchHeading,
                pitch = expected.pitch,
                roll = expected.pitchRoll,
            )
            assertOrientation(
                actual = DisplayRelativeOrientation.calculate(rollMatrix, rotation)!!,
                heading = expected.rollHeading,
                pitch = expected.rollPitch,
                roll = expected.roll,
            )
        }
    }

    @Test fun invalidMatricesGiveNoSample() {
        assertNull(DisplayRelativeOrientation.calculate(FloatArray(8), DisplayRotation.Portrait))
        val withNaN = floatArrayOf(1f, 0f, 0f, 0f, Float.NaN, 0f, 0f, 0f, 1f)
        assertNull(DisplayRelativeOrientation.calculate(withNaN, DisplayRotation.Portrait))
    }

    private fun assertPoses(
        rotation: DisplayRotation,
        heading: Double,
        noseUpPitch: Double,
        rollRightRoll: Double,
    ) {
        val deviceInDisplay = deviceInDisplay(rotation)
        assertOrientation(
            actual = DisplayRelativeOrientation.calculate(multiply(IDENTITY, deviceInDisplay), rotation)!!,
            heading = heading,
            pitch = 0.0,
            roll = 0.0,
        )
        assertOrientation(
            actual = DisplayRelativeOrientation.calculate(multiply(noseUp(10.0), deviceInDisplay), rotation)!!,
            heading = heading,
            pitch = noseUpPitch,
            roll = 0.0,
        )
        assertOrientation(
            actual = DisplayRelativeOrientation.calculate(multiply(rollRight(10.0), deviceInDisplay), rotation)!!,
            heading = heading,
            pitch = 0.0,
            roll = rollRightRoll,
        )
    }

    /** Columns: the device x and y axes expressed in display coordinates (x right, y up). */
    private fun deviceInDisplay(rotation: DisplayRotation): FloatArray =
        when (rotation) {
            DisplayRotation.Portrait -> columns(deviceX = 1f to 0f, deviceY = 0f to 1f)
            // Turned counter-clockwise: the device's right edge is the display top.
            DisplayRotation.Landscape -> columns(deviceX = 0f to 1f, deviceY = -1f to 0f)
            DisplayRotation.ReversePortrait -> columns(deviceX = -1f to 0f, deviceY = 0f to -1f)
            // Turned clockwise: the device's top edge is the display right.
            DisplayRotation.ReverseLandscape -> columns(deviceX = 0f to -1f, deviceY = 1f to 0f)
        }

    private fun columns(
        deviceX: Pair<Float, Float>,
        deviceY: Pair<Float, Float>,
    ): FloatArray = floatArrayOf(deviceX.first, deviceY.first, 0f, deviceX.second, deviceY.second, 0f, 0f, 0f, 1f)

    /** Display frame rotated about its x axis so that its top edge rises. */
    private fun noseUp(degrees: Double): FloatArray {
        val c = cos(Math.toRadians(degrees)).toFloat()
        val s = sin(Math.toRadians(degrees)).toFloat()
        return floatArrayOf(1f, 0f, 0f, 0f, c, -s, 0f, s, c)
    }

    /** Display frame rotated about its y axis so that its right edge drops. */
    private fun rollRight(degrees: Double): FloatArray {
        val c = cos(Math.toRadians(degrees)).toFloat()
        val s = sin(Math.toRadians(degrees)).toFloat()
        return floatArrayOf(c, 0f, s, 0f, 1f, 0f, -s, 0f, c)
    }

    private fun multiply(
        a: FloatArray,
        b: FloatArray,
    ): FloatArray =
        FloatArray(9) { index ->
            val row = index / 3
            val column = index % 3
            (0..2).sumOf { k -> (a[row * 3 + k] * b[k * 3 + column]).toDouble() }.toFloat()
        }

    private data class ExpectedOrientation(
        val pitchHeading: Double,
        val pitch: Double,
        val pitchRoll: Double,
        val rollHeading: Double,
        val rollPitch: Double,
        val roll: Double,
    )

    private fun deviceMatrixForPitch(degrees: Double): FloatArray {
        val radians = Math.toRadians(degrees)
        val cosine = cos(radians).toFloat()
        val sine = sin(radians).toFloat()
        return floatArrayOf(1f, 0f, 0f, 0f, cosine, sine, 0f, -sine, cosine)
    }

    private fun deviceMatrixForRoll(degrees: Double): FloatArray {
        val radians = Math.toRadians(degrees)
        val cosine = cos(radians).toFloat()
        val sine = sin(radians).toFloat()
        return floatArrayOf(cosine, 0f, sine, 0f, 1f, 0f, -sine, 0f, cosine)
    }

    private fun assertOrientation(
        actual: OrientationSample,
        heading: Double,
        pitch: Double,
        roll: Double,
    ) {
        assertEquals(heading, actual.headingDegrees, 0.001)
        assertEquals(pitch, actual.pitchDegrees, 0.001)
        assertEquals(roll, actual.rollDegrees, 0.001)
    }

    private companion object {
        val IDENTITY = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    }
}
