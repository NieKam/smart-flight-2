package kniezrec.com.flightinfo.monitoring

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundMonitoringTest {
    @Test fun `missing and malformed preference default to enabled`() {
        val preferences = FakePreferences()
        val store = BackgroundNotificationPreferencesStore(preferences)
        assertTrue(store.read().showBackgroundNotification)
        preferences.edit().putString(BackgroundNotificationPreferencesStore.KEY_SHOW_BACKGROUND_NOTIFICATION, "bad").apply()
        assertTrue(store.read().showBackgroundNotification)
    }

    @Test fun `preference round trips independently`() {
        val preferences = FakePreferences()
        val store = BackgroundNotificationPreferencesStore(preferences)
        store.write(BackgroundNotificationPreferences(false))
        assertFalse(store.read().showBackgroundNotification)
    }

    @Test fun `session transitions are idempotent and fix stops background wait`() {
        val session = BackgroundMonitoringSession()
        assertTrue(session.startForeground())
        assertFalse(session.startForeground())
        assertTrue(session.background(true))
        assertTrue(session.usableFix())
        assertEquals(BackgroundMonitoringState.BackgroundFixedThenStopped, session.state)
        assertFalse(session.usableFix())
    }

    @Test fun `disabled preference does not enter background waiting`() {
        val session = BackgroundMonitoringSession()
        session.startForeground()
        assertFalse(session.background(false))
        assertEquals(BackgroundMonitoringState.Inactive, session.state)
    }
}

private class FakePreferences : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(
        key: String?,
        defValue: String?,
    ) = values[key] as? String ?: defValue

    override fun getStringSet(
        key: String?,
        defValues: MutableSet<String>?,
    ) = values[key] as? MutableSet<String> ?: defValues

    override fun getInt(
        key: String?,
        defValue: Int,
    ) = values[key] as? Int ?: defValue

    override fun getLong(
        key: String?,
        defValue: Long,
    ) = values[key] as? Long ?: defValue

    override fun getFloat(
        key: String?,
        defValue: Float,
    ) = values[key] as? Float ?: defValue

    override fun getBoolean(
        key: String?,
        defValue: Boolean,
    ) = values[key] as? Boolean ?: defValue

    override fun contains(key: String?) = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class Editor : SharedPreferences.Editor {
        override fun putString(
            key: String?,
            value: String?,
        ) = apply { if (key != null) values[key] = value }

        override fun putStringSet(
            key: String?,
            value: MutableSet<String>?,
        ) = apply { if (key != null) values[key] = value }

        override fun putInt(
            key: String?,
            value: Int,
        ) = apply { if (key != null) values[key] = value }

        override fun putLong(
            key: String?,
            value: Long,
        ) = apply { if (key != null) values[key] = value }

        override fun putFloat(
            key: String?,
            value: Float,
        ) = apply { if (key != null) values[key] = value }

        override fun putBoolean(
            key: String?,
            value: Boolean,
        ) = apply { if (key != null) values[key] = value }

        override fun remove(key: String?) = apply { if (key != null) values.remove(key) }

        override fun clear() = apply { values.clear() }

        override fun commit() = true

        override fun apply() = Unit
    }
}
