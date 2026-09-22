package kniezrec.com.flightinfo.monitoring

import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.flight.FlightLocationPlatform
import kniezrec.com.flightinfo.gnss.GnssSatellite
import kniezrec.com.flightinfo.gnss.GnssStatusPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationGnssMonitoringSessionTest {
    @Test fun `one active session forwards callbacks and prevents duplicates`() {
        val location = FakeLocationPlatform()
        val gnss = FakeGnssPlatform()
        var fixes = 0
        var statuses = 0
        val session = LocationGnssMonitoringSession(location, gnss, { fixes++ }, { statuses++ })

        assertTrue(session.start())
        assertTrue(session.start())
        location.emit(FIX)
        gnss.emit(listOf(GnssSatellite(true, 20f)))

        assertEquals(2, location.registerCount)
        assertEquals(2, gnss.registerCount)
        assertEquals(1, fixes)
        assertEquals(1, statuses)
        session.stop()
        assertFalse(session.isActive)
        assertEquals(2, location.unregisterCount)
        assertEquals(2, gnss.unregisterCount)
    }

    @Test fun `failed gnss registration releases location registration`() {
        val location = FakeLocationPlatform()
        val gnss = FakeGnssPlatform(registerResult = false)
        val session = LocationGnssMonitoringSession(location, gnss, {}, {})

        assertFalse(session.start())
        assertEquals(1, location.unregisterCount)
        assertFalse(session.isActive)
    }

    @Test fun `late callback from a stopped generation is ignored`() {
        val location = RetainingLocationPlatform()
        val gnss = FakeGnssPlatform()
        var fixes = 0
        val session = LocationGnssMonitoringSession(location, gnss, { fixes++ }, {})

        assertTrue(session.start())
        val oldCallback = location.callbacks.single()
        session.stop()
        assertTrue(session.start())

        oldCallback(FIX)
        assertEquals(0, fixes)
        location.emit(FIX)
        assertEquals(1, fixes)
    }
}

private val FIX = FlightLocationFix(1.0, 100.0, 1L)

private class FakeLocationPlatform : FlightLocationPlatform {
    var registerCount = 0
    var unregisterCount = 0
    private var callback: ((FlightLocationFix) -> Unit)? = null

    override fun areLocationServicesEnabled() = true

    override fun hasGnssHardware() = true

    override fun registerLocationListener(onLocation: (FlightLocationFix) -> Unit): Boolean {
        registerCount++
        callback = onLocation
        return true
    }

    override fun unregisterLocationListener() {
        unregisterCount++
        callback = null
    }

    fun emit(fix: FlightLocationFix) = callback?.invoke(fix)
}

private class RetainingLocationPlatform : FlightLocationPlatform {
    val callbacks = mutableListOf<(FlightLocationFix) -> Unit>()

    override fun areLocationServicesEnabled() = true

    override fun hasGnssHardware() = true

    override fun registerLocationListener(onLocation: (FlightLocationFix) -> Unit): Boolean {
        callbacks += onLocation
        return true
    }

    override fun unregisterLocationListener() = Unit

    fun emit(fix: FlightLocationFix) = callbacks.last()(fix)
}

private class FakeGnssPlatform(
    private val registerResult: Boolean = true,
) : GnssStatusPlatform {
    var registerCount = 0
    var unregisterCount = 0
    private var callback: ((List<GnssSatellite>) -> Unit)? = null

    override fun areLocationServicesEnabled() = true

    override fun hasGnssHardware() = true

    override fun registerGnssStatusCallback(onStatus: (List<GnssSatellite>) -> Unit): Boolean {
        registerCount++
        callback = onStatus
        return registerResult
    }

    override fun unregisterGnssStatusCallback() {
        unregisterCount++
        callback = null
    }

    fun emit(satellites: List<GnssSatellite>) = callback?.invoke(satellites)
}
