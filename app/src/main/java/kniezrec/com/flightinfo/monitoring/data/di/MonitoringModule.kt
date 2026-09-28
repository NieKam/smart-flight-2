package kniezrec.com.flightinfo.monitoring.data.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kniezrec.com.flightinfo.monitoring.data.BackgroundNotificationSettingsRepository
import kniezrec.com.flightinfo.monitoring.data.SharedPreferencesBackgroundNotificationSettingsRepository
import javax.inject.Qualifier

/** The `monitoring_behavior` SharedPreferences file. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MonitoringBehaviorPreferences

@Module
@InstallIn(SingletonComponent::class)
object MonitoringModule {
    private const val PREFERENCES_NAME = "monitoring_behavior"

    @Provides
    @MonitoringBehaviorPreferences
    fun provideMonitoringBehaviorPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}

/**
 * One [SharedPreferencesBackgroundNotificationSettingsRepository] (a `@Singleton`) shared by
 * `MainActivity` and `LocationForegroundService`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MonitoringBindingsModule {
    @Binds
    abstract fun bindBackgroundNotificationSettingsRepository(
        repository: SharedPreferencesBackgroundNotificationSettingsRepository,
    ): BackgroundNotificationSettingsRepository
}
