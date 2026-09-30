package kniezrec.com.flightinfo.dashboard

/**
 * A dashboard card the user may hide because this device lacks its sensor (as the original app,
 * only the sensor-dependent Course and Horizon cards).
 */
enum class HideableCard {
    Course,
    Horizon,
}
