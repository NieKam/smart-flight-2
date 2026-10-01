package kniezrec.com.flightinfo.map.data.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kniezrec.com.flightinfo.map.data.MapTipRepository
import kniezrec.com.flightinfo.map.data.SharedPreferencesMapTipRepository
import javax.inject.Qualifier

/** The `map_tips` SharedPreferences file. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MapTipPreferences

@Module
@InstallIn(SingletonComponent::class)
object MapModule {
    private const val PREFERENCES_NAME = "map_tips"

    @Provides
    @MapTipPreferences
    fun provideMapTipPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MapBindingsModule {
    @Binds
    abstract fun bindMapTipRepository(repository: SharedPreferencesMapTipRepository): MapTipRepository
}
