package kniezrec.com.flightinfo.dashboard.data.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kniezrec.com.flightinfo.dashboard.data.CardVisibilityRepository
import kniezrec.com.flightinfo.dashboard.data.SharedPreferencesCardVisibilityRepository
import javax.inject.Qualifier

/** The `card_visibility` SharedPreferences file. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CardVisibilityPreferences

@Module
@InstallIn(SingletonComponent::class)
object DashboardModule {
    private const val PREFERENCES_NAME = "card_visibility"

    @Provides
    @CardVisibilityPreferences
    fun provideCardVisibilityPreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}

/** One [SharedPreferencesCardVisibilityRepository] (a `@Singleton`) shared by the dashboard and Settings. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DashboardBindingsModule {
    @Binds
    abstract fun bindCardVisibilityRepository(repository: SharedPreferencesCardVisibilityRepository): CardVisibilityRepository
}
