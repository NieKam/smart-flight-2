package kniezrec.com.flightinfo.settings.ui

import kniezrec.com.flightinfo.display.DisplayPreferences
import kniezrec.com.flightinfo.displayunits.AltitudeUnit
import kniezrec.com.flightinfo.displayunits.SpeedUnit
import kniezrec.com.flightinfo.displayunits.UnitPreferences
import kniezrec.com.flightinfo.monitoring.NotificationAccess
import kniezrec.com.flightinfo.testutil.FakeBackgroundNotificationSettingsRepository
import kniezrec.com.flightinfo.testutil.FakeDisplaySettingsRepository
import kniezrec.com.flightinfo.testutil.FakeUnitSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val units = FakeUnitSettingsRepository()
    private val display = FakeDisplaySettingsRepository()
    private val notification = FakeBackgroundNotificationSettingsRepository()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun initialStateHoldsTheStoredSettings() =
        runTest(dispatcher) {
            val stored = UnitPreferences(speed = SpeedUnit.KNOTS)
            units.setUnits(stored)
            display.set(DisplayPreferences(keepScreenAlwaysOn = true))
            notification.setShowBackgroundNotification(false)

            val state = viewModel().state.value

            assertEquals(SettingsUiState(stored, DisplayPreferences(keepScreenAlwaysOn = true), false), state)
        }

    @Test
    fun setUnitsPersistsAndEmits() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)
            val value = UnitPreferences(speed = SpeedUnit.MILES_PER_HOUR, altitude = AltitudeUnit.FEET)

            viewModel.setUnits(value)
            runCurrent()

            assertEquals(value, units.units.value)
            assertEquals(value, viewModel.state.value.units)
        }

    @Test
    fun setDisplayPersistsAndEmits() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)
            val value = DisplayPreferences(keepScreenAlwaysOn = true, portraitOrientation = false, largerMapZoom = true)

            viewModel.setDisplay(value)
            runCurrent()

            assertEquals(value, display.display.value)
            assertEquals(value, viewModel.state.value.display)
        }

    @Test
    fun setShowBackgroundNotificationPersistsAndEmits() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)

            viewModel.setShowBackgroundNotification(false)
            runCurrent()
            assertFalse(notification.settings.value.showBackgroundNotification)
            assertFalse(viewModel.state.value.showBackgroundNotification)

            viewModel.setShowBackgroundNotification(true)
            runCurrent()
            assertEquals(true, notification.settings.value.showBackgroundNotification)
            assertEquals(true, viewModel.state.value.showBackgroundNotification)
        }

    @Test
    fun enablingBackgroundNotificationRequestsMonitoringAndDisablingDoesNot() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val requests = mutableListOf<Unit>()
            backgroundScope.launch { viewModel.backgroundMonitoringRequests.collect { requests += it } }
            runCurrent()

            viewModel.setShowBackgroundNotification(false)
            runCurrent()
            assertEquals(0, requests.size)

            viewModel.setShowBackgroundNotification(true)
            runCurrent()
            assertEquals(1, requests.size)
        }

    @Test
    fun monitoringRequestWithoutCollectorIsNotReplayedLater() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.setShowBackgroundNotification(true)
            runCurrent()

            val requests = mutableListOf<Unit>()
            backgroundScope.launch { viewModel.backgroundMonitoringRequests.collect { requests += it } }
            runCurrent()

            assertEquals(0, requests.size)
            assertEquals(true, notification.settings.value.showBackgroundNotification)
        }

    private fun viewModel() = SettingsViewModel(units, display, notification)

    @Test
    fun switchingTheNotificationOnAsksForThePermissionOnlyWhenRequestable() =
        runTest(dispatcher) {
            notification.setShowBackgroundNotification(false)
            val viewModel = viewModel()
            val actions = mutableListOf<NotificationAction>()
            backgroundScope.launch { viewModel.notificationActions.collect { actions += it } }
            runCurrent()

            viewModel.refreshNotificationAccess(NotificationAccess.Blocked)
            viewModel.setShowBackgroundNotification(true)
            runCurrent()
            assertEquals(emptyList<NotificationAction>(), actions)

            viewModel.setShowBackgroundNotification(false)
            viewModel.refreshNotificationAccess(NotificationAccess.Requestable)
            viewModel.setShowBackgroundNotification(true)
            runCurrent()
            assertEquals(listOf(NotificationAction.RequestPermission), actions)
        }

    @Test
    fun allowNotificationsRequestsOrOpensTheSettings() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            val actions = mutableListOf<NotificationAction>()
            backgroundScope.launch { viewModel.notificationActions.collect { actions += it } }
            runCurrent()

            // One tap at a time, as from the Settings screen.
            listOf(NotificationAccess.Allowed, NotificationAccess.Requestable, NotificationAccess.Blocked).forEach { access ->
                viewModel.refreshNotificationAccess(access)
                viewModel.allowNotifications()
                runCurrent()
            }

            assertEquals(listOf(NotificationAction.RequestPermission, NotificationAction.OpenSettings), actions)
        }

    @Test
    fun notificationsBlockedIsTheEffectiveStateAndDecidesTheRequestAfterLocation() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            subscribe(viewModel)
            assertFalse(viewModel.state.value.notificationsBlocked)

            viewModel.refreshNotificationAccess(NotificationAccess.Requestable)
            runCurrent()
            assertTrue(viewModel.state.value.notificationsBlocked)
            assertTrue(viewModel.shouldRequestNotificationPermission())

            viewModel.setShowBackgroundNotification(false)
            runCurrent()
            assertFalse(viewModel.shouldRequestNotificationPermission())
        }

    private fun TestScope.subscribe(viewModel: SettingsViewModel) {
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()
    }
}
