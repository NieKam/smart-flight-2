package kniezrec.com.flightinfo.monitoring.data.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kniezrec.com.flightinfo.monitoring.BackgroundNotificationPreferencesStore
import javax.inject.Qualifier
import javax.inject.Singleton

/** The `monitoring_behavior` SharedPreferences file ([BackgroundNotificationPreferencesStore.PREFERENCES_NAME]). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MonitoringBehaviorPreferences

@Module
@InstallIn(SingletonComponent::class)
object MonitoringModule {
    @Provides
    @MonitoringBehaviorPreferences
    fun provideMonitoringBehaviorPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences = context.getSharedPreferences(BackgroundNotificationPreferencesStore.PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** One instance shared by `MainActivity` and `LocationForegroundService`. */
    @Provides
    @Singleton
    fun provideBackgroundNotificationPreferencesStore(
        @MonitoringBehaviorPreferences preferences: SharedPreferences,
    ): BackgroundNotificationPreferencesStore = BackgroundNotificationPreferencesStore(preferences)
}
