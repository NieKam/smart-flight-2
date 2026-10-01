package kniezrec.com.flightinfo.permission.data

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionRequestHistoryRepositoryTest {
    private val preferences: SharedPreferences =
        ApplicationProvider
            .getApplicationContext<Context>()
            .getSharedPreferences("location_permission", Context.MODE_PRIVATE)

    @Test
    fun missingValueMeansNotRequested() {
        assertFalse(PermissionRequestHistoryRepository(preferences).hasRequestedFineLocation)
    }

    @Test
    fun recordedRequestPersistsUnderTheSameKey() {
        PermissionRequestHistoryRepository(preferences).hasRequestedFineLocation = true

        assertTrue(preferences.getBoolean("has_requested_location_permission", false))
        // A new instance (e.g. after process restart) reads the same value back.
        assertTrue(PermissionRequestHistoryRepository(preferences).hasRequestedFineLocation)
    }

    @Test
    fun valueWrittenElsewhereIsReadOnNextAccess() {
        val repository = PermissionRequestHistoryRepository(preferences)

        preferences.edit().putBoolean("has_requested_location_permission", true).commit()

        assertTrue(repository.hasRequestedFineLocation)
    }
}
