package kniezrec.com.flightinfo.displayunits.data.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kniezrec.com.flightinfo.displayunits.data.SharedPreferencesUnitSettingsRepository
import kniezrec.com.flightinfo.displayunits.data.UnitSettingsRepository
import javax.inject.Qualifier

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
}

/** One [SharedPreferencesUnitSettingsRepository] (a `@Singleton`) serves every consumer. */
@Module
@InstallIn(SingletonComponent::class)
abstract class UnitsBindingsModule {
    @Binds
    abstract fun bindUnitSettingsRepository(repository: SharedPreferencesUnitSettingsRepository): UnitSettingsRepository
}
