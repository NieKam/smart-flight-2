package kniezrec.com.flightinfo.displayunits.data.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kniezrec.com.flightinfo.displayunits.UnitPreferencesStore
import javax.inject.Qualifier
import javax.inject.Singleton

/** The `display_units` SharedPreferences file. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DisplayUnitsPreferences

@Module
@InstallIn(SingletonComponent::class)
object UnitsModule {
    private const val PREFERENCES_NAME = "display_units"

    @Provides
    @DisplayUnitsPreferences
    fun provideDisplayUnitsPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideUnitPreferencesStore(
        @DisplayUnitsPreferences preferences: SharedPreferences,
    ): UnitPreferencesStore = UnitPreferencesStore(preferences)
}
