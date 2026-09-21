package kniezrec.com.flightinfo.route

import android.content.SharedPreferences
import kniezrec.com.flightinfo.flight.FlightLocationFix
import kniezrec.com.flightinfo.nearby.NearbyCityRecord
import kniezrec.com.flightinfo.nearby.NearbyCityRepository
import kniezrec.com.flightinfo.nearby.NearbyCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class RouteControllerTest {
    private val departure = NearbyCityRecord(1, "Alpha", "A", 0.0, 0.0, "UTC")
    private val destination = NearbyCityRecord(2, "Beta", "B", 0.0, 1.0, "UTC")
    private val direct = Executor { it.run() }

    @Test fun `valid endpoint ids restore and invalid ids are discarded`() {
        val preferences = MemoryPreferences()
        val repository = FakeRepository(listOf(departure, destination))
        var restored = RouteState()
        RouteController(repository, preferences, direct, direct, { restored = it }).apply {
            start()
            choose(RouteEndpoint.DEPARTURE, departure)
            choose(RouteEndpoint.DESTINATION, destination)
        }
        var next = RouteState()
        RouteController(repository, preferences, direct, direct, { next = it }).start()
        assertEquals(departure, next.departure)
        assertEquals(destination, next.destination)

        preferences.edit().putLong("route_departure_id", 99L).commit()
        var invalid = RouteState()
        RouteController(repository, preferences, direct, direct, { invalid = it }).start()
        assertNull(invalid.departure)
        assertEquals(destination, invalid.destination)
        assertTrue(restored.overlay != null)
    }

    @Test fun `clearing route removes both endpoints and overlay`() {
        var state = RouteState()
        val controller = RouteController(FakeRepository(listOf(departure, destination)), MemoryPreferences(), direct, direct, { state = it })
        controller.start()
        controller.choose(RouteEndpoint.DEPARTURE, departure)
        controller.choose(RouteEndpoint.DESTINATION, destination)
        controller.clearRoute()
        assertNull(state.departure)
        assertNull(state.destination)
        assertNull(state.overlay)
    }

    @Test fun `stopping session clears live details and rejects later fixes`() {
        val states = mutableListOf<RouteState>()
        val controller = RouteController(FakeRepository(listOf(departure, destination)), MemoryPreferences(), direct, direct, states::add)
        controller.start()
        controller.choose(RouteEndpoint.DEPARTURE, departure)
        controller.choose(RouteEndpoint.DESTINATION, destination)
        controller.onFix(FlightLocationFix(100.0, 0.0, 1L, 0.0, 0.0, 0.5))
        assertTrue(states.last().details?.remainingDistanceKm != null)
        controller.stop()
        assertNull(states.last().details?.remainingDistanceKm)
        val stoppedStateCount = states.size
        controller.onFix(FlightLocationFix(100.0, 0.0, 2L, 0.0, 0.0, 0.9))
        assertEquals(stoppedStateCount, states.size)
    }

    @Test fun restoreReadFailuresAreSurfacedForRetry() {
        val preferences = MemoryPreferences().apply { edit().putLong("route_departure_id", departure.id).commit() }
        val repository = object : NearbyCityRepository {
            override fun findById(id: Long, reload: Boolean): NearbyCityRecord? = error("database unavailable")
        }
        var state = RouteState()
        RouteController(repository, preferences, direct, direct, { state = it }).start()
        assertTrue(state.error != null)
    }

    @Test fun nearestCallbackReturnsGeographicallyNearestCity() {
        val near = destination.copy(latitude = 0.0, longitude = 0.1)
        val far = destination.copy(id = 3L, latitude = 0.0, longitude = 10.0)
        var nearest: NearbyCityRecord? = null
        val repository = object : NearbyCityRepository {
            override fun findNearest(position: NearbyCoordinate, reload: Boolean): NearbyCityRecord? = listOf(near, far).minByOrNull { kniezrec.com.flightinfo.nearby.distanceKilometres(position, NearbyCoordinate(it.latitude, it.longitude)) }
        }
        val controller = RouteController(repository, MemoryPreferences(), direct, direct, {})
        controller.start()
        controller.nearest(NearbyCoordinate(0.0, 0.0)) { nearest = it.getOrNull() }
        assertEquals(near, nearest)
    }

    private class FakeRepository(private val records: List<NearbyCityRecord>) : NearbyCityRepository {
        override fun findNearest(position: NearbyCoordinate, reload: Boolean): NearbyCityRecord? = records.minByOrNull { it.latitude }
        override fun findById(id: Long, reload: Boolean): NearbyCityRecord? = records.firstOrNull { it.id == id }
        override fun searchByName(query: String, reload: Boolean): List<NearbyCityRecord> = records.filter { it.name.lowercase().contains(query) }
    }

    private class MemoryPreferences : SharedPreferences {
        private val values = mutableMapOf<String, Any>()
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = (values[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        private inner class Editor : SharedPreferences.Editor {
            override fun putString(key: String, value: String?): SharedPreferences.Editor = applyValue(key, value)
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = applyValue(key, values)
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = applyValue(key, value)
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = applyValue(key, value)
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = applyValue(key, value)
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = applyValue(key, value)
            override fun remove(key: String): SharedPreferences.Editor { values.remove(key); return this }
            override fun clear(): SharedPreferences.Editor { values.clear(); return this }
            override fun commit(): Boolean = true
            override fun apply() = Unit
            private fun applyValue(key: String, value: Any?): SharedPreferences.Editor { if (value == null) values.remove(key) else values[key] = value; return this }
        }
    }
}
