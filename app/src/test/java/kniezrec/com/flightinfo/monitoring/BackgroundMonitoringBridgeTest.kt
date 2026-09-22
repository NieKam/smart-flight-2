package kniezrec.com.flightinfo.monitoring

import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.gnss.GnssSatellite
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundMonitoringBridgeTest {
    private val service = FakeService()

    @After
    fun tearDown() {
        BackgroundMonitoringBridge.clear()
    }

    @Test
    fun `visible to background handoff reconciles one attached service`() {
        BackgroundMonitoringBridge.attach(service)
        BackgroundMonitoringBridge.setActivityVisible(true)
        BackgroundMonitoringBridge.setActivityVisible(false)

        assertEquals(listOf(true, false), service.reconciliations.map { it.first })
        assertEquals(0, service.stopForPreferenceDisabledCount)
    }

    @Test
    fun `foreground return and repeated resume are idempotent and retain fix`() {
        BackgroundMonitoringBridge.attach(service)
        BackgroundMonitoringBridge.setActivityVisible(true)
        BackgroundMonitoringBridge.setActivityVisible(false)
        BackgroundMonitoringBridge.onUsableLocationFix(FIX)
        BackgroundMonitoringBridge.beginSession()
        BackgroundMonitoringBridge.setActivityVisible(true)
        BackgroundMonitoringBridge.setActivityVisible(true)

        assertEquals(1, service.onUsableFixCount)
        assertEquals(3, service.reconciliations.count { it.first })
    }

    @Test
    fun `setting off stops current run and on reconciles without changing state`() {
        BackgroundMonitoringBridge.attach(service)
        BackgroundMonitoringBridge.setActivityVisible(false)
        BackgroundMonitoringBridge.setNotificationEnabled(false)
        BackgroundMonitoringBridge.setNotificationEnabled(true)

        assertEquals(1, service.stopForPreferenceDisabledCount)
        assertEquals(listOf(false), service.reconciliations.map { it.first })
    }

    @Test
    fun `usable fix cancels background run and forwards exactly once`() {
        var fixes = 0
        BackgroundMonitoringBridge.setEventHandlers({ fixes++ }, {})
        BackgroundMonitoringBridge.attach(service)
        BackgroundMonitoringBridge.setActivityVisible(false)
        val generation = serviceGeneration()
        BackgroundMonitoringBridge.forwardLocation(generation, FIX)

        assertEquals(1, fixes)
        assertEquals(1, service.onUsableFixCount)
    }

    @Test
    fun `stale location and gnss callbacks are rejected after detach and recreation`() {
        var fixes = 0
        var statuses = 0
        BackgroundMonitoringBridge.setEventHandlers({ fixes++ }, { statuses++ })
        val oldGeneration = BackgroundMonitoringBridge.attach(service)
        BackgroundMonitoringBridge.detach(service, oldGeneration)
        val newService = FakeService()
        val newGeneration = BackgroundMonitoringBridge.attach(newService)

        BackgroundMonitoringBridge.forwardLocation(oldGeneration, FIX)
        BackgroundMonitoringBridge.forwardGnssStatus(oldGeneration, SATELLITES)
        BackgroundMonitoringBridge.forwardLocation(newGeneration, FIX)
        BackgroundMonitoringBridge.forwardGnssStatus(newGeneration, SATELLITES)

        assertEquals(1, fixes)
        assertEquals(1, statuses)
    }

    @Test
    fun `eligibility loss is forwarded without changing persisted preference`() {
        var lost = 0
        BackgroundMonitoringBridge.setEligibilityLostHandler { lost++ }
        BackgroundMonitoringBridge.attach(service)
        BackgroundMonitoringBridge.notifyEligibilityLost()

        assertEquals(1, lost)
    }

    @Test
    fun `late callbacks cannot mark a later session fixed`() {
        BackgroundMonitoringBridge.attach(service)
        val oldGeneration = serviceGeneration()
        BackgroundMonitoringBridge.detach(service, oldGeneration)
        val newService = FakeService()
        val newGeneration = BackgroundMonitoringBridge.attach(newService)
        BackgroundMonitoringBridge.setActivityVisible(false)

        BackgroundMonitoringBridge.forwardLocation(oldGeneration, FIX)
        assertEquals(0, newService.onUsableFixCount)
        BackgroundMonitoringBridge.forwardLocation(newGeneration, FIX)
        assertEquals(1, newService.onUsableFixCount)
    }

    private fun serviceGeneration(): Long {
        BackgroundMonitoringBridge.clear()
        return BackgroundMonitoringBridge.attach(service)
    }
}

private class FakeService : BackgroundMonitoringService {
    val reconciliations = mutableListOf<Pair<Boolean, Boolean>>()
    var onUsableFixCount = 0
    var stopForPreferenceDisabledCount = 0

    override fun reconcile(
        activityVisible: Boolean,
        hasUsableFix: Boolean,
    ) {
        reconciliations += activityVisible to hasUsableFix
    }

    override fun onUsableFix() {
        onUsableFixCount++
    }

    override fun stopForPreferenceDisabled() {
        stopForPreferenceDisabledCount++
    }
}

private val FIX = FlightLocationFix(1.0, 100.0, 1L)
private val SATELLITES = listOf(GnssSatellite(true, 20f))
