package kniezrec.com.flightinfo.route.data.di

import android.content.Context
import android.content.SharedPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier

/** The `route` SharedPreferences file (persisted departure/destination ids), owned by `RouteRepository`. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RoutePreferences

@Module
@InstallIn(SingletonComponent::class)
object RouteModule {
    private const val PREFERENCES_NAME = "route"

    @Provides
    @RoutePreferences
    fun provideRoutePreferences(
        @ApplicationContext context: Context,
    ): SharedPreferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
}
