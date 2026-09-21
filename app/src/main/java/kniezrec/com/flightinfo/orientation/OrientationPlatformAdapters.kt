package kniezrec.com.flightinfo.orientation

import kniezrec.com.flightinfo.course.CourseOrientationPlatform
import kniezrec.com.flightinfo.horizon.HorizonOrientationPlatform

internal class SharedCourseOrientationPlatform(
    private val source: OrientationSource,
) : CourseOrientationPlatform {
    private var listener: ((OrientationSample) -> Unit)? = null

    override fun isOrientationAvailable(): Boolean = source.isAvailable()

    override fun registerOrientationListener(onHeading: (Double) -> Unit): Boolean {
        val newListener: (OrientationSample) -> Unit = { onHeading(it.headingDegrees) }
        listener = newListener
        return source.addListener(newListener)
    }

    override fun unregisterOrientationListener() {
        listener?.let(source::removeListener)
        listener = null
    }
}

internal class SharedHorizonOrientationPlatform(
    private val source: OrientationSource,
) : HorizonOrientationPlatform {
    private var listener: ((OrientationSample) -> Unit)? = null

    override fun isOrientationAvailable(): Boolean = source.isAvailable()

    override fun registerOrientationListener(onAttitude: (Double, Double) -> Unit): Boolean {
        val newListener: (OrientationSample) -> Unit = { onAttitude(it.pitchDegrees, it.rollDegrees) }
        listener = newListener
        return source.addListener(newListener)
    }

    override fun unregisterOrientationListener() {
        listener?.let(source::removeListener)
        listener = null
    }
}
