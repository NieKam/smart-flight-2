package kniezrec.com.flightinfo.display.data.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kniezrec.com.flightinfo.display.DisplayPreferencesStore
import javax.inject.Qualifier
import javax.inject.Singleton

/** The `display_behavior` SharedPreferences file. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DisplayBehaviorPreferences

@Module
@InstallIn(SingletonComponent::class)
object DisplayModule {
    private const val PREFERENCES_NAME = "display_behavior"

    @Provides
    @DisplayBehaviorPreferences
    fun provideDisplayBehaviorPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideDisplayPreferencesStore(
        @DisplayBehaviorPreferences preferences: SharedPreferences,
    ): DisplayPreferencesStore = DisplayPreferencesStore(preferences)
}
