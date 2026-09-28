package kniezrec.com.flightinfo.di

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.SensorManager
import android.location.LocationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Application-context system services shared by the whole process. */
@Module
@InstallIn(SingletonComponent::class)
object SystemServicesModule {
    @Provides
    @Singleton
    fun provideLocationManager(
        @ApplicationContext context: Context,
    ): LocationManager = checkNotNull(context.getSystemService(LocationManager::class.java))

    @Provides
    @Singleton
    fun provideSensorManager(
        @ApplicationContext context: Context,
    ): SensorManager = checkNotNull(context.getSystemService(SensorManager::class.java))

    @Provides
    @Singleton
    fun providePackageManager(
        @ApplicationContext context: Context,
    ): PackageManager = context.packageManager

    @Provides
    @Singleton
    fun provideNotificationManager(
        @ApplicationContext context: Context,
    ): NotificationManager = checkNotNull(context.getSystemService(NotificationManager::class.java))
}
