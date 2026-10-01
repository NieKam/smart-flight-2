package kniezrec.com.flightinfo.display.data.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kniezrec.com.flightinfo.display.data.DisplaySettingsRepository
import kniezrec.com.flightinfo.display.data.SharedPreferencesDisplaySettingsRepository
import javax.inject.Qualifier

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
}

/** One [SharedPreferencesDisplaySettingsRepository] (a `@Singleton`) serves every consumer. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DisplayBindingsModule {
    @Binds
    abstract fun bindDisplaySettingsRepository(repository: SharedPreferencesDisplaySettingsRepository): DisplaySettingsRepository
}
