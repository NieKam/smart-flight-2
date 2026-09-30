package kniezrec.com.flightinfo.nearby

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** WGS84 semi-major axis in metres. */
private const val WGS84_SEMI_MAJOR_METRES = 6_378_137.0

/** WGS84 flattening. */
private const val WGS84_FLATTENING = 1.0 / 298.257223563

/** WGS84 semi-minor axis in metres. */
private const val WGS84_SEMI_MINOR_METRES = (1.0 - WGS84_FLATTENING) * WGS84_SEMI_MAJOR_METRES

private const val MAX_ITERATIONS = 200
private const val CONVERGENCE_RADIANS = 1e-12

/** Below this sin(sigma) the points are treated as identical or exactly antipodal. */
private const val DEGENERATE_SIN_SIGMA = 1e-15

/**
 * Geodesic distance in kilometres between two points on the WGS84 ellipsoid, like the original
 * app's `Location.distanceBetween`. Uses Vincenty's inverse formula (sub-millimetre accuracy where
 * it converges). Identical points give 0; exactly antipodal points give half a meridian (the
 * shortest geodesic runs over a pole). For the rare near-antipodal pairs where Vincenty does not
 * converge it falls back to the spherical [distanceKilometres] (within about 0.5% there).
 */
internal fun ellipsoidalDistanceKm(
    first: NearbyCoordinate,
    second: NearbyCoordinate,
): Double = (vincentyMetres(first, second)?.div(1000.0) ?: distanceKilometres(first, second))

/** Vincenty's inverse formula in metres, or null when it does not converge. */
private fun vincentyMetres(
    first: NearbyCoordinate,
    second: NearbyCoordinate,
): Double? {
    val longitudeDelta = Math.toRadians(second.longitude - first.longitude)
    // Normalised to [-pi, pi] so that pairs across the antimeridian start from the short way round.
    val l = atan2(sin(longitudeDelta), cos(longitudeDelta))
    val tanU1 = (1.0 - WGS84_FLATTENING) * tan(Math.toRadians(first.latitude))
    val cosU1 = 1.0 / sqrt(1.0 + tanU1 * tanU1)
    val sinU1 = tanU1 * cosU1
    val tanU2 = (1.0 - WGS84_FLATTENING) * tan(Math.toRadians(second.latitude))
    val cosU2 = 1.0 / sqrt(1.0 + tanU2 * tanU2)
    val sinU2 = tanU2 * cosU2

    var lambda = l
    var sinSigma = 0.0
    var cosSigma = 0.0
    var sigma = 0.0
    var cosSquaredAlpha = 0.0
    var cos2SigmaM = 0.0
    var iteration = 0
    while (true) {
        val sinLambda = sin(lambda)
        val cosLambda = cos(lambda)
        sinSigma = hypot(cosU2 * sinLambda, cosU1 * sinU2 - sinU1 * cosU2 * cosLambda)
        cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda
        if (sinSigma < DEGENERATE_SIN_SIGMA) {
            if (cosSigma > 0.0) return 0.0
            // Exactly antipodal: the geodesic is a meridian through a pole.
            sinSigma = 0.0
            cosSigma = -1.0
            sigma = PI
            cosSquaredAlpha = 1.0
            cos2SigmaM = cosSigma - 2.0 * sinU1 * sinU2
            break
        }
        sigma = atan2(sinSigma, cosSigma)
        val sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma
        cosSquaredAlpha = 1.0 - sinAlpha * sinAlpha
        // On the equator cos²α is 0 and the term is conventionally 0.
        cos2SigmaM = if (cosSquaredAlpha != 0.0) cosSigma - 2.0 * sinU1 * sinU2 / cosSquaredAlpha else 0.0
        val c = WGS84_FLATTENING / 16.0 * cosSquaredAlpha * (4.0 + WGS84_FLATTENING * (4.0 - 3.0 * cosSquaredAlpha))
        val previous = lambda
        lambda = l + (1.0 - c) * WGS84_FLATTENING * sinAlpha *
            (sigma + c * sinSigma * (cos2SigmaM + c * cosSigma * (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM)))
        if (!lambda.isFinite() || abs(lambda) > PI) return null
        if (abs(lambda - previous) < CONVERGENCE_RADIANS) break
        if (++iteration >= MAX_ITERATIONS) return null
    }

    val a2 = WGS84_SEMI_MAJOR_METRES * WGS84_SEMI_MAJOR_METRES
    val b2 = WGS84_SEMI_MINOR_METRES * WGS84_SEMI_MINOR_METRES
    val uSquared = cosSquaredAlpha * (a2 - b2) / b2
    val bigA = 1.0 + uSquared / 16384.0 * (4096.0 + uSquared * (-768.0 + uSquared * (320.0 - 175.0 * uSquared)))
    val bigB = uSquared / 1024.0 * (256.0 + uSquared * (-128.0 + uSquared * (74.0 - 47.0 * uSquared)))
    val deltaSigma =
        bigB * sinSigma *
            (
                cos2SigmaM +
                    bigB / 4.0 *
                    (
                        cosSigma * (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM) -
                            bigB / 6.0 * cos2SigmaM * (-3.0 + 4.0 * sinSigma * sinSigma) *
                            (-3.0 + 4.0 * cos2SigmaM * cos2SigmaM)
                    )
            )
    return (WGS84_SEMI_MINOR_METRES * bigA * (sigma - deltaSigma)).takeIf(Double::isFinite)
}
