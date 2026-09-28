package kniezrec.com.flightinfo.permission.data.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier

/** The `location_permission` SharedPreferences file (permission request history). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocationPermissionPreferences

@Module
@InstallIn(SingletonComponent::class)
object PermissionModule {
    private const val PREFERENCES_NAME = "location_permission"

    @Provides
    @LocationPermissionPreferences
    fun provideLocationPermissionPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
